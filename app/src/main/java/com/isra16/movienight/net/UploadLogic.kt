package com.isra16.movienight.net

import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URISyntaxException
import java.text.Normalizer
import java.util.Locale

/*
 * Lógica pura de la subida de video (Fase 4A): sin imports de Android, para poder probarla como JVM puro.
 * La parte que toca el teléfono (selector, ContentResolver, OkHttp) vive en `VideoUploader.kt`.
 *
 * Contrato verificado en `movienight` (rama plan-produccion), server.js + lib/r2.js:
 *  - `POST /api/uploads/presign`, body JSON `{ filename, contentType }`. Pide sesión (la cookie que ya
 *    usa la app) o `x-library-password`. Devuelve `{ key, uploadUrl, expiresIn }`.
 *    Errores: 400 (falta filename), 401, 404 (el server no tiene R2), 413 (límite de la biblioteca,
 *    con `error`), 429, 502.
 *  - El PUT va directo al bucket con `uploadUrl`. La URL firma SOLO el header `host`
 *    (`X-Amz-SignedHeaders=host`, comprobado generando una URL con el `lib/r2.js` real): el
 *    `Content-Type` no entra en la firma, pero se manda igual, el mismo que se pidió, como hace la web.
 *  - Después del PUT NO hay paso de confirmación: `GET /api/uploads` lista el bucket entero filtrando
 *    solo por extensión, así que el video aparece en cuanto el PUT termina. El contenido (que sea un
 *    video de verdad) recién se valida cuando alguien crea una sala con él (`create-room-from-upload`),
 *    y si no pasa, el server lo borra.
 *  - La key es `<8 hex>__<nombre saneado><extensión tal cual>`; el saneado quita todo lo que no sea
 *    `[a-zA-Z0-9 _-]` (las tildes se pierden: "Película" queda "Pelcula"). Por eso [uploadFilename]
 *    las normaliza antes.
 */

/** Extensiones que el server lista en la biblioteca (`VIDEO_EXTENSIONS` de server.js). Minúsculas, con punto. */
val UPLOAD_VIDEO_EXTENSIONS: List<String> = listOf(".mp4", ".mkv", ".mov", ".webm", ".avi", ".m4v")

/**
 * Máximo de un PUT simple a R2: 5 GiB menos 5 MiB (4,995 GiB), según la documentación de límites de
 * Cloudflare R2 (no son 5 GiB redondos: un archivo entre los dos valores pasaría un chequeo ingenuo y
 * R2 lo rechazaría recién al final de la subida).
 */
const val MAX_SIMPLE_PUT_BYTES: Long = 5L * 1024 * 1024 * 1024 - 5L * 1024 * 1024

/** El límite de arriba, redondeado hacia abajo, para mostrarlo en pantalla ("4,99 GB", GB de 1024 MB como en la app). */
const val MAX_SIMPLE_PUT_LABEL: String = "4,99 GB"

/** Tamaño del búfer con el que se lee el archivo: lo único que vive en memoria de él. */
const val UPLOAD_BUFFER_BYTES: Int = 64 * 1024

/** Extensión (minúsculas, con punto) si [fileName] termina en un formato que el server lista; `null` si no. */
fun videoExtensionOf(fileName: String): String? {
    val name = fileName.trim()
    val dot = name.lastIndexOf('.')
    // dot == 0 es un archivo oculto (".mp4"): path.extname() del server devuelve '' para eso.
    if (dot <= 0) return null
    return name.substring(dot).lowercase(Locale.ROOT).takeIf { it in UPLOAD_VIDEO_EXTENSIONS }
}

/** Content-Type a mandar según la extensión. Se usa el MISMO valor al pedir la URL y al hacer el PUT. */
fun contentTypeFor(fileName: String): String = when (videoExtensionOf(fileName)) {
    ".mp4" -> "video/mp4"
    ".m4v" -> "video/x-m4v"
    ".mov" -> "video/quicktime"
    ".webm" -> "video/webm"
    ".mkv" -> "video/x-matroska"
    ".avi" -> "video/x-msvideo"
    else -> "application/octet-stream"
}

/**
 * Nombre del archivo elegido, con el tipo MIME como respaldo: algunos proveedores (ej. Drive) entregan
 * un nombre sin extensión. Si el nombre ya trae una extensión válida se respeta; si no, se agrega la
 * que corresponde al [mimeType]; si tampoco se reconoce, se devuelve el nombre tal cual (y la
 * validación lo rechaza con un mensaje claro).
 */
