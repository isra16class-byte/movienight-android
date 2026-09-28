package com.isra16.movienight.net

/**
 * Deja la URL del servidor en la forma `https://host[:puerto]` (sin barra final).
 * Si la persona no escribe esquema se asume https, que es lo que expone el túnel de Cloudflare.
 * Devuelve "" si no hay nada escrito.
 */
fun normalizeBaseUrl(raw: String): String {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isEmpty()) return ""
    val hasScheme = trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    return if (hasScheme) trimmed else "https://$trimmed"
}
