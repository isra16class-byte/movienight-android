package com.isra16.movienight.auth

/** Estado de la sesión de la cuenta, que decide qué parte de la app se muestra. */
sealed interface SessionState {
    /** Todavía no se sabe: se está consultando `GET /auth/me`. */
    data object Loading : SessionState

    /** El server respondió que no hay sesión iniciada: se muestra login/registro. */
    data object LoggedOut : SessionState

    data class LoggedIn(val id: String, val email: String) : SessionState

    /**
     * No se pudo averiguar el estado (sin red, servidor caído, respuesta rara). Es distinto de
     * [LoggedOut]: con una sesión guardada y el server momentáneamente inaccesible, mandar a la
     * persona a la pantalla de login sería engañoso. Se ofrece reintentar.
     */
    data class Unreachable(val message: String) : SessionState
}

/** Resultado de una acción de cuenta (login, registro, recuperar contraseña). */
sealed interface AuthResult {
    data object Success : AuthResult
    data class Failure(val message: String) : AuthResult
}
