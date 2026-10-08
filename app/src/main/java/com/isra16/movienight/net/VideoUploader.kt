package com.isra16.movienight.net

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.coroutines.resume

/** Video elegido con el selector del sistema. [sizeBytes] `<= 0` si el proveedor no supo decir cuánto pesa. */
data class PickedVideo(val uri: Uri, val fileName: String, val sizeBytes: Long)

/**
 * Sube un video del teléfono con el PUT de una URL prefirmada (la parte con Android/OkHttp de la
 * Fase 4A; las decisiones y validaciones puras están en `UploadLogic.kt`).
 *
 * - El archivo se lee con `ContentResolver.openInputStream` (un URI `content://` del selector del
 *   sistema: no hacen falta permisos de almacenamiento) y se escribe al socket de a 64 KB; nunca se
 *   carga entero en memoria.
 * - [http] tiene que ser un cliente SIN el CookieJar de la app: la sesión del servidor no tiene nada
 *   que hacer en el bucket, y los timeouts de las requests normales no sirven para varios GB.
 * - Cancelar la corrutina cancela la llamada de red.
 */
class VideoUploader(
    private val http: OkHttpClient,
    private val contentResolver: ContentResolver,
) {

    /**
     * Lee nombre y tamaño del URI elegido. `null` si el proveedor no deja ni consultarlo
     * (permiso, archivo borrado). Si no informa el tamaño, [PickedVideo.sizeBytes] queda en -1 y la
     * validación lo rechaza con un mensaje.
     */
    suspend fun inspect(uri: Uri): PickedVideo? = withContext(Dispatchers.IO) {
        try {
            var displayName: String? = null
            var size = -1L
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) displayName = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
            if (size <= 0L) {
                // Algunos proveedores no llenan SIZE: se le pregunta al descriptor del archivo.
                size = contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            }
            val name = resolveVideoFileName(displayName ?: uri.lastPathSegment, contentResolver.getType(uri))
            PickedVideo(uri, name, size)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Pide que el permiso de lectura del URI dure más que esta pantalla, para poder reintentar aunque la
     * persona salga y vuelva. Si el proveedor no lo permite no pasa nada: el permiso temporal alcanza
     * mientras viva el proceso.
     */
    fun retainAccess(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // No persistible: se sigue con el permiso temporal.
        }
    }

    /** Devuelve el permiso tomado con [retainAccess] (hay un tope de permisos persistentes por app). */
    fun releaseAccess(uri: Uri) {
        try {
            contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // No estaba tomado.
        }
    }

    /**
     * PUT de [video] a [uploadUrl] (la URL prefirmada, tal cual la devolvió el server: no se vuelve a
     * armar ni a codificar). [onProgress] recibe los bytes enviados, ya espaciados por
     * [shouldReportProgress], desde un hilo de OkHttp. [contentType] es el mismo que se pidió al server.
     */
    suspend fun put(
        uploadUrl: String,
        video: PickedVideo,
        contentType: String,
        onProgress: (sentBytes: Long) -> Unit,
    ): PutOutcome {
        val body = ContentUriRequestBody(
            contentResolver,
            video.uri,
            contentType.toMediaTypeOrNull(),
            video.sizeBytes,
            onProgress,
        )
        val request = try {
            Request.Builder().url(uploadUrl).put(body).build()
        } catch (e: IllegalArgumentException) {
            return PutOutcome.NetworkFailure("URL inválida: ${e.message}")
        }
        val call = http.newCall(request)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isActive) return
                    val sourceError = body.sourceError
                    continuation.resume(
                        if (sourceError != null) {
                            PutOutcome.SourceFailure(sourceError.message.orEmpty())
                        } else {
                            PutOutcome.NetworkFailure("${e.javaClass.simpleName}: ${e.message}")
                        },
                    )
                }

                override fun onResponse(call: Call, response: Response) {
                    val outcome = try {
                        response.use { PutOutcome.Response(it.code, it.body?.string().orEmpty()) }
                    } catch (e: IOException) {
                        PutOutcome.NetworkFailure("${e.javaClass.simpleName}: ${e.message}")
                    }
                    if (continuation.isActive) continuation.resume(outcome)
                }
            })
        }
    }
}

/**
 * Cuerpo del PUT que lee el archivo en streaming. `contentLength` es fijo (el tamaño que informó el
 * proveedor): el PUT prefirmado no admite `Transfer-Encoding: chunked`.
 *
 * OkHttp puede llamar a [writeTo] más de una vez (reintento tras una conexión caída): cada vez se abre
 * el archivo de nuevo desde el principio.
 */
private class ContentUriRequestBody(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val type: MediaType?,
    private val size: Long,
    private val onProgress: (Long) -> Unit,
) : RequestBody() {

    /**
     * Si el fallo vino de LEER el archivo (y no de la red). OkHttp tapa el tipo de las excepciones que
     * salen de `writeTo`, así que se anota acá para que quien llama pueda distinguirlas.
     */
    @Volatile
    var sourceError: Exception? = null
        private set

    override fun contentType(): MediaType? = type

    override fun contentLength(): Long = size

    override fun writeTo(sink: BufferedSink) {
        sourceError = null
        val input = try {
            contentResolver.openInputStream(uri) ?: throw FileNotFoundException("El proveedor no abrió el archivo.")
        } catch (e: FileNotFoundException) {
            sourceError = e
            throw e
        } catch (e: SecurityException) {
            sourceError = e
            throw IOException("Sin permiso para leer el archivo.", e)
        }
        try {
            input.use {
                copyStreamWithProgress(
                    input = it,
                    totalBytes = size,
                    buffer = ByteArray(UPLOAD_BUFFER_BYTES),
                    write = { buffer, offset, length -> sink.write(buffer, offset, length) },
                    onProgress = onProgress,
                )
            }
        } catch (e: SourceChangedException) {
            sourceError = e
            throw e
        }
    }
}
