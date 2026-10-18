package com.isra16.movienight.home

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.isra16.movienight.MovieNightApp
import com.isra16.movienight.auth.SessionState
import com.isra16.movienight.auth.displayNameError
import com.isra16.movienight.auth.effectiveUsername
import com.isra16.movienight.net.ADMIN_PROBE_PATH
import com.isra16.movienight.net.AdminAccess
import com.isra16.movienight.net.CREATE_ROOM_FROM_UPLOAD_PATH
import com.isra16.movienight.net.CreateRoomResult
import com.isra16.movienight.net.LibraryItem
import com.isra16.movienight.net.MovieNightApi
import com.isra16.movienight.net.UploadFlow
import com.isra16.movienight.net.UploadGoal
import com.isra16.movienight.net.UploadState
import com.isra16.movienight.net.adminAccessFrom
import com.isra16.movienight.net.adminPanelUrl
import com.isra16.movienight.net.applyRoomCheck
import com.isra16.movienight.net.MSG_WRONG_SERVER_ADDRESS
import com.isra16.movienight.net.RECENT_ROOMS_CHECK_INTERVAL_MS
import com.isra16.movienight.net.RECENT_ROOMS_PARALLEL_CHECKS
import com.isra16.movienight.net.RecentRoomItem
import com.isra16.movienight.net.RoomCheckStatus
import com.isra16.movienight.net.apiErrorMessage
import com.isra16.movienight.net.createRoomBody
import com.isra16.movienight.net.extractRoomId
import com.isra16.movienight.net.interpretCreateRoom
import com.isra16.movienight.net.isBusy
import com.isra16.movienight.net.isSessionExpired
import com.isra16.movienight.net.mergeAdminAccess
import com.isra16.movienight.net.parseJsonObject
import com.isra16.movienight.net.parseLibrary
import com.isra16.movienight.net.roomCheckFailedByNetwork
import com.isra16.movienight.net.roomCheckStatus
import com.isra16.movienight.net.serverErrorMessage
import com.isra16.movienight.net.toFollowUp
import com.isra16.movienight.net.toItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val MSG_NAME_NOT_SAVED = "No se pudo guardar. Inténtalo de nuevo."

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
            ?: LibraryState.Error(MSG_WRONG_SERVER_ADDRESS)
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
 * **La subida y la app en segundo plano (Fases 4A y 6C):** la subida ([UploadFlow]) es una corrutina de
 * este ViewModel. Sobrevive a rotar la pantalla y a entrar a una sala (esta pantalla sigue en la pila), y se
 * cancela sola al cerrar sesión o al cerrar la app desde recientes (el ViewModel muere). Al pasar la app a
 * segundo plano NO se pausa ni se cancela. Sin más, Android corta la red de una app en segundo plano ("Se
 * cortó la conexión"), así que mientras hay una subida en marcha un servicio en primer plano
 * ([com.isra16.movienight.notify.UploadKeepAlive]) mantiene vivo el proceso y muestra una notificación de
 * avance; cuando termina o falla, un aviso local lo cuenta si la app no se está viendo. Si el sistema aun así
 * mata el proceso, la subida se pierde y se empieza de nuevo: un PUT simple cortado no deja ningún objeto en
 * el bucket, así que no queda basura. La pantalla se mantiene encendida mientras sube.
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

    // --- Acceso al panel de administración (Fase 6B) ------------------------------------------------

    /**
     * Si la cuenta es admin. El server no lo dice en `/auth/me`: se averigua con un GET de solo lectura
     * ([ADMIN_PROBE_PATH]). Empieza en [AdminAccess.UNKNOWN] (sin botón) y muere con este ViewModel al
     * cerrar sesión, así que una cuenta nueva nunca hereda el acceso de la anterior.
     */
    var adminAccess by mutableStateOf(AdminAccess.UNKNOWN)
        private set

    /** Link del panel web (`/admin.html`), que se abre en el navegador del teléfono. */
    val adminPanelLink: String
        get() = adminPanelUrl(baseUrl)

    /** Vuelve a preguntar si la cuenta es admin. Una respuesta dudosa (sin red, 5xx) no cambia lo ya sabido. */
    fun refreshAdminAccess() {
        viewModelScope.launch {
            val result = api.get(baseUrl, ADMIN_PROBE_PATH)
            adminAccess = mergeAdminAccess(adminAccess, adminAccessFrom(result.code, result.body))
        }
    }

    // --- Mis salas (Home > Salas) --------------------------------------------------------------------

    /** Las salas a las que la cuenta entró en este teléfono y que siguen existiendo (o no se pudo comprobar), de la más reciente a la más antigua. */
    var recentRooms by mutableStateOf<List<RecentRoomItem>>(emptyList())
        private set

    /** Alguna comprobación falló por falta de red: la lista se ve, pero no se sabe si las salas siguen activas. */
    var recentRoomsOffline by mutableStateOf(false)
        private set

    var isCheckingRecentRooms by mutableStateOf(false)
        private set

    private var recentCheckJob: Job? = null
    private var lastRecentCheckMillis = 0L

    private fun accountId(): String? = (container.session.state.value as? SessionState.LoggedIn)?.id

    // --- Nombre que se muestra en las salas (Home > Perfil) -------------------------------------------

    private fun loggedInEmail(): String = (container.session.state.value as? SessionState.LoggedIn)?.email.orEmpty()

    private fun savedCustomName(): String? = accountId()?.let { container.profilePrefs.getName(it) }

    /** El nombre efectivo: el elegido si existe y, si no, el del correo. Solo se aplica al entrar a una sala; lo guardado vive en este teléfono. */
    var displayName by mutableStateOf(effectiveUsername(savedCustomName(), loggedInEmail()))
        private set

    /** Si hay un nombre elegido (para ofrecer volver al del correo). */
    var hasCustomName by mutableStateOf(savedCustomName() != null)
        private set

    private fun refreshDisplayName() {
        val custom = savedCustomName()
        displayName = effectiveUsername(custom, loggedInEmail())
        hasCustomName = custom != null
    }

    /** Guarda el nombre. Devuelve el mensaje de error para mostrar bajo el campo, o null si se guardó. */
    fun saveDisplayName(input: String): String? {
        displayNameError(input)?.let { return it }
        val account = accountId() ?: return MSG_NAME_NOT_SAVED
        container.profilePrefs.setName(account, input)
        refreshDisplayName()
        return null
    }

    /** Vuelve al nombre del correo. */
    fun resetDisplayName() {
        accountId()?.let { container.profilePrefs.clearName(it) }
        refreshDisplayName()
    }

    /**
     * 1) Muestra al instante lo guardado (conservando lo último que se supo de cada sala). 2) Comprueba cada sala con
     * `GET /api/room/<id>`, de a [RECENT_ROOMS_PARALLEL_CHECKS]: 404 la quita de la lista y del teléfono, 200 actualiza si
     * pide contraseña, cualquier otro resultado (sin red, 429, 5xx...) la conserva. No repite la comprobación si se hizo
     * hace menos de [RECENT_ROOMS_CHECK_INTERVAL_MS], salvo con [force] ("Actualizar"); tampoco si ya hay una en curso.
     */
    fun refreshRecentRooms(force: Boolean = false) {
        val account = accountId()
        if (account == null) {
            recentRooms = emptyList()
            return
        }
        val known = recentRooms.associateBy { it.roomId }
        recentRooms = container.recentRooms.load(account).map { it.toItem(known[it.roomId]) }
        if (recentRooms.isEmpty()) {
            recentRoomsOffline = false
            return
        }
        // Con una comprobación en curso no se lanza otra (ni siquiera con "Actualizar", que además se desactiva mientras tanto).
        if (recentCheckJob?.isActive == true) return
        if (!force && System.currentTimeMillis() - lastRecentCheckMillis < RECENT_ROOMS_CHECK_INTERVAL_MS) return
        val ids = recentRooms.map { it.roomId }
        recentCheckJob = viewModelScope.launch {
            isCheckingRecentRooms = true
            try {
                val gate = Semaphore(RECENT_ROOMS_PARALLEL_CHECKS)
                var failedByNetwork = false
                coroutineScope {
                    for (id in ids) {
                        launch {
                            val result = gate.withPermit { api.get(baseUrl, "/api/room/$id") }
                            val status = roomCheckStatus(result.code)
                            if (roomCheckFailedByNetwork(result.code)) failedByNetwork = true
                            val passwordProtected = parseJsonObject(result.body)?.takeIf { it.has("passwordProtected") }?.optBoolean("passwordProtected")
                            if (status == RoomCheckStatus.GONE) container.recentRooms.remove(account, id)
                            recentRooms = applyRoomCheck(recentRooms, id, status, passwordProtected)
                        }
                    }
                }
                recentRoomsOffline = failedByNetwork
                lastRecentCheckMillis = System.currentTimeMillis()
            } finally {
                isCheckingRecentRooms = false
            }
        }
    }

    /** "Quitar de la lista": solo la quita de este teléfono; si se vuelve a entrar a la sala, reaparece. */
    fun forgetRecentRoom(roomId: String) {
        val account = accountId() ?: return
        container.recentRooms.remove(account, roomId)
        recentRooms = recentRooms.filter { it.roomId != roomId }
    }

    // --- Subir un video del teléfono (Fase 4A) y crear la sala con él (Fase 4B) --------------------

    /** Identifica a esta subida ante el servicio en primer plano (puede haber otra en una sala). */
    private val uploadKey = Any()

    private val flow = UploadFlow(
        scope = viewModelScope,
        uploader = container.uploader,
        api = api,
        baseUrl = baseUrl,
        onSessionExpired = { container.session.refresh() },
        onLibraryChanged = { refresh() },
        onFinished = container.uploadNotifier::onUploadFinished,
        onStateChanged = { container.uploadKeepAlive.onState(uploadKey, it) },
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
            joinError = "Eso no parece un código ni un enlace de sala."
            return
        }
        joinError = null
        onJoin(roomId)
    }

    fun clearJoinError() {
        joinError = null
    }
}
