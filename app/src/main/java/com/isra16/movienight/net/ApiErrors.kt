package com.isra16.movienight.net

/**
 * Convierte el resultado de un request "de datos" (biblioteca, crear sala, comprobar una sala) en un
 * mensaje para mostrarle a la persona. Es la hermana de [authErrorMessage], que es para el login.
 *
 * - [code] es el código HTTP, o `-1` si la request ni siquiera tuvo respuesta (red, TLS, URL mala).
 * - [serverMessage] es el campo `error` del JSON de respuesta, si venía.
 *
 * El `401` merece atención: con la app logueada solo puede significar que la sesión venció en el
 * servidor. Quien llama debería pedirle al `SessionManager` que vuelva a consultar `/auth/me`
 * (ver [isSessionExpired]), así la app pasa sola a la pantalla de login.
 *
 * Sin dependencias de Android, para poder testearla como JVM puro.
 */
fun apiErrorMessage(code: Int, serverMessage: String?): String {
    val fromServer = serverMessage?.trim()?.takeIf { it.isNotEmpty() }
    return when (code) {
        -1 -> "No se pudo conectar con el servidor. Revisá tu conexión a internet e intentá de nuevo."
        400 -> fromServer ?: "La solicitud no es válida."
        401 -> "Tu sesión venció. Iniciá sesión de nuevo."
        403 -> fromServer ?: "No tenés permiso para hacer eso."
        404 -> fromServer ?: "No se encontró lo que buscabas en el servidor."
        429 -> fromServer ?: "Demasiados intentos. Esperá unos minutos e intentá de nuevo."
        502, 503, 504 -> fromServer ?: "El servidor no está disponible en este momento. Intentá de nuevo en un rato."
        in 500..599 -> fromServer ?: "El servidor tuvo un problema. Intentá de nuevo en un momento."
        else -> "Respuesta inesperada del servidor (código $code)."
    }
}

/** `true` si el código indica que la sesión ya no es válida en el servidor. */
fun isSessionExpired(code: Int): Boolean = code == 401
