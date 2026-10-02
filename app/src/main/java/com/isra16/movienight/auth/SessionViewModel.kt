package com.isra16.movienight.auth

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.isra16.movienight.MovieNightApp
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Estado de sesión para la raíz de la app: al crearse consulta `/auth/me`. */
class SessionViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as MovieNightApp).container
    private val session = container.session

    val state: StateFlow<SessionState> = session.state
    val serverUrl: String = container.baseUrl

    var isLoggingOut by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch { session.refresh() }
    }

    fun retry() {
        viewModelScope.launch { session.refresh() }
    }

    fun logout() {
        if (isLoggingOut) return
        isLoggingOut = true
        viewModelScope.launch {
            try {
                session.logout()
            } finally {
                isLoggingOut = false
            }
        }
    }
}
