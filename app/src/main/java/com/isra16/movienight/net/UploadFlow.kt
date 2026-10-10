package com.isra16.movienight.net

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Lo que se hace con la key del objeto apenas termina el PUT (crear la sala, cambiar el video de la sala). */
typealias FollowUp = suspend (key: String) -> FollowUpOutcome

/**
 * Una subida de video a la biblioteca de punta a punta (Fase 4A) más, opcionalmente, un paso siguiente
 * (Fase 4B: crear una sala con el video, o cambiar el video de la sala). Lo comparten `HomeViewModel` y
 * `RoomViewModel`; cada uno le pasa su propio [scope] (la subida muere con su pantalla).
 *
 * Pasos: elegir el archivo → pedir la URL prefirmada → `PUT` directo al bucket → (si hay [FollowUp])
 * crear la sala / cambiar el video. Después del PUT no hay confirmación: el video ya está en la
 * biblioteca, así que [onLibraryChanged] avisa para que se recargue la lista.
 *
 * **Cancelar y reintentar:**
 *  - Se puede cancelar mientras se prepara o se sube ([cancel]); cortar el PUT no deja nada en el bucket.
 *    Durante el paso siguiente ya no (es un POST de un instante; cortarlo dejaría en duda si la sala se creó).
 *  - Si falló la subida, [retry] pide una URL nueva y sube desde cero (un PUT simple no se reanuda).
 *  - Si la subida salió bien y falló el paso siguiente, [retry] repite SOLO ese paso con la misma key: no se
 *    vuelve a subir un video que ya está en la nube.
 *
 * Decisiones de la 4A: la subida es una corrutina del ViewModel dueño y, si Android mata el proceso, se pierde. Desde la
 * 6C un servicio en primer plano (`UploadKeepAlive`, que se entera por `onStateChanged`) mantiene vivo el proceso
 * mientras hay una subida en marcha, porque con la app en segundo plano Android corta la red.
 */
