package com.isra16.movienight.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.isra16.movienight.MovieNightApp
import com.isra16.movienight.net.LibraryItem
import com.isra16.movienight.net.apiErrorMessage
import com.isra16.movienight.net.extractRoomId
import com.isra16.movienight.net.isSessionExpired
import com.isra16.movienight.net.parseCreatedRoomId
import com.isra16.movienight.net.parseLibrary
import com.isra16.movienight.net.serverErrorMessage
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Estado de la lista de la biblioteca. */
sealed interface LibraryState {
    data object Loading : LibraryState
    data class Error(val message: String) : LibraryState
    data class Loaded(val items: List<LibraryItem>) : LibraryState
}

/**
 * Pantalla principal: biblioteca compartida (`GET /api/uploads`), crear una sala con un video de la
 * biblioteca (`POST /create-room-from-upload`) y unirse a una sala por código o link.
 *
 * Sin sesión válida el server contesta 401; en ese caso se le pide al `SessionManager` que vuelva a
 * consultar `/auth/me`, que pasa el estado a "sin sesión" y la raíz de la app muestra el login.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as MovieNightApp).container
    private val api = container.api
    private val baseUrl = container.baseUrl

    var library by mutableStateOf<LibraryState>(LibraryState.Loading)
        private set

    var isCreatingRoom by mutableStateOf(false)
        private set
    var createRoomError by mutableStateOf<String?>(null)
        private set
    var joinError by mutableStateOf<String?>(null)
        private set

    init {
        refresh()
    }

    fun refresh() {
        library = LibraryState.Loading
        viewModelScope.launch {
            val result = api.get(baseUrl, "/api/uploads")
            if (isSessionExpired(result.code)) container.session.refresh()
            library = if (result.code == 200) {
                parseLibrary(result.body)?.let { LibraryState.Loaded(it) }
                    ?: LibraryState.Error("El servidor respondió algo inesperado. Revisá que la dirección del servidor sea correcta.")
            } else {
                LibraryState.Error(apiErrorMessage(result.code, serverErrorMessage(result.body)))
            }
        }
    }

    /**
     * Crea una sala con [item] y, si sale bien, llama a [onCreated] con el id (en el hilo principal).
     * [password] vacía = sala sin contraseña.
     */
    fun createRoom(item: LibraryItem, password: String, onCreated: (roomId: String) -> Unit) {
        if (isCreatingRoom) return
        isCreatingRoom = true
        createRoomError = null
        viewModelScope.launch {
            try {
                val trimmed = password.trim()
                val body = JSONObject().put("filename", item.filename)
                if (trimmed.isNotEmpty()) body.put("password", trimmed)
                val result = api.postJson(baseUrl, "/create-room-from-upload", body)
                if (isSessionExpired(result.code)) container.session.refresh()
                val roomId = if (result.code == 200) parseCreatedRoomId(result.body) else null
                if (roomId != null) {
                    if (trimmed.isNotEmpty()) container.roomPasswords.put(roomId, trimmed)
                    onCreated(roomId)
                } else {
                    createRoomError = if (result.code == 200) {
                        "El servidor respondió algo inesperado al crear la sala."
                    } else {
                        apiErrorMessage(result.code, serverErrorMessage(result.body))
                    }
                }
            } finally {
                isCreatingRoom = false
            }
        }
    }

    fun clearCreateRoomError() {
        createRoomError = null
    }

    /** Valida lo que se pegó (código o link) y llama a [onJoin] con el id de sala. Si la sala existe lo comprueba la pantalla de la sala. */
    fun joinByCode(input: String, onJoin: (roomId: String) -> Unit) {
        val roomId = extractRoomId(input)
        if (roomId == null) {
            joinError = "Eso no parece un código ni un link de sala."
            return
        }
        joinError = null
        onJoin(roomId)
    }

    fun clearJoinError() {
        joinError = null
    }
}
