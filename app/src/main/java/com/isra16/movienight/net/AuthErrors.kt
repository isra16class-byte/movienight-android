package com.isra16.movienight.net

/**
 * Convierte el resultado de un request de los endpoints de cuenta (`/auth/...`) en un mensaje para mostrarle a la persona.
 *
 * - [code] es el código HTTP, o `-1` si la request ni siquiera tuvo respuesta (red, TLS, URL mala;
 *   ver `MovieNightApi.Result`).
 * - [serverMessage] es el campo `error` del JSON de respuesta (`{ "error": "..." }`), si venía.
 *
 * Los mensajes del server ya están en español y son específicos donde importa (400: "El email no
 * tiene un formato válido." vs. "La contraseña necesita al menos 8 caracteres."; 429: cuántos
 * minutos esperar), así que en esos casos se prefieren. En el resto se usa texto propio, porque el
 * cuerpo puede no ser del server (una página HTML de error de Cloudflare cuando el túnel está caído).
 *
 * Sin dependencias de Android, para poder testearla como JVM puro.
 */
fun authErrorMessage(code: Int, serverMessage: String?): String {
    val fromServer = serverMessage?.trim()?.takeIf { it.isNotEmpty() }
    return when (code) {
        -1 -> MSG_NO_CONNECTION
        400 -> fromServer ?: "Los datos no son válidos. Revisa el correo y la contraseña."
        401 -> "Correo o contraseña incorrectos."
        404 -> "No se encontró el servicio de cuentas. Revisa la dirección del servidor."
        409 -> "Ya existe una cuenta con ese correo."
        429 -> fromServer ?: MSG_TOO_MANY_ATTEMPTS
        502, 503, 504 -> MSG_SERVER_UNAVAILABLE
        in 500..599 -> fromServer ?: MSG_SERVER_PROBLEM
        else -> MSG_UNEXPECTED_RESPONSE
    }
}
