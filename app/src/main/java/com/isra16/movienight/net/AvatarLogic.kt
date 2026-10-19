package com.isra16.movienight.net

/**
 * Lógica pura de los avatares de cuenta (Fase 8, «Avatares de cuenta»). Sin Android ni `org.json`, para poder probarla con un arnés
 * de JVM. El contrato del servidor está en `docs/API-CONTRATO.md`, sección «Avatares».
 */

/** Un avatar que ofrece el servidor (`GET /api/avatars`). [url] ya es absoluta (ver [avatarAbsoluteUrl]) y conserva el `?v=`. */
data class Avatar(val id: String, val name: String, val url: String)

/** Tamaño al que se decodifican los iconos (el servidor los manda de 256x256). */
const val AVATAR_DECODE_TARGET_PX = 128

/** Tiempo mínimo entre dos intentos de bajar la lista de avatares después de un fallo (el botón «Reintentar» no lo respeta). */
const val AVATAR_LIST_RETRY_MS = 30_000L

/** Lo que se guarda en disco: nada con más de 30 días y, en total, unos 5 MB. */
const val AVATAR_CACHE_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
const val AVATAR_CACHE_MAX_BYTES = 5L * 1024 * 1024

private const val FNV_OFFSET_BASIS_64 = 0xcbf29ce484222325UL
private const val FNV_PRIME_64 = 0x100000001b3UL

/**
 * Junta la dirección del servidor con la `url` relativa de un avatar (`/avatars/<archivo>?v=<hash>`) sin duplicar ni perder la `/`.
 * Si [relative] ya es absoluta (`http://` o `https://`) se deja igual. Vacía o en blanco da `null`; sin dirección de servidor
 * y con una url relativa también (no hay a dónde apuntar). Conserva el `?v=`.
 */
fun avatarAbsoluteUrl(baseUrl: String, relative: String): String? {
    val path = relative.trim()
    if (path.isEmpty()) return null
    if (path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true)) return path
    val base = baseUrl.trim().trimEnd('/')
    if (base.isEmpty()) return null
    return base + "/" + path.trimStart('/')
}

/**
 * Nombre de archivo estable y seguro para guardar el icono de [url] en disco: FNV-1a de 64 bits sobre los bytes UTF-8 de la URL
 * COMPLETA (con el `?v=`), en hexadecimal de 16 cifras, más `.png`. Así un icono reemplazado en el servidor (otro `?v=`) no se
 * confunde con el viejo, y la URL nunca llega al sistema de archivos.
 */
fun avatarCacheKey(url: String): String {
    var hash = FNV_OFFSET_BASIS_64
    for (byte in url.toByteArray(Charsets.UTF_8)) {
        hash = hash xor (byte.toULong() and 0xFFUL)
        hash *= FNV_PRIME_64
    }
    return hash.toString(16).padStart(16, '0') + ".png"
}

/** El avatar con ese [id], o `null` si el id es `null`, está vacío o no existe (un id desconocido no es un error: sin avatar). */
fun avatarById(list: List<Avatar>, id: String?): Avatar? =
    if (id.isNullOrBlank()) null else list.firstOrNull { it.id == id }

/**
 * La inicial para el círculo sin imagen: la primera letra o dígito de [name], en mayúscula. Recorre por puntos de código, así que un
 * par sustituto (una letra fuera del plano básico) sale entero; salta espacios y símbolos (también los emoji). Sin ninguno da «?».
 */
fun avatarInitial(name: String): String {
    var i = 0
    while (i < name.length) {
        val codePoint = name.codePointAt(i)
        if (Character.isLetterOrDigit(codePoint)) {
            return String(Character.toChars(Character.toUpperCase(codePoint)))
        }
        i += Character.charCount(codePoint)
    }
    return "?"
}

/**
 * El `inSampleSize` de `BitmapFactory` para decodificar una imagen de [srcWidth] x [srcHeight] sin bajar de [targetPx] en ningún lado:
 * la potencia de 2 más grande que deja los dos lados >= [targetPx]. Mínimo 1 (también con medidas o destino no positivos).
 */
fun avatarSampleSize(srcWidth: Int, srcHeight: Int, targetPx: Int): Int {
    if (srcWidth <= 0 || srcHeight <= 0 || targetPx <= 0) return 1
    var sample = 1
    while (sample < (1 shl 29) && srcWidth / (sample * 2) >= targetPx && srcHeight / (sample * 2) >= targetPx) sample *= 2
    return sample
}

/** El mensaje común de los errores de red y de servidor de los avatares; `null` si [code] pide un texto propio. */
private fun commonAvatarError(code: Int): String? = when {
    code == -1 -> MSG_NO_CONNECTION
    code == 429 -> MSG_TOO_MANY_ATTEMPTS
    code in 500..599 -> MSG_SERVER_PROBLEM
    else -> null
}

/** Texto para la persona cuando `PUT /auth/avatar` falla con [code] (`-1` = sin respuesta). Sin códigos ni voseo. */
fun avatarChangeMessage(code: Int): String = commonAvatarError(code) ?: when (code) {
    401 -> "Tu sesión terminó. Inicia sesión de nuevo."
    400 -> "Ese avatar ya no está disponible. Elige otro."
    404 -> "No se pudo guardar el avatar. Inténtalo más tarde."
    else -> MSG_UNEXPECTED_RESPONSE
}

/** Texto para la persona cuando `GET /api/avatars` falla con [code] (`-1` = sin respuesta). */
fun avatarListErrorMessage(code: Int): String = commonAvatarError(code) ?: MSG_UNEXPECTED_RESPONSE

/**
 * ¿Se puede volver a pedir la lista de avatares sin que la persona lo pida? Sí si nunca falló o si pasaron [AVATAR_LIST_RETRY_MS] desde
 * el último fallo (un reloj que retrocede también lo permite). El botón «Reintentar» no pasa por aquí.
 */
fun shouldRetryAvatarList(lastFailureAtMillis: Long?, nowMillis: Long): Boolean =
    lastFailureAtMillis == null || nowMillis < lastFailureAtMillis || nowMillis - lastFailureAtMillis >= AVATAR_LIST_RETRY_MS

/** Un archivo de la caché de imágenes, para decidir cuáles borrar. */
data class AvatarCacheEntry(val name: String, val sizeBytes: Long, val lastModifiedMillis: Long)

/**
 * Qué archivos de la caché de disco hay que borrar: primero todos los que tienen más de [maxAgeMillis]; después, si lo que queda pasa de
 * [maxTotalBytes], los más viejos (por fecha de última modificación) hasta que quepa. Devuelve los nombres, sin repetir.
 */
fun avatarCacheFilesToDelete(
    entries: List<AvatarCacheEntry>,
    nowMillis: Long,
    maxAgeMillis: Long = AVATAR_CACHE_MAX_AGE_MS,
    maxTotalBytes: Long = AVATAR_CACHE_MAX_BYTES,
): List<String> {
    val (expired, alive) = entries.partition { nowMillis - it.lastModifiedMillis > maxAgeMillis }
    val toDelete = expired.map { it.name }.toMutableList()
    var total = alive.sumOf { it.sizeBytes }
    for (entry in alive.sortedBy { it.lastModifiedMillis }) {
        if (total <= maxTotalBytes) break
        toDelete += entry.name
        total -= entry.sizeBytes
    }
    return toDelete.distinct()
}
