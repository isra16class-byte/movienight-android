package com.isra16.movienight.auth

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.isra16.movienight.MovieNightApp
import kotlinx.coroutines.launch

/**
 * Estado compartido de las pantallas de login, registro y recuperar contraseña (solo una se ve a la
 * vez). Los campos de texto viven en cada pantalla; acá solo el resultado de la acción en curso.
 * Cuando login/registro salen bien no hace falta navegar: [SessionManager] cambia el estado de sesión
 * y la raíz de la app reemplaza las pantallas de auth por las de adentro.
 */
class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val session = (app as MovieNightApp).container.session

    var isBusy by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var infoMessage by mutableStateOf<String?>(null)
        private set

    /** Cada pantalla lo llama al entrar, para no arrastrar el error de la anterior. */
    fun clearMessages() {
        errorMessage = null
        infoMessage = null
    }

    fun login(email: String, password: String) = launchAuth {
        session.login(email, password)
    }

    fun register(email: String, password: String) = launchAuth {
        session.register(email, password)
    }

    fun forgotPassword(email: String) = launchAuth(onSuccess = { infoMessage = FORGOT_PASSWORD_INFO }) {
        session.forgotPassword(email)
    }

    private fun launchAuth(onSuccess: () -> Unit = {}, action: suspend () -> AuthResult) {
        if (isBusy) return
        clearMessages()
        isBusy = true
        viewModelScope.launch {
            try {
                when (val result = action()) {
                    AuthResult.Success -> onSuccess()
                    is AuthResult.Failure -> errorMessage = result.message
                }
            } finally {
                isBusy = false
            }
        }
    }

    private companion object {
        // Texto propio (no el del server): el server responde igual exista o no la cuenta.
        const val FORGOT_PASSWORD_INFO =
            "Si ese email tiene una cuenta, te mandamos un link para elegir una contraseña nueva. " +
                "Revisá tu correo (y la carpeta de spam). El link se abre en el navegador."
    }
}