class UploadFlow(
    private val scope: CoroutineScope,
    private val uploader: VideoUploader,
    private val api: MovieNightApi,
    private val baseUrl: String,
    /** Se llama cuando el server contesta 401 (la sesión venció), para que la app vuelva al login. */
    private val onSessionExpired: suspend () -> Unit,
    /** El video ya está en la biblioteca (o el server borró uno inválido): hay que recargar la lista. */
    private val onLibraryChanged: () -> Unit,
    /**
     * La subida llegó a su estado final ([UploadState.Done] o [UploadState.Failed]); lo usa el aviso local de
     * "terminó tu subida" (Fase 6C). Se llama desde el hilo principal.
     */
    private val onFinished: (UploadState) -> Unit = {},
    /**
     * Cada cambio de estado, incluido el avance; lo usa el servicio en primer plano que mantiene viva la
     * subida con la app en segundo plano (Fase 6C, opción B). Se llama desde cualquier hilo.
     */
    private val onStateChanged: (UploadState) -> Unit = {},
) {
    private var currentState by mutableStateOf<UploadState>(UploadState.Idle)

    /** Estado para la pantalla. Todo cambio pasa por acá, así [onFinished] se entera de cada final. */
    var state: UploadState
        get() = currentState
        private set(value) {
            currentState = value
            onStateChanged(value)
            if (value is UploadState.Done || value is UploadState.Failed) onFinished(value)
        }

    private var job: Job? = null

    /** El video de la subida en curso o fallida (para reintentar la subida). */
    private var picked: PickedVideo? = null

    /** Key del objeto ya subido (para reintentar solo el paso siguiente). */
    private var uploadedKey: String? = null
    private var currentName: String = ""
    private var goal: UploadGoal = UploadGoal.LIBRARY
    private var followUp: FollowUp? = null

    /**
     * Número de la subida vigente. Cada subida nueva, cada reintento y cada cancelación lo suben; lo que
     * llegue tarde de una subida ya cancelada (un último avance desde el hilo de red) se ignora en [setState].
     */
    @Volatile
    private var generation = 0

    private fun setState(gen: Int, newState: UploadState) {
        if (gen == generation) state = newState
    }

    /** Un [UploadState.Failed] que recuerda para qué era la subida. */
    private fun failed(
        fileName: String,
        message: String,
        canRetry: Boolean,
        alreadyUploaded: Boolean = false,
    ) = UploadState.Failed(fileName, message, canRetry, alreadyUploaded, goal)

    /**
     * Empieza una subida con el video que devolvió el selector del sistema ([uri] `null` = la persona lo
     * cerró sin elegir). Con [followUp] `null` el video solo queda en la biblioteca.
     */
    fun start(uri: Uri?, goal: UploadGoal = UploadGoal.LIBRARY, followUp: FollowUp? = null) {
        if (uri == null || state.isBusy()) return
        releasePicked()
        uploadedKey = null
        currentName = ""
        this.goal = goal
        this.followUp = followUp
        val gen = ++generation
        state = UploadState.Preparing("")
        launch(gen) {
            val video = uploader.inspect(uri)
            if (video == null) {
                setState(gen, failed("", "No se pudo leer el archivo elegido. Probá con otro.", false))
                return@launch
            }
            currentName = video.fileName
            val problem = validateUpload(video.fileName, video.sizeBytes)
            if (problem != null) {
                setState(gen, failed(video.fileName, problem, false))
                return@launch
            }
            uploader.retainAccess(uri)
            picked = video
            runUpload(gen, video)
        }
    }

    /** Vuelve a intentar tras un fallo recuperable (ver la explicación de la clase). */
    fun retry() {
        val current = state as? UploadState.Failed ?: return
        if (!current.canRetry) return
        val key = uploadedKey
        if (current.alreadyUploaded && key != null) {
            val gen = ++generation
            state = UploadState.Finishing(current.fileName, goal)
            launch(gen) { runFollowUp(gen, current.fileName, key) }
            return
        }
        val video = picked ?: return
        val gen = ++generation
        state = UploadState.Preparing(video.fileName)
        launch(gen) { runUpload(gen, video) }
    }

    /** Corta la subida en curso (cancela la llamada de red) y vuelve al estado inicial. Solo mientras se prepara o se sube. */
    fun cancel() {
        if (!state.canCancel()) return
        generation++
        job?.cancel()
        job = null
        releasePicked()
        uploadedKey = null
        state = UploadState.Idle
    }

    /** Cierra el mensaje de "listo" o de error. */
    fun dismiss() {
        if (state.isBusy()) return
        releasePicked()
        uploadedKey = null
        state = UploadState.Idle
    }

    /** Suelta el acceso al archivo (al cerrarse la pantalla dueña). La corrutina la cancela el [scope]. */
    fun release() {
        releasePicked()
        // La pantalla dueña se cierra y su corrutina se cancela: que el servicio en primer plano no se quede esperando.
        onStateChanged(UploadState.Idle)
    }

    private fun launch(gen: Int, block: suspend () -> Unit) {
        job = scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setState(
                    gen,
                    failed(
                        currentName,
                        "Pasó algo inesperado durante la subida (${e.javaClass.simpleName}). Intentá de nuevo.",
                        canRetry = picked != null || uploadedKey != null,
                        alreadyUploaded = uploadedKey != null,
                    ),
                )
            }
        }
    }

    private suspend fun runUpload(gen: Int, video: PickedVideo) {
        setState(gen, UploadState.Preparing(video.fileName))
        val contentType = contentTypeFor(video.fileName)
        val presign = api.postJson(
            baseUrl,
            "/api/uploads/presign",
            JSONObject()
                .put("filename", uploadFilename(video.fileName))
                .put("contentType", contentType),
        )
        if (isSessionExpired(presign.code)) onSessionExpired()
        val ticket = when (val result = interpretPresign(presign.code, presign.body)) {
            is PresignResult.Failed -> {
                setState(gen, failed(video.fileName, result.failure.message, result.failure.canRetry))
                return
            }
            is PresignResult.Ok -> result.upload
        }

        setState(gen, UploadState.Uploading(video.fileName, 0L, video.sizeBytes))
        val outcome = uploader.put(ticket.uploadUrl, video, contentType) { sent ->
            setState(gen, UploadState.Uploading(video.fileName, sent, video.sizeBytes))
        }
        val failure = failureFromPut(outcome)
        if (failure != null) {
            setState(gen, failed(video.fileName, failure.message, failure.canRetry))
            return
        }

        // El PUT terminó: no hay paso de confirmación, el video ya aparece en GET /api/uploads.
        if (gen != generation) return
        releasePicked()
        uploadedKey = ticket.key
        onLibraryChanged()
        if (followUp == null) {
            state = UploadState.Done(video.fileName, UploadGoal.LIBRARY)
        } else {
            state = UploadState.Finishing(video.fileName, goal)
            runFollowUp(gen, video.fileName, ticket.key)
        }
    }

    private suspend fun runFollowUp(gen: Int, fileName: String, key: String) {
        val step = followUp ?: return
        val outcome = step(key)
        if (gen != generation) return
        state = stateAfterFollowUp(fileName, goal, outcome)
        // El server borra de la biblioteca un video que no pasa la validación de contenido.
        if (outcome is FollowUpOutcome.Failed && outcome.failure.libraryChanged) onLibraryChanged()
    }

    private fun releasePicked() {
        picked?.let { uploader.releaseAccess(it.uri) }
        picked = null
    }
}
