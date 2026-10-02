package com.isra16.movienight.net

import org.json.JSONException
import org.json.JSONObject

/** Parsea [body] como objeto JSON; `null` si no lo es (ej. una página HTML de error de Cloudflare). */
fun parseJsonObject(body: String): JSONObject? =
    try {
        JSONObject(body)
    } catch (e: JSONException) {
        null
    }

/** Campo `error` de las respuestas de error del backend (`{ "error": "..." }`), si hay. */
fun serverErrorMessage(body: String): String? =
    parseJsonObject(body)?.optString("error", "")?.takeIf { it.isNotBlank() }
