package com.isra16.movienight.auth

import com.isra16.movienight.net.MovieNightApi
import com.isra16.movienight.net.PersistentCookieJar
import com.isra16.movienight.net.authErrorMessage
import com.isra16.movienight.net.parseJsonObject
import com.isra16.movienight.net.serverErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Dueño del estado de sesión y de las acciones de cuenta (endpoints `/auth/...`, ver docs/API-CONTRATO.md).
 *
 * La sesión vive en el servidor (cookie `movienight.sid`, httpOnly, que guarda el
 * [PersistentCookieJar]); acá solo se refleja si hay una. La única forma de saberlo es preguntar a
 * `GET /auth/me`, por eso [refresh] se llama al abrir la app.
 *
 * Esta app exige cuenta: no maneja el flujo anónimo (sin `hostToken`). Ver docs/MEMORIA.md.
 */
class SessionManager(
    private val api: MovieNightApi,
    private val cookieJar: PersistentCookieJar,
    private val baseUrl: String,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Consulta `GET /auth/me` y actualiza [state]. Se puede llamar de nuevo para reintentar. */
    suspend fun refresh() {
        if (_state.value is SessionState.Unreachable) _state.value = SessionState.Loading
        val result = api.get(baseUrl, "/auth/me")
        val json = if (result.code == 200) parseJsonObject(result.body) else null
        _state.value = when {
            json == null -> SessionState.Unreachable(
                if (result.code == 200) {
                    // 200 con un cuerpo que no es JSON = no es el backend (URL equivocada, página de error).
                    "El servidor respondió algo inesperado. Revisá que la dirección del servidor sea correcta."
                } else {
                    authErrorMessage(result.code, serverErrorMessage(result.body))
                },
            )
            json.optBoolean("loggedIn", false) -> loggedIn(json, fallbackEmail = "")
            else -> SessionState.LoggedOut
        }
    }

    suspend fun login(email: String, password: String): AuthResult {
        val trimmedEmail = email.trim()
        val result = api.postJson(
            baseUrl, "/auth/login",
            JSONObject().put("email", trimmedEmail).put("password", password),
        )
        if (result.code != 200) {
            return AuthResult.Failure(authErrorMessage(result.code, serverErrorMessage(result.body)))
        }
        // El server dijo OK, pero si la cookie no quedó guardada la próxima request ya no tendría
        // sesión. Pasa, por ejemplo, contra un server HTTP sin SESSION_COOKIE_INSECURE=1 (la cookie
        // sale con secure:true y OkHttp no la reenvía por HTTP). Mejor avisarlo que fingir un login.
        if (!cookieJar.hasCookie(SESSION_COOKIE)) {
            return AuthResult.Failure(
                "El servidor aceptó el login pero la sesión no se guardó. " +
                    "Si estás probando por HTTP, el server necesita SESSION_COOKIE_INSECURE=1.",
            )
        }
        _state.value = loggedIn(parseJsonObject(result.body), fallbackEmail = trimmedEmail)
        return AuthResult.Success
    }

    /**
     * Crea la cuenta y después inicia sesión. Hace falta el segundo paso porque
     * `POST /auth/register` responde `201 { id, email }` pero NO deja una sesión iniciada.
     */
    suspend fun register(email: String, password: String): AuthResult {
        val result = api.postJson(
            baseUrl, "/auth/register",
            JSONObject().put("email", email.trim()).put("password", password),
        )
        if (result.code != 201) {
            return AuthResult.Failure(authErrorMessage(result.code, serverErrorMessage(result.body)))
        }
        return when (val loginResult = login(email, password)) {
            AuthResult.Success -> AuthResult.Success
            is AuthResult.Failure -> AuthResult.Failure(
                "Tu cuenta se creó, pero no pudimos iniciar sesión automáticamente. " +
                    "Probá iniciar sesión. (${loginResult.message})",
            )
        }
    }

    /**
     * Pide el link de recuperación. El server responde 200 con el mismo mensaje exista o no la
     * cuenta (anti-enumeración), así que un éxito acá no confirma nada. El link llega por email y
     * apunta a `reset-password.html` del servidor: se abre en el navegador.
     */
    suspend fun forgotPassword(email: String): AuthResult {
        val result = api.postJson(baseUrl, "/auth/forgot-password", JSONObject().put("email", email.trim()))
        return if (result.code == 200) {
            AuthResult.Success
        } else {
            AuthResult.Failure(authErrorMessage(result.code, serverErrorMessage(result.body)))
        }
    }

    /**
     * Cierra la sesión. Siempre termina cerrándola en el dispositivo (borra la cookie), aunque el
     * `POST /auth/logout` falle por falta de red: la persona pidió salir y no debe quedar atrapada.
     * En ese caso la sesión del lado del servidor vence sola (30 días), pero ya sin cookie que la use.
     */
    suspend fun logout() {
        api.postJson(baseUrl, "/auth/logout")
        cookieJar.clear()
        _state.value = SessionState.LoggedOut
    }

    private fun loggedIn(json: JSONObject?, fallbackEmail: String): SessionState.LoggedIn =
        SessionState.LoggedIn(
            id = json?.optString("id", "").orEmpty(),
            email = json?.optString("email", "")?.takeIf { it.isNotBlank() } ?: fallbackEmail,
        )

    private companion object {
        const val SESSION_COOKIE = "movienight.sid"
    }
}
