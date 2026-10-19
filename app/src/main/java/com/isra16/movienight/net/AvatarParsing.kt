package com.isra16.movienight.net

import org.json.JSONObject

/**
 * Parseo con `org.json` de las respuestas de avatares (Fase 8, «Avatares de cuenta»). Aparte de [AvatarLogic.kt] para que la lógica pura no
 * dependa de `org.json`: estos se prueban con Gradle (`AvatarParsingTest`).
 */

/**
 * `GET /api/avatars` -> `{ avatars: [{ id, name, url }] }`, con cada `url` ya absoluta ([avatarAbsoluteUrl]). `null` si el cuerpo no es
 * un objeto o no trae `avatars` (el servidor no es el esperado). Una entrada sin `id` o sin `url` utilizable se descarta; sin `name` se
 * usa el id.
 */
fun parseAvatarList(body: String, baseUrl: String): List<Avatar>? {
    val array = parseJsonObject(body)?.optJSONArray("avatars") ?: return null
    return (0 until array.length()).mapNotNull { i ->
        val item = array.optJSONObject(i) ?: return@mapNotNull null
        val id = item.stringOrNull("id") ?: return@mapNotNull null
        val url = avatarAbsoluteUrl(baseUrl, item.stringOrNull("url").orEmpty()) ?: return@mapNotNull null
        Avatar(id = id, name = item.stringOrNull("name") ?: id, url = url)
    }
}

/** El id del avatar de la cuenta en la respuesta de `GET /auth/me` (`avatar: { id, name, url } | null`); `null` si no tiene o no viene. */
fun avatarIdFromMe(json: JSONObject): String? = json.optJSONObject("avatar")?.stringOrNull("id")

/** Texto de la clave, o `null` si falta, vale `null` o está vacío (el `org.json` de Android devuelve "null" con `optString`). */
private fun JSONObject.stringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key, "").takeIf { it.isNotEmpty() }