fun resolveVideoFileName(displayName: String?, mimeType: String?): String {
    val name = displayName?.trim().orEmpty()
    if (videoExtensionOf(name) != null) return name
    val ext = when (mimeType?.trim()?.lowercase(Locale.ROOT)) {
        "video/mp4" -> ".mp4"
        "video/x-m4v" -> ".m4v"
        "video/quicktime" -> ".mov"
        "video/webm" -> ".webm"
        "video/x-matroska" -> ".mkv"
        "video/x-msvideo", "video/avi", "video/vnd.avi" -> ".avi"
        else -> null
    }
    val base = name.ifEmpty { "video" }
    return if (ext != null) base + ext else base
}

/**
 * Nombre que se manda en `filename` al pedir la URL: sin tildes (el server las borraría) y con la
 * extensión en minúsculas. Si no es un formato válido devuelve el nombre recortado, sin tocar.
 */
fun uploadFilename(fileName: String): String {
    val name = fileName.trim()
    val ext = videoExtensionOf(name) ?: return name
    val base = name.substring(0, name.length - ext.length)
    val plain = Normalizer.normalize(base, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    return plain + ext
}

/**
 * Valida lo que se eligió ANTES de pedir nada al server. Devuelve el mensaje para la persona, o `null`
 * si se puede subir. [sizeBytes] `<= 0` significa vacío o tamaño desconocido (hace falta conocerlo: el
 * PUT prefirmado no admite subida "chunked" y R2 pide `Content-Length`).
 */
fun validateUpload(fileName: String, sizeBytes: Long): String? {
    if (videoExtensionOf(fileName) == null) {
        return "Ese tipo de archivo no se puede subir. Formatos aceptados: " +
            UPLOAD_VIDEO_EXTENSIONS.joinToString(", ") { it.removePrefix(".").uppercase(Locale.ROOT) } + "."
    }
    if (sizeBytes <= 0L) {
        return "El archivo está vacío o no se pudo saber cuánto pesa."
    }
    if (sizeBytes > MAX_SIMPLE_PUT_BYTES) {
        return "El archivo pesa ${formatFileSize(sizeBytes)} y el máximo para subir desde la app es $MAX_SIMPLE_PUT_LABEL " +
            "(el límite de una subida simple a la nube)."
    }
    return null
}

// --- Respuesta de /api/uploads/presign ---------------------------------------------------------------

/** `{ key, uploadUrl, expiresIn }` de `POST /api/uploads/presign`. */
data class PresignedUpload(val key: String, val uploadUrl: String, val expiresInSeconds: Long)

/** Por qué falló un paso y si tiene sentido ofrecer "Reintentar". */
data class UploadFailure(val message: String, val canRetry: Boolean)

sealed interface PresignResult {
    data class Ok(val upload: PresignedUpload) : PresignResult
    data class Failed(val failure: UploadFailure) : PresignResult
}

/** `true` si [url] es una URL https con host (la del bucket siempre lo es). */
fun isHttpsUrl(url: String): Boolean =
    try {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrEmpty()
    } catch (e: URISyntaxException) {
        false
    }

/** Interpreta la respuesta de `POST /api/uploads/presign`. [code] `-1` = sin respuesta (red). */
fun interpretPresign(code: Int, body: String): PresignResult {
    if (code == 200) {
        val obj = parseJsonObject(body)
        val key = obj?.optString("key", "")?.trim().orEmpty()
        val url = obj?.optString("uploadUrl", "")?.trim().orEmpty()
        if (key.isNotEmpty() && isHttpsUrl(url)) {
            return PresignResult.Ok(PresignedUpload(key, url, obj!!.optLong("expiresIn", 0L)))
        }
        return PresignResult.Failed(
            UploadFailure("El servidor respondió algo inesperado al preparar la subida. Intentá de nuevo.", true),
        )
    }
    val server = serverErrorMessage(body)
    val failure = when (code) {
        404 -> UploadFailure(
            "Este servidor no tiene la subida directa a la nube (Cloudflare R2) configurada, así que no se puede subir desde la app.",
            false,
        )
        413 -> UploadFailure(
            server ?: "La biblioteca llegó a su límite de almacenamiento. Borrá algún video desde la web e intentá de nuevo.",
            false,
        )
        400, 401, 403 -> UploadFailure(apiErrorMessage(code, server), false)
        else -> UploadFailure(apiErrorMessage(code, server), code == -1 || code == 429 || code in 500..599)
    }
    return PresignResult.Failed(failure)
}

// --- Resultado del PUT al bucket ---------------------------------------------------------------------

sealed interface PutOutcome {
    /** El bucket contestó. [body] es el XML de error de R2 si no fue 2xx. */
    data class Response(val code: Int, val body: String) : PutOutcome

    /** No hubo respuesta: se cortó la red, TLS, timeout. */
    data class NetworkFailure(val detail: String) : PutOutcome

    /** No se pudo leer el archivo del teléfono (lo movieron/borraron, perdió el permiso, cambió de tamaño). */
    data class SourceFailure(val detail: String) : PutOutcome
}

/** `<Code>` del XML de error que devuelve R2/S3, si lo hay. */
fun r2ErrorCode(body: String): String? =
    Regex("<Code>([^<]+)</Code>").find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

/** `null` si el PUT salió bien (2xx); si no, el mensaje y si conviene ofrecer reintentar. */
fun failureFromPut(outcome: PutOutcome): UploadFailure? = when (outcome) {
    is PutOutcome.NetworkFailure -> UploadFailure(
        "Se cortó la conexión durante la subida. Al reintentar empieza de nuevo desde el principio.",
        true,
    )
    is PutOutcome.SourceFailure -> UploadFailure(
        "No se pudo leer el archivo del teléfono (¿lo moviste, lo borraste o cambió mientras subía?). Elegilo de nuevo.",
        false,
    )
    is PutOutcome.Response -> if (outcome.code in 200..299) null else failureFromBucket(outcome.code, outcome.body)
}

private fun failureFromBucket(code: Int, body: String): UploadFailure {
    val errorCode = r2ErrorCode(body)
    val expired = errorCode.equals("ExpiredRequest", ignoreCase = true) ||
        (code == 403 && body.contains("expired", ignoreCase = true))
    return when {
        errorCode.equals("EntityTooLarge", ignoreCase = true) -> UploadFailure(
            "La nube rechazó el archivo por ser demasiado grande (máximo $MAX_SIMPLE_PUT_LABEL).",
            false,
        )
        expired -> UploadFailure(
            "El permiso de subida venció (pasa si la subida tarda demasiado). Reintentá: se pide uno nuevo y la subida empieza de cero.",
            true,
        )
        code == 403 -> UploadFailure(
            "La nube rechazó la subida (permiso o firma no válidos). Reintentá; si se repite, avisá a quien administra el servidor.",
            true,
        )
        code == 408 || code == 429 || code in 500..599 -> UploadFailure(
            "La nube no respondió bien (código $code). Reintentá en un momento.",
            true,
        )
        else -> UploadFailure("La nube rechazó la subida (código $code).", true)
    }
}

// --- Estado de la subida, para la pantalla -----------------------------------------------------------

/** Para qué se sube el video (Fase 4B): decide qué pasa apenas termina el PUT. */
enum class UploadGoal {
    /** Solo a la biblioteca (Fase 4A). */
    LIBRARY,

    /** "Subir y crear sala": después del PUT se crea una sala con el video. */
    CREATE_ROOM,

    /** Desde dentro de una sala, como host: después del PUT se cambia el video de la sala. */
    CHANGE_ROOM_VIDEO,
}

sealed interface UploadState {
    data object Idle : UploadState

    /** Pidiendo la URL prefirmada. */
    data class Preparing(val fileName: String) : UploadState

    data class Uploading(val fileName: String, val sentBytes: Long, val totalBytes: Long) : UploadState {
        val fraction: Float get() = uploadProgressFraction(sentBytes, totalBytes)
    }

    /** El PUT terminó bien (el video ya está en la biblioteca) y se está haciendo lo que venía después. */
    data class Finishing(val fileName: String, val goal: UploadGoal) : UploadState

    /** Terminó todo. [roomId] solo si [goal] es [UploadGoal.CREATE_ROOM]. */
    data class Done(
        val fileName: String,
        val goal: UploadGoal = UploadGoal.LIBRARY,
        val roomId: String? = null,
    ) : UploadState

    /**
     * Falló. [alreadyUploaded] = el video YA quedó en la biblioteca y lo que falló fue el paso siguiente
     * (crear la sala o cambiar el video): reintentar repite solo ese paso, no la subida.
     */
    data class Failed(
        val fileName: String,
        val message: String,
        val canRetry: Boolean,
        val alreadyUploaded: Boolean = false,
        /** Para qué era la subida: la pantalla solo ofrece "elegir otro video" si era a la biblioteca (ver [UploadGoal]). */
        val goal: UploadGoal = UploadGoal.LIBRARY,
    ) : UploadState
}

/** Hay algo en marcha (no se puede empezar otra subida). */
fun UploadState.isBusy(): Boolean =
    this is UploadState.Preparing || this is UploadState.Uploading || this is UploadState.Finishing

/**
 * Se puede cortar solo mientras se pide la URL o se sube. Con [UploadState.Finishing] ya no: el POST que
 * crea la sala o cambia el video dura un instante y cortarlo dejaría en duda si la sala se creó.
 */
fun UploadState.canCancel(): Boolean = this is UploadState.Preparing || this is UploadState.Uploading

/** Texto de "estoy terminando" para [UploadState.Finishing]. */
fun finishingLabel(goal: UploadGoal): String = when (goal) {
    UploadGoal.LIBRARY -> "Terminando…"
    UploadGoal.CREATE_ROOM -> "Creando la sala…"
    UploadGoal.CHANGE_ROOM_VIDEO -> "Cambiando el video de la sala…"
}

/** Mensaje de [UploadState.Done]. */
fun doneMessage(done: UploadState.Done): String = when (done.goal) {
    UploadGoal.LIBRARY -> "Listo: ${done.fileName} ya está en la biblioteca."
    UploadGoal.CREATE_ROOM -> "Sala creada con ${done.fileName}."
    UploadGoal.CHANGE_ROOM_VIDEO -> "Listo: la sala ahora tiene ${done.fileName}."
}

/** Aclaración que acompaña a un [UploadState.Failed] con `alreadyUploaded`: la subida en sí salió bien. */
const val ALREADY_UPLOADED_NOTE: String = "El video sí se subió y está en la biblioteca."

/** Estado final tras el paso que sigue a la subida. */
fun stateAfterFollowUp(fileName: String, goal: UploadGoal, outcome: FollowUpOutcome): UploadState = when (outcome) {
    is FollowUpOutcome.Done -> UploadState.Done(fileName, goal, outcome.roomId)
    is FollowUpOutcome.Failed -> UploadState.Failed(
        fileName,
        outcome.failure.message,
        outcome.failure.canRetry,
        alreadyUploaded = true,
        goal = goal,
    )
}

fun uploadProgressFraction(sent: Long, total: Long): Float =
    if (total <= 0L) 0f else (sent.toDouble() / total).coerceIn(0.0, 1.0).toFloat()

/** `"512 MB de 1,2 GB · 41%"`. */
fun progressLabel(sent: Long, total: Long): String {
    val percent = if (total <= 0L) 0L else (sent.coerceAtLeast(0L) * 100 / total).coerceIn(0L, 100L)
    return "${formatFileSize(sent)} de ${formatFileSize(total)} · $percent%"
}

/**
 * ¿Vale la pena avisarle a la pantalla del avance? Se reporta cada 0,5 % del archivo (mínimo 512 KB) y
 * una vez al llegar al final, para no recomponer la UI miles de veces en una subida de varios GB.
 */
fun shouldReportProgress(lastReported: Long, sent: Long, total: Long): Boolean {
    if (sent >= total) return sent != lastReported
    val step = maxOf(total / 200, 512L * 1024)
    return sent - lastReported >= step
}

// --- Lectura en streaming del archivo ----------------------------------------------------------------

/** El archivo no entregó los bytes que se anunciaron (se achicó o creció mientras se subía). */
class SourceChangedException(message: String) : IOException(message)

/**
 * Copia EXACTAMENTE [totalBytes] bytes de [input] hacia [write], de a [buffer].size como mucho, así que
 * en memoria solo vive el búfer, nunca el archivo. Llama a [onProgress] con el total enviado según
 * [shouldReportProgress]. Devuelve los bytes copiados.
 *
 * Tira [SourceChangedException] si el archivo termina antes de lo anunciado, o si todavía tiene bytes
 * después (el `Content-Length` ya salió con [totalBytes]: mandar un archivo cortado en silencio sería peor).
 */
fun copyStreamWithProgress(
    input: InputStream,
    totalBytes: Long,
    buffer: ByteArray,
    write: (buffer: ByteArray, offset: Int, length: Int) -> Unit,
    onProgress: (sentBytes: Long) -> Unit,
): Long {
    var sent = 0L
    var lastReported = 0L
    while (sent < totalBytes) {
        val wanted = minOf(buffer.size.toLong(), totalBytes - sent).toInt()
        val read = input.read(buffer, 0, wanted)
        if (read <= 0) {
            throw SourceChangedException("El archivo terminó antes de lo esperado ($sent de $totalBytes bytes).")
        }
        write(buffer, 0, read)
        sent += read
        if (shouldReportProgress(lastReported, sent, totalBytes)) {
            lastReported = sent
            onProgress(sent)
        }
    }
    if (input.read() != -1) {
        throw SourceChangedException("El archivo creció mientras se subía.")
    }
    return sent
}
