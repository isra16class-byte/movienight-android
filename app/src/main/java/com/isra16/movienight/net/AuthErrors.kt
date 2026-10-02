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
        -1 -> "No se pudo conectar con el servidor. Revisá tu conexión a internet e intentá de nuevo."
        400 -> fromServer ?: "Los datos no son válidos. Revisá el email y la contraseña."
        401 -> "Email o contraseña incorrectos."
        404 -> "El servidor no tiene las cuentas habilitadas (o la dirección del servidor es incorrecta)."
        409 -> "Ya existe una cuenta con ese email."
        429 -> fromServer ?: "Demasiados intentos. Esperá unos minutos e intentá de nuevo."
        502, 503, 504 -> "El servidor no está disponible en este momento. Intentá de nuevo en un rato."
        in 500..599 -> fromServer ?: "El servidor tuvo un problema. Intentá de nuevo en un momento."
        else -> "Respuesta inesperada del servidor (código $code)."
    }
}
