package com.isra16.movienight.net

private val BARE_ROOM_ID = Regex("^[A-Za-z0-9_-]{3,40}$")
private val HEX_ROOM_ID = Regex("^[0-9A-Fa-f]{6}$")
private val ROOM_PATH = Regex("/room/([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)

/**
 * Saca el id de sala de lo que la persona pegó: el código suelto (`a1b2c3`) o el link completo
 * (`https://servidor/room/a1b2c3`, con o sin query). `null` si no se parece a ninguno.
 *
 * El server genera ids de 6 caracteres hexadecimales en minúscula y los busca sin normalizar, así que
 * si lo que llega tiene esa forma se pasa a minúscula (típico al copiar o dictar un código).
 */
fun extractRoomId(raw: String): String? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    val id = ROOM_PATH.find(text)?.groupValues?.get(1)
        ?: text.takeIf { BARE_ROOM_ID.matches(it) }
        ?: return null
    return if (HEX_ROOM_ID.matches(id)) id.lowercase() else id
}

/**
 * ¿Este `room-error` del `join-room` es por la contraseña de la sala (mal puesta o bloqueada por
 * demasiados intentos)? En ese caso se vuelve a pedir la contraseña; en cualquier otro (la sala ya
 * no existe) no tiene sentido. El server solo manda texto, no un código, así que se mira el mensaje.
 */
fun isRoomPasswordError(message: String): Boolean {
    val lower = message.lowercase()
    return "contraseña" in lower || "intentos" in lower
}

/**
 * Nombre legible del video a partir de `room.videoFile` (`/uploads/123__mi-peli.mp4` -> `mi-peli.mp4`).
 * Misma regla que `videoDisplayName` del server: se queda con el último tramo de la ruta y, si trae el
 * separador `__`, con lo que viene después.
 */
fun videoDisplayName(videoFile: String?): String {
    if (videoFile.isNullOrBlank()) return ""
    val last = videoFile.substringBefore('?').substringAfterLast('/')
    val decoded = try {
        java.net.URLDecoder.decode(last.replace("+", "%2B"), "UTF-8")
    } catch (e: IllegalArgumentException) {
        last // un % suelto que no es una secuencia válida: se deja tal cual
    }
    val idx = decoded.indexOf("__")
    return if (idx >= 0) decoded.substring(idx + 2) else decoded
}
