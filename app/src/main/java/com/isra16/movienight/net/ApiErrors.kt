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
        -1 -> MSG_NO_CONNECTION
        400 -> fromServer ?: "La solicitud no es válida."
        401 -> "Tu sesión venció. Inicia sesión de nuevo."
        403 -> fromServer ?: "No tienes permiso para hacer eso."
        404 -> fromServer ?: "No se encontró lo que buscabas."
        429 -> fromServer ?: MSG_TOO_MANY_ATTEMPTS
        502, 503, 504 -> fromServer ?: MSG_SERVER_UNAVAILABLE
        in 500..599 -> fromServer ?: MSG_SERVER_PROBLEM
        else -> MSG_UNEXPECTED_RESPONSE
    }
}

/** `true` si el código indica que la sesión ya no es válida en el servidor. */
fun isSessionExpired(code: Int): Boolean = code == 401
