package com.isra16.movienight.home

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.isra16.movienight.MovieNightApp
import com.isra16.movienight.net.CREATE_ROOM_FROM_UPLOAD_PATH
import com.isra16.movienight.net.CreateRoomResult
import com.isra16.movienight.net.LibraryItem
import com.isra16.movienight.net.MovieNightApi
import com.isra16.movienight.net.UploadFlow
import com.isra16.movienight.net.UploadGoal
import com.isra16.movienight.net.UploadState
import com.isra16.movienight.net.apiErrorMessage
import com.isra16.movienight.net.createRoomBody
import com.isra16.movienight.net.extractRoomId
import com.isra16.movienight.net.interpretCreateRoom
import com.isra16.movienight.net.isBusy
import com.isra16.movienight.net.isSessionExpired
import com.isra16.movienight.net.parseLibrary
import com.isra16.movienight.net.serverErrorMessage
import com.isra16.movienight.net.toFollowUp
import kotlinx.coroutines.launch

/** Estado de la lista de la biblioteca. */
sealed interface LibraryState {
    data object Loading : LibraryState
    data class Error(val message: String) : LibraryState
    data class Loaded(val items: List<LibraryItem>) : LibraryState
}

/**
 * `GET /api/uploads` ya interpretado. Lo usan la pantalla principal y el cambio de video dentro de una
 * sala. Si el server contesta 401 llama a [onSessionExpired] (la app vuelve sola al login).
 */
suspend fun fetchLibrary(api: MovieNightApi, baseUrl: String, onSessionExpired: suspend () -> Unit): LibraryState {
    val result = api.get(baseUrl, "/api/uploads")
    if (isSessionExpired(result.code)) onSessionExpired()
    return if (result.code == 200) {
        parseLibrary(result.body)?.let { LibraryState.Loaded(it) }
            ?: LibraryState.Error("El servidor respondió algo inesperado. Revisá que la dirección del servidor sea correcta.")
    } else {
        LibraryState.Error(apiErrorMessage(result.code, serverErrorMessage(result.body)))
    }
}

/**
 * Pantalla principal: biblioteca compartida (`GET /api/uploads`), subir un video del teléfono a esa
 * biblioteca (URL prefirmada de R2, Fase 4A), crear una sala con un video de la biblioteca
 * (`POST /create-room-from-upload`), subir un video y crear la sala en un solo paso (Fase 4B) y unirse a
 * una sala por código o link.
 *
 * **La subida y la app en segundo plano (decisión de la Fase 4A):** la subida ([UploadFlow]) es una
 * corrutina de este ViewModel. Sobrevive a rotar la pantalla y a entrar a una sala (esta pantalla sigue en
 * la pila), y se cancela sola al cerrar sesión (el ViewModel muere). Al pasar la app a segundo plano NO se
 * pausa ni se cancela, pero tampoco hay servicio en primer plano ni notificación: sigue mientras Android
 * mantenga vivo el proceso, que no está garantizado (menos aún con la pantalla apagada o ahorro de
 * batería). Si el sistema lo mata, la subida se pierde y se empieza de nuevo: un PUT simple cortado no deja
 * ningún objeto en el bucket, así que no queda basura. Por eso la pantalla se mantiene encendida mientras
 * sube y avisa que conviene dejar la app abierta. Un servicio en primer plano queda como mejora si la
 * prueba real muestra cortes.
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

    // --- Subir un video del teléfono (Fase 4A) y crear la sala con él (Fase 4B) --------------------

    private val flow = UploadFlow(
        scope = viewModelScope,
        uploader = container.uploader,
        api = api,
        baseUrl = baseUrl,
        onSessionExpired = { container.session.refresh() },
        onLibraryChanged = { refresh() },
    )

    val upload: UploadState
        get() = flow.state

    /** `true` mientras se pide la URL, se sube o se termina el paso siguiente (no se puede empezar otra). */
    val isUploadBusy: Boolean
        get() = flow.state.isBusy()

    /**
     * Contraseña con la que crear la sala cuando termine la subida que está por empezar ("Subir y crear
     * sala"); `null` = solo subir a la biblioteca. Se guarda acá porque el selector del sistema devuelve
     * el video a la pantalla, no a este ViewModel.
     */
    private var pendingCreatePassword: String? = null

    init {
        refresh()
    }

    /**
     * Antes de abrir el selector para "Subir y crear sala": recuerda la contraseña ([password] vacía = sala
     * sin contraseña). El video elegido llega a [onVideoPicked].
     */
    fun prepareUploadAndCreateRoom(password: String) {
        if (isUploadBusy) return
        pendingCreatePassword = password
    }

    /** Resultado del selector del sistema; [uri] es `null` si la persona lo cerró sin elegir. */
    fun onVideoPicked(uri: Uri?) {
        val password = pendingCreatePassword
        pendingCreatePassword = null
        if (uri == null || isUploadBusy) return
        if (password == null) {
            flow.start(uri)
        } else {
            flow.start(uri, UploadGoal.CREATE_ROOM) { key -> createRoomFromKey(key, password).toFollowUp() }
        }
    }

    /** Vuelve a intentar tras un fallo recuperable: si la subida salió bien y falló crear la sala, repite solo eso. */
    fun retryUpload() = flow.retry()

    /** Corta la subida en curso (cancela la llamada de red) y vuelve al estado inicial. */
    fun cancelUpload() = flow.cancel()

    /** Cierra el mensaje de "listo" o de error. */
    fun dismissUpload() = flow.dismiss()

    override fun onCleared() {
        flow.release()
        super.onCleared()
    }

    fun refresh() {
        library = LibraryState.Loading
        viewModelScope.launch {
            library = fetchLibrary(api, baseUrl) { container.session.refresh() }
        }
    }

    /**
     * `POST /create-room-from-upload` con la key de un video de la biblioteca. Si sale bien y la sala lleva
     * contraseña, la deja en memoria para que la pantalla de la sala no la vuelva a pedir.
     */
    private suspend fun createRoomFromKey(key: String, password: String): CreateRoomResult {
        val result = api.postJson(baseUrl, CREATE_ROOM_FROM_UPLOAD_PATH, createRoomBody(key, password))
        if (isSessionExpired(result.code)) container.session.refresh()
        val parsed = interpretCreateRoom(result.code, result.body)
        val trimmed = password.trim()
        if (parsed is CreateRoomResult.Ok && trimmed.isNotEmpty()) container.roomPasswords.put(parsed.roomId, trimmed)
        return parsed
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
                when (val result = createRoomFromKey(item.filename, password)) {
                    is CreateRoomResult.Ok -> onCreated(result.roomId)
                    is CreateRoomResult.Failed -> {
                        createRoomError = result.failure.message
                        // El server borra de la biblioteca un video que no pasó la validación de contenido.
                        if (result.failure.libraryChanged) refresh()
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
