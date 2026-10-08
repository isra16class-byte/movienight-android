package com.isra16.movienight.home

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.isra16.movienight.MovieNightApp
import com.isra16.movienight.net.LibraryItem
import com.isra16.movienight.net.PickedVideo
import com.isra16.movienight.net.PresignResult
import com.isra16.movienight.net.UploadState
import com.isra16.movienight.net.apiErrorMessage
import com.isra16.movienight.net.contentTypeFor
import com.isra16.movienight.net.extractRoomId
import com.isra16.movienight.net.failureFromPut
import com.isra16.movienight.net.interpretPresign
import com.isra16.movienight.net.isSessionExpired
import com.isra16.movienight.net.parseCreatedRoomId
import com.isra16.movienight.net.parseLibrary
import com.isra16.movienight.net.serverErrorMessage
import com.isra16.movienight.net.uploadFilename
import com.isra16.movienight.net.validateUpload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Estado de la lista de la biblioteca. */
sealed interface LibraryState {
    data object Loading : LibraryState
    data class Error(val message: String) : LibraryState
    data class Loaded(val items: List<LibraryItem>) : LibraryState
}

/**
 * Pantalla principal: biblioteca compartida (`GET /api/uploads`), subir un video del teléfono a esa
 * biblioteca (URL prefirmada de R2, Fase 4A), crear una sala con un video de la biblioteca
 * (`POST /create-room-from-upload`) y unirse a una sala por código o link.
 *
 * **La subida y la app en segundo plano (decisión de la Fase 4A):** la subida es una corrutina de este
 * ViewModel. Sobrevive a rotar la pantalla y a entrar a una sala (esta pantalla sigue en la pila), y se
 * cancela sola al cerrar sesión (el ViewModel muere). Al pasar la app a segundo plano NO se pausa ni se
 * cancela, pero tampoco hay servicio en primer plano ni notificación: sigue mientras Android mantenga
 * vivo el proceso, que no está garantizado (menos aún con la pantalla apagada o ahorro de batería). Si
 * el sistema lo mata, la subida se pierde y se empieza de nuevo: un PUT simple cortado no deja ningún
 * objeto en el bucket, así que no queda basura. Por eso la pantalla se mantiene encendida mientras
 * sube y avisa que conviene dejar la app abierta. Un servicio en primer plano queda como mejora si la
 * prueba real muestra cortes.
 *
 * Sin sesión válida el server contesta 401; en ese caso se le pide al `SessionManager` que vuelva a
 * consultar `/auth/me`, que pasa el estado a "sin sesión" y la raíz de la app muestra el login.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as MovieNightApp).container
    private val api = container.api
    private val baseUrl = container.baseUrl

    var library by mutableStateOf<LibraryState>(LibraryState.Loading)
        private set

    var isCreatingRoom by mutableStateOf(false)
        private set
    var createRoomError by mutableStateOf<String?>(null)
        private set
    var joinError by mutableStateOf<String?>(null)
        private set

    // --- Subir un video del teléfono (Fase 4A) ---------------------------------------------------

    private val uploader = container.uploader

    var upload by mutableStateOf<UploadState>(UploadState.Idle)
        private set

    /** `true` mientras se pide la URL o se está subiendo (no se puede empezar otra). */
    val isUploadBusy: Boolean
        get() = upload is UploadState.Preparing || upload is UploadState.Uploading

    private var uploadJob: Job? = null

    /** El video de la subida en curso o fallida (para reintentar). */
    private var picked: PickedVideo? = null

    /**
     * Número de la subida vigente. Cada subida nueva y cada cancelación lo suben; lo que llegue tarde de
     * una subida ya cancelada (un último avance desde el hilo de red) se ignora en [setUpload].
     */
    @Volatile
    private var uploadGeneration = 0

    init {
        refresh()
    }

    private fun setUpload(generation: Int, state: UploadState) {
        if (generation == uploadGeneration) upload = state
    }

    /** Resultado del selector del sistema; [uri] es `null` si la persona lo cerró sin elegir. */
    fun onVideoPicked(uri: Uri?) {
        if (uri == null || isUploadBusy) return
        releasePicked()
        val generation = ++uploadGeneration
        upload = UploadState.Preparing("")
        launchUpload(generation) {
            val video = uploader.inspect(uri)
            if (video == null) {
                setUpload(generation, UploadState.Failed("", "No se pudo leer el archivo elegido. Probá con otro.", false))
                return@launchUpload
            }
            val problem = validateUpload(video.fileName, video.sizeBytes)
            if (problem != null) {
                setUpload(generation, UploadState.Failed(video.fileName, problem, false))
                return@launchUpload
            }
            uploader.retainAccess(uri)
            picked = video
            runUpload(generation, video)
        }
    }

    /** Vuelve a intentar tras un fallo recuperable: pide una URL nueva y sube desde el principio (un PUT simple no se reanuda). */
    fun retryUpload() {
        val video = picked ?: return
        val failed = upload as? UploadState.Failed ?: return
        if (!failed.canRetry) return
        val generation = ++uploadGeneration
        upload = UploadState.Preparing(video.fileName)
        launchUpload(generation) { runUpload(generation, video) }
    }

    /** Corta la subida en curso (cancela la llamada de red) y vuelve al estado inicial. */
    fun cancelUpload() {
        if (!isUploadBusy) return
        uploadGeneration++
        uploadJob?.cancel()
        uploadJob = null
        releasePicked()
        upload = UploadState.Idle
    }

    /** Cierra el mensaje de "listo" o de error. */
    fun dismissUpload() {
        if (isUploadBusy) return
        releasePicked()
        upload = UploadState.Idle
    }

    private fun launchUpload(generation: Int, block: suspend () -> Unit) {
        uploadJob = viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setUpload(
                    generation,
                    UploadState.Failed(
                        picked?.fileName.orEmpty(),
                        "Pasó algo inesperado durante la subida (${e.javaClass.simpleName}). Intentá de nuevo.",
                        picked != null,
                    ),
                )
            }
        }
    }

    private suspend fun runUpload(generation: Int, video: PickedVideo) {
        setUpload(generation, UploadState.Preparing(video.fileName))
        val contentType = contentTypeFor(video.fileName)
        val presign = api.postJson(
            baseUrl,
            "/api/uploads/presign",
            JSONObject()
                .put("filename", uploadFilename(video.fileName))
                .put("contentType", contentType),
        )
        if (isSessionExpired(presign.code)) container.session.refresh()
        val ticket = when (val result = interpretPresign(presign.code, presign.body)) {
            is PresignResult.Failed -> {
                setUpload(generation, UploadState.Failed(video.fileName, result.failure.message, result.failure.canRetry))
                return
            }
            is PresignResult.Ok -> result.upload
        }

        setUpload(generation, UploadState.Uploading(video.fileName, 0L, video.sizeBytes))
        val outcome = uploader.put(ticket.uploadUrl, video, contentType) { sent ->
            setUpload(generation, UploadState.Uploading(video.fileName, sent, video.sizeBytes))
        }
        val failure = failureFromPut(outcome)
        if (failure != null) {
            setUpload(generation, UploadState.Failed(video.fileName, failure.message, failure.canRetry))
            return
        }

        // El PUT terminó: no hay paso de confirmación, el video ya aparece en GET /api/uploads.
        if (generation == uploadGeneration) {
            releasePicked()
            upload = UploadState.Done(video.fileName)
            refresh()
        }
    }

    private fun releasePicked() {
        picked?.let { uploader.releaseAccess(it.uri) }
        picked = null
    }

    override fun onCleared() {
        releasePicked()
        super.onCleared()
    }

    fun refresh() {
        library = LibraryState.Loading
        viewModelScope.launch {
            val result = api.get(baseUrl, "/api/uploads")
            if (isSessionExpired(result.code)) container.session.refresh()
            library = if (result.code == 200) {
                parseLibrary(result.body)?.let { LibraryState.Loaded(it) }
                    ?: LibraryState.Error("El servidor respondió algo inesperado. Revisá que la dirección del servidor sea correcta.")
            } else {
                LibraryState.Error(apiErrorMessage(result.code, serverErrorMessage(result.body)))
            }
        }
    }

    /**
     * Crea una sala con [item] y, si sale bien, llama a [onCreated] con el id (en el hilo principal).
     * [password] vacía = sala sin contraseña.
     */
    fun createRoom(item: LibraryItem, password: String, onCreated: (roomId: String) -> Unit) {
        if (isCreatingRoom) return
        isCreatingRoom = true
        createRoomError = null
        viewModelScope.launch {
            try {
                val trimmed = password.trim()
                val body = JSONObject().put("filename", item.filename)
                if (trimmed.isNotEmpty()) body.put("password", trimmed)
                val result = api.postJson(baseUrl, "/create-room-from-upload", body)
                if (isSessionExpired(result.code)) container.session.refresh()
                val roomId = if (result.code == 200) parseCreatedRoomId(result.body) else null
                if (roomId != null) {
                    if (trimmed.isNotEmpty()) container.roomPasswords.put(roomId, trimmed)
                    onCreated(roomId)
                } else {
                    createRoomError = if (result.code == 200) {
                        "El servidor respondió algo inesperado al crear la sala."
                    } else {
                        apiErrorMessage(result.code, serverErrorMessage(result.body))
                    }
                }
            } finally {
                isCreatingRoom = false
            }
        }
    }

    fun clearCreateRoomError() {
        createRoomError = null
    }

    /** Valida lo que se pegó (código o link) y llama a [onJoin] con el id de sala. Si la sala existe lo comprueba la pantalla de la sala. */
    fun joinByCode(input: String, onJoin: (roomId: String) -> Unit) {
        val roomId = extractRoomId(input)
        if (roomId == null) {
            joinError = "Eso no parece un código ni un link de sala."
            return
        }
        joinError = null
        onJoin(roomId)
    }

    fun clearJoinError() {
        joinError = null
    }
}
