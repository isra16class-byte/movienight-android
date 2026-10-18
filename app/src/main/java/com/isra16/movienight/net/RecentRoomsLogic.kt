package com.isra16.movienight.net

/**
 * "Mis salas" (Home > Salas): lógica pura, sin Android ni org.json. La lista es local (por cuenta, en este teléfono): el servidor no
 * tiene un endpoint de "mis salas". Cada sala se comprueba con `GET /api/room/:id` (200 existe, 404 no existe).
 */

/** Máximo de salas guardadas por cuenta. */
const val RECENT_ROOMS_MAX = 20

/** Una sala a la que la persona entró: id (ya normalizado), nombre del video al entrar y cuándo fue la última vez. */
data class RecentRoom(val roomId: String, val videoName: String, val lastJoinedMillis: Long)

/**
 * Qué se supo de una sala al consultarla. Solo un 404 prueba que ya no existe: sin red (-1), 429, 5xx, 401 o 403 no dicen nada
 * sobre la sala y no permiten quitarla de la lista.
 */
enum class RoomCheckStatus { ACTIVE, GONE, UNKNOWN }

fun roomCheckStatus(code: Int): RoomCheckStatus = when (code) {
    200 -> RoomCheckStatus.ACTIVE
    404 -> RoomCheckStatus.GONE
    else -> RoomCheckStatus.UNKNOWN
}

/** ¿Falló la comprobación por falta de red? (`MovieNightApi` devuelve -1 cuando no hubo respuesta). */
fun roomCheckFailedByNetwork(code: Int): Boolean = code == -1

/** Id con el que se guarda una sala: el mismo criterio que al unirse (`extractRoomId`); si no se reconoce, se deja recortado. */
fun normalizeRecentRoomId(raw: String): String = extractRoomId(raw) ?: raw.trim()

/**
 * La sala va primero con la hora nueva; si ya estaba se reemplaza (sin duplicar); si el nombre nuevo viene vacío se conserva el
 * anterior; si hay más de [max] se descarta la última (la más antigua: la lista va de la más reciente a la más antigua).
 */
fun upsertRecentRoom(
    list: List<RecentRoom>,
    roomId: String,
    videoName: String,
    nowMillis: Long,
    max: Int = RECENT_ROOMS_MAX,
): List<RecentRoom> {
    val previous = list.firstOrNull { it.roomId == roomId }
    val name = videoName.trim().ifEmpty { previous?.videoName.orEmpty() }
    return (listOf(RecentRoom(roomId, name, nowMillis)) + list.filter { it.roomId != roomId }).take(max)
}

fun removeRecentRoom(list: List<RecentRoom>, roomId: String): List<RecentRoom> = list.filter { it.roomId != roomId }

// --- Formato de almacenamiento: una línea por sala, campos separados por tabulador ---------------------------------

private fun cleanField(text: String): String = text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

fun encodeRecentRooms(list: List<RecentRoom>): String =
    list.joinToString("\n") { "${cleanField(it.roomId)}\t${cleanField(it.videoName)}\t${it.lastJoinedMillis}" }

/** Nunca lanza: con `null`, vacío o basura devuelve lo que se pueda leer (posiblemente nada). */
fun decodeRecentRooms(raw: String?): List<RecentRoom> {
    if (raw.isNullOrEmpty()) return emptyList()
    return raw.split('\n').mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size != 3) return@mapNotNull null
        val id = parts[0]
        val millis = parts[2].toLongOrNull()
        if (id.isBlank() || id.any { it.isWhitespace() } || millis == null || millis < 0) null else RecentRoom(id, parts[1], millis)
    }.take(RECENT_ROOMS_MAX)
}

// --- Lo que se muestra --------------------------------------------------------------------------------------------

/** Una fila de "Mis salas": lo guardado más lo último que se supo del servidor. */
data class RecentRoomItem(
    val roomId: String,
    val videoName: String,
    val lastJoinedMillis: Long,
    val passwordProtected: Boolean = false,
    val status: RoomCheckStatus = RoomCheckStatus.UNKNOWN,
)

fun RecentRoom.toItem(previous: RecentRoomItem? = null): RecentRoomItem =
    RecentRoomItem(roomId, videoName, lastJoinedMillis, previous?.passwordProtected ?: false, previous?.status ?: RoomCheckStatus.UNKNOWN)

/**
 * Aplica el resultado de comprobar una sala a la lista que se muestra. GONE la quita; ACTIVE actualiza si pide contraseña
 * (`passwordProtected` null = no se pudo leer: se conserva lo anterior); UNKNOWN la conserva marcada como sin verificar.
 * Una sala que ya no está en la lista (por ejemplo, la persona la quitó mientras se comprobaba) no se vuelve a agregar.
 */
fun applyRoomCheck(
    items: List<RecentRoomItem>,
    roomId: String,
    status: RoomCheckStatus,
    passwordProtected: Boolean?,
): List<RecentRoomItem> = when (status) {
    RoomCheckStatus.GONE -> items.filter { it.roomId != roomId }
    RoomCheckStatus.ACTIVE -> items.map {
        if (it.roomId == roomId) it.copy(status = status, passwordProtected = passwordProtected ?: it.passwordProtected) else it
    }
    RoomCheckStatus.UNKNOWN -> items.map { if (it.roomId == roomId) it.copy(status = status) else it }
}

/** Nombre del video, o "Sala <id>" si no hay nombre. */
fun recentRoomTitle(item: RecentRoomItem): String = item.videoName.ifBlank { "Sala ${item.roomId}" }

/** "Sala <id> · Última vez: hace 2 h" y, si pide contraseña, " · Con contraseña". */
fun recentRoomSubtitle(item: RecentRoomItem, nowMillis: Long): String =
    "Sala ${item.roomId} · Última vez: ${relativeTimeLabel(nowMillis, item.lastJoinedMillis)}" +
        if (item.passwordProtected) " · Con contraseña" else ""

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS

/** "hace un momento" (menos de 1 min o una hora futura), "hace N min", "hace N h", "hace 1 día", "hace N días". */
fun relativeTimeLabel(nowMillis: Long, thenMillis: Long): String {
    val diff = nowMillis - thenMillis
    return when {
        diff < MINUTE_MS -> "hace un momento"
        diff < HOUR_MS -> "hace ${diff / MINUTE_MS} min"
        diff < DAY_MS -> "hace ${diff / HOUR_MS} h"
        diff < 2 * DAY_MS -> "hace 1 día"
        else -> "hace ${diff / DAY_MS} días"
    }
}
