package com.isra16.movienight.net

import org.json.JSONArray
import org.json.JSONException
import kotlin.math.roundToLong

/** Un video de la biblioteca compartida (`GET /api/uploads`). */
data class LibraryItem(
    /** Identificador opaco: nombre de archivo en disco o key de R2. Se reenvía tal cual al crear la sala. */
    val filename: String,
    val displayName: String,
    val sizeBytes: Long,
    val modifiedMillis: Long,
)

/**
 * Parsea la respuesta de `GET /api/uploads`: un array `[{ filename, displayName, size, mtime }]`.
 * `null` si el cuerpo no es un array JSON (ej. una página HTML de error del túnel). Los elementos
 * sin `filename` se descartan. El orden se respeta: el server ya manda lo más nuevo primero.
 */
fun parseLibrary(body: String): List<LibraryItem>? {
    val array = try {
        JSONArray(body)
    } catch (e: JSONException) {
        return null
    }
    val items = ArrayList<LibraryItem>(array.length())
    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val filename = obj.optString("filename", "")
        if (filename.isBlank()) continue
        items += LibraryItem(
            filename = filename,
            displayName = obj.optString("displayName", "").ifBlank { filename },
            sizeBytes = obj.optLong("size", 0L),
            modifiedMillis = obj.optLong("mtime", 0L),
        )
    }
    return items
}

/** `roomId` de la respuesta de `POST /create-room-from-upload` (`{ roomId, hostToken }`), o `null`. */
fun parseCreatedRoomId(body: String): String? =
    parseJsonObject(body)?.optString("roomId", "")?.takeIf { it.isNotBlank() }

/** Tamaño legible: `850 B`, `512 KB`, `1,5 MB`, `2,0 GB` (coma decimal, la app está en español). */
fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "${bytes.coerceAtLeast(0)} B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${kb.roundToLong()} KB"
    val mb = kb / 1024.0
    if (mb < 1024) return withOneDecimal(mb) + " MB"
    return withOneDecimal(mb / 1024.0) + " GB"
}

private fun withOneDecimal(value: Double): String {
    val tenths = (value * 10).roundToLong()
    return "${tenths / 10},${tenths % 10}"
}
