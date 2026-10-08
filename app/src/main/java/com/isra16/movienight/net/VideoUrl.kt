package com.isra16.movienight.net

private val URL_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
private const val HEX_DIGITS = "0123456789ABCDEF"

/**
 * URL absoluta del video de la sala a partir de `room.videoFile`, que el server manda de dos formas
 * (`videoUrlForUploadedFile` en `server.js`):
 *  - `/uploads/<archivo>` cuando los videos viven en disco: hay que anteponerle la URL del servidor;
 *  - una URL absoluta (`https://...`) cuando viven en Cloudflare R2: se usa tal cual.
 *
 * Los nombres de archivo pueden llevar espacios (el server permite `[a-zA-Z0-9 _-]`), y un espacio
 * crudo en la request HTTP la vuelve inválida: la web se lo deja al navegador, acá se codifica a mano
 * (sin tocar los `%XX` que ya vengan codificados).
 *
 * `null` si no hay video, si el esquema no es http/https (nunca debería pasar, pero no se le pasa
 * `file:` o `content:` al reproductor) o si es una ruta relativa y no hay URL de servidor.
 */
fun resolveVideoUrl(baseUrl: String, videoFile: String?): String? {
    val raw = videoFile?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val absolute = when {
        raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true) -> raw
        URL_SCHEME.containsMatchIn(raw) -> return null // file:, content:, javascript:...
        raw.startsWith("//") -> return null // sin esquema ni ruta propia: el server nunca lo manda
        else -> {
            val base = baseUrl.trim().trimEnd('/')
            if (base.isEmpty()) return null
            if (raw.startsWith("/")) base + raw else "$base/$raw"
        }
    }
    return percentEncodeUnsafe(absolute)
}

/**
 * URL absoluta del subtítulo (`room.subtitleFile`). El server siempre lo guarda en disco como
 * `/uploads/<hex>.vtt` (a diferencia del video, nunca va a R2), pero se resuelve con la misma regla.
 */
fun resolveSubtitleUrl(baseUrl: String, subtitleFile: String?): String? = resolveVideoUrl(baseUrl, subtitleFile)

/**
 * ¿Hay que (re)cargar el video en el reproductor? Misma regla que la web:
 *  - `room-data` llega en cada `join-room`, o sea también al reconectar tras un corte de red: si el
 *    video ya es ese, no se toca (recargar lo mandaría al segundo 0 sin motivo) -> [force] = false;
 *  - `video-changed` es el host cambiando la cinta: se recarga siempre, aunque la URL sea la misma
 *    (el server también vuelve la posición a 0) -> [force] = true.
 * Sin URL nueva solo hay algo que hacer si había un video cargado (quitarlo).
 */
fun shouldLoadVideo(loadedUrl: String?, newUrl: String?, force: Boolean): Boolean =
    if (newUrl == null) loadedUrl != null else force || loadedUrl != newUrl

/**
 * Texto para la persona a partir del `errorCode` de `PlaybackException` (Media3). Los números son los
 * de las constantes `PlaybackException.ERROR_CODE_*`, verificados contra el código fuente de Media3
 * 1.11.1; se usan como número para que esta función se pueda probar sin la librería.
 */
fun playbackErrorMessage(errorCode: Int): String = when (errorCode) {
    2001, 2002 -> "No se pudo cargar el video: revisá tu conexión."
    2004, 2005 -> "El servidor no encontró el video. Puede que lo hayan borrado de la biblioteca."
    2007 -> "El video está en una dirección sin cifrar (http) y la app no la permite."
    in 3001..3004 -> "El archivo de video está dañado o tiene un formato que no se puede leer."
    in 4001..4006 -> "Este teléfono no puede reproducir este video."
    else -> "No se pudo reproducir el video."
}

/**
 * Codifica (`%XX`, en UTF-8) lo que no puede ir crudo en una URL: espacios, comillas, no ASCII...
 * Deja tal cual los caracteres reservados de URL (`/ : ? # & = ...`) y los `%XX` ya codificados.
 */
private fun percentEncodeUnsafe(url: String): String {
    val out = StringBuilder(url.length + 16)
    var i = 0
    while (i < url.length) {
        val c = url[i]
        val alreadyEncoded = c == '%' && i + 2 < url.length && isHex(url[i + 1]) && isHex(url[i + 2])
        if (isUrlSafe(c) || alreadyEncoded) {
            out.append(c)
            i++
        } else {
            val charCount = Character.charCount(url.codePointAt(i))
            for (b in url.substring(i, i + charCount).toByteArray(Charsets.UTF_8)) {
                val v = b.toInt() and 0xFF
                out.append('%').append(HEX_DIGITS[v shr 4]).append(HEX_DIGITS[v and 0x0F])
            }
            i += charCount
        }
    }
    return out.toString()
}

private fun isHex(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

private fun isUrlSafe(c: Char): Boolean =
    c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in "-._~:/?#[]@!\$&'()*+,;="
