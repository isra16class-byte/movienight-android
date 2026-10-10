package com.isra16.movienight.room

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.isra16.movienight.MovieNightApp
import com.isra16.movienight.auth.SessionState
import com.isra16.movienight.auth.defaultUsername
import android.net.Uri
import com.isra16.movienight.home.LibraryState
import com.isra16.movienight.home.fetchLibrary
import com.isra16.movienight.net.ChatMessage
import com.isra16.movienight.net.FollowUpOutcome
import com.isra16.movienight.net.JoinParams
import com.isra16.movienight.net.LibraryItem
import com.isra16.movienight.net.ModerationAction
import com.isra16.movienight.net.ModerationCheck
import com.isra16.movienight.net.PendingModerations
import com.isra16.movienight.net.canCancel
import com.isra16.movienight.net.checkModeration
import com.isra16.movienight.net.moderationTimeoutMessage
import com.isra16.movienight.net.RoomVideoFailure
import com.isra16.movienight.net.UploadFlow
import com.isra16.movienight.net.UploadGoal
import com.isra16.movienight.net.UploadState
import com.isra16.movienight.net.changeVideoBody
import com.isra16.movienight.net.changeVideoPath
import com.isra16.movienight.net.interpretChangeVideo
import com.isra16.movienight.net.isBusy
import com.isra16.movienight.net.isSessionExpired
import com.isra16.movienight.net.toFollowUp
import com.isra16.movienight.net.RESTART_GIVE_UP_MS
import com.isra16.movienight.net.RestartState
import com.isra16.movienight.net.RoomEvent
import com.isra16.movienight.net.HEARTBEAT_INTERVAL_MS
import com.isra16.movienight.net.RoomSocket
import com.isra16.movienight.net.SyncMessage
import com.isra16.movienight.net.canEmitSync
import com.isra16.movienight.net.resolveSubtitleUrl
import com.isra16.movienight.net.roomErrorAfterRestart
import com.isra16.movienight.net.seekTargetMs
import com.isra16.movienight.net.Viewer
import com.isra16.movienight.net.apiErrorMessage
import com.isra16.movienight.net.isRoomPasswordError
import com.isra16.movienight.net.parseJsonObject
import com.isra16.movienight.net.resolveVideoUrl
import com.isra16.movienight.net.serverErrorMessage
import com.isra16.movienight.net.videoDisplayName
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** En qué punto de "entrar a la sala" está la pantalla. */
sealed interface RoomPhase {
    /** Preguntando al server si la sala existe y si pide contraseña (`GET /api/room/:id`). */
    data object Checking : RoomPhase

    /** La sala pide contraseña. [message] = por qué se vuelve a pedir (ej. "incorrecta"), si aplica. */
    data class AskPassword(val message: String?) : RoomPhase

    /** Socket abierto, esperando que el server acepte el `join-room`. */
    data object Connecting : RoomPhase
    data object InRoom : RoomPhase

    /** El host te sacó de la sala (`kicked`). No hay nada que reintentar: solo volver. */
    data object Kicked : RoomPhase

    /** No se pudo entrar. [canRetry] solo si tiene sentido volver a intentar (ej. fue un problema de red). */
    data class Failed(val message: String, val canRetry: Boolean) : RoomPhase
}

/**
 * Una sala: comprueba que exista, pide la contraseña si hace falta, abre el socket y mantiene el
 * estado del chat. Vive mientras la pantalla esté en la pila de navegación: al salir, [onCleared]
 * cierra el socket y el server anuncia que la persona salió.
 *
 * El video lo reproduce [player]. Fase 3: si la persona NO es host, el reproductor sigue los `sync` del
 * host. Si es host (confirmado por `host-status`, ver [isHost]) emite `sync`: play, pause y seek de sus
 * controles ([hostTogglePlay], [hostSeekTo], [onAppStopped]) y un heartbeat cada 4 s. Todo el que emite
 * pasa por [emitSync], que exige ser host y estar conectado. Todos (host o no) avisan `buffering-status`.
 */
class RoomViewModel(app: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(app) {

    private val container = (app as MovieNightApp).container
    private val baseUrl = container.baseUrl

    val roomId: String = savedStateHandle.get<String>(ROOM_ID_ARG).orEmpty()

    /** `userId` por dispositivo: sirve para distinguir "mis" mensajes y para que el server reconozca reconexiones. */
    val myUserId: String = container.userIds.userId
    private val username: String =
        defaultUsername((container.session.state.value as? SessionState.LoggedIn)?.email.orEmpty())

    // Los callbacks de Socket.IO llegan en hilos de la librería: pasan por un canal y se procesan en orden en el principal.
    private val events = Channel<RoomEvent>(Channel.UNLIMITED)
    private val roomSocket = RoomSocket(container.socketClient) { events.trySend(it) }

    /** El reproductor de la sala: vive acá para sobrevivir a rotar el teléfono y se libera en [onCleared]. */
    val player = RoomPlayer(app)

    private var passwordProtected = false
    private var lastTypingSentAt = 0L
    private var noticeJob: Job? = null
    private var typingJob: Job? = null

    var phase by mutableStateOf<RoomPhase>(RoomPhase.Checking)
        private set
    val messages = mutableStateListOf<ChatMessage>()
    var viewerCount by mutableIntStateOf(0)
        private set
    var viewers by mutableStateOf<List<Viewer>>(emptyList())
        private set
    var isHost by mutableStateOf(false)
        private set
    var isConnected by mutableStateOf(false)
        private set
    var isMuted by mutableStateOf(false)
        private set
    var videoName by mutableStateOf("")
        private set
    var typingUser by mutableStateOf<String?>(null)
        private set

    /** Aviso transitorio (límite de mensajes, reintentando conexión). El reinicio del server va aparte: [restart]. */
    var notice by mutableStateOf<String?>(null)
        private set

    /**
     * Reinicio del server (`server-restarting`): desde el aviso hasta que volvemos a unirnos a la sala. Tiene
     * prioridad sobre [notice] en la franja de aviso (ver `connectionBanner`) y le dice al reproductor que la vuelta
     * es de un reinicio (ver `planRejoin`). El aviso se deja de mostrar solo a los [RESTART_GIVE_UP_MS] si el server no vuelve.
     */
    var restart by mutableStateOf(RestartState())
        private set
    private var restartJob: Job? = null

    // --- Moderación del host (Fase 5) -------------------------------------------------------------

    /**
     * El `id` de socket propio: es el `id` con el que el server nos lista en `viewer-list`. Sirve para no ofrecernos
     * acciones sobre nosotros mismos. Se vuelve a leer en cada conexión y cada lista; `null` = no se sabe.
     */
    var mySocketId by mutableStateOf<String?>(null)
        private set

    /** Personas con un pedido de moderación en curso (se les deshabilita el menú hasta que se vea el resultado). */
    var busyViewerIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Último problema al moderar (se muestra dentro de la lista de conectados). */
    var moderationMessage by mutableStateOf<String?>(null)
        private set

    private val pendingModerations = PendingModerations()
    private val moderationJobs = mutableMapOf<String, Job>()
    private var moderationMessageJob: Job? = null

    // --- Cambiar el video de la sala (Fase 4B) ---------------------------------------------------

    /**
     * Secreto del host que manda `host-status` (solo importa en salas sin dueño; ver [RoomEvent.HostStatus]).
     * Solo en memoria. Se borra apenas se deja de ser host o se pierde la conexión.
     */
    private var hostToken: String? = null

    /** `true` mientras está abierto el cuadro "Cambiar video" (solo el host lo puede abrir). */
    var showChangeVideo by mutableStateOf(false)
        private set

    /** Biblioteca que muestra ese cuadro. */
    var changeLibrary by mutableStateOf<LibraryState>(LibraryState.Loading)
        private set

    /** `true` mientras se pide el cambio de un video de la biblioteca. */
    var isChangingVideo by mutableStateOf(false)
        private set
    var changeVideoError by mutableStateOf<String?>(null)
        private set

    /**
     * Subir un video nuevo desde el teléfono y, al terminar, ponerlo en la sala. Es una corrutina de este
     * ViewModel: si se sale de la sala mientras sube, se corta (igual que si se cierra la sesión en la pantalla
     * principal). Con la app en segundo plano sigue, con los mismos límites que la subida de la 4A.
     */
    private val flow = UploadFlow(
        scope = viewModelScope,
        uploader = container.uploader,
        api = container.api,
        baseUrl = baseUrl,
        onSessionExpired = { container.session.refresh() },
        onLibraryChanged = { loadChangeLibrary() },
        onFinished = container.uploadNotifier::onUploadFinished,
    )

    val upload: UploadState
        get() = flow.state

    init {
        viewModelScope.launch { for (event in events) handle(event) }
        viewModelScope.launch { checkRoom() }
        player.onBufferingChange = { roomSocket.sendBuffering(it) }
        viewModelScope.launch {
            // Red de seguridad del host por si se pierde algún evento (igual que `setInterval` en room.html).
            while (true) {
                delay(HEARTBEAT_INTERVAL_MS)
                player.heartbeat()?.let(::emitSync)
            }
        }
    }

    /** El único camino por el que sale un `sync`: solo si el server confirmó que somos host y hay conexión. */
    private fun emitSync(msg: SyncMessage) {
        if (!canEmitSync(isHost, isConnected)) return
        Log.d(SYNC_TAG, "emito ${msg.type.wire} t=${msg.timeMs}ms paused=${msg.paused}")
        roomSocket.sendSync(msg)
    }

    /**
     * `make-host`, `toggle-mute` o `kick-user` sobre [targetId] (el `id` de un integrante de [viewers]). Solo se emite
     * si el server confirmó que somos host y hay conexión, y si la persona sigue en la lista y no somos nosotros. El
     * server no contesta: el resultado llega por `viewer-list` / `host-status`; si no llega en [MODERATION_CONFIRM_MS],
     * se avisa. Mientras hay un pedido en curso sobre alguien no se acepta otro sobre esa persona (`toggle-mute` alterna).
     */
    fun moderate(action: ModerationAction, targetId: String) {
        val target = when (val check = checkModeration(targetId, viewers, mySocketId, isHost, isConnected)) {
            is ModerationCheck.Rejected -> {
                showModerationMessage(check.message)
                return
            }
            is ModerationCheck.Allowed -> check.target
        }
        if (!pendingModerations.begin(action, target)) return
        if (!roomSocket.sendModeration(action, target.id)) {
            pendingModerations.expire(target.id)
            showModerationMessage("No se pudo enviar: sin conexión con la sala.")
            return
        }
        moderationMessageJob?.cancel()
        moderationMessage = null
        busyViewerIds = pendingModerations.ids
        moderationJobs[target.id] = viewModelScope.launch {
            delay(MODERATION_CONFIRM_MS)
            moderationJobs.remove(target.id)
            val unresolved = pendingModerations.expire(target.id)
            busyViewerIds = pendingModerations.ids
            if (unresolved != null) {
                showModerationMessage(moderationTimeoutMessage(unresolved.first, unresolved.second.username))
            }
        }
    }

    /** Borra el aviso de moderación (al abrir la lista de conectados). */
    fun clearModerationMessage() {
        moderationMessageJob?.cancel()
        moderationMessage = null
    }

    private fun showModerationMessage(text: String) {
        moderationMessageJob?.cancel()
        moderationMessage = text
        moderationMessageJob = viewModelScope.launch {
            delay(MODERATION_MESSAGE_MS)
            moderationMessage = null
        }
    }

    /** Un `viewer-list` nuevo: los pedidos que ya se reflejan dejan de estar en curso. */
    private fun resolveModerations(list: List<Viewer>) {
        val done = pendingModerations.resolve(list)
        if (done.isEmpty()) return
        done.forEach { moderationJobs.remove(it)?.cancel() }
        busyViewerIds = pendingModerations.ids
    }

    /** Se perdió el rol, la conexión o la sala: lo que estaba en curso ya no tiene a quién avisarle. */
    private fun clearModerations() {
        moderationJobs.values.forEach { it.cancel() }
        moderationJobs.clear()
        pendingModerations.clear()
        busyViewerIds = emptySet()
        moderationMessageJob?.cancel()
        moderationMessage = null
    }

    /** Play/pausa del botón (solo host): lo aplica al video y lo emite. */
    fun hostTogglePlay() {
        if (!isHost) return
        player.togglePlay()?.let(::emitSync)
    }

    /** Salto de la barra (solo host), con [fraction] de 0 a 1: salta el video y emite `seek`. */
    fun hostSeekTo(fraction: Float) {
        if (!isHost) return
        player.seekTo(seekTargetMs(fraction, player.durationMs))?.let(::emitSync)
    }

    /** La app dejó de verse: el video se pausa, y si somos host la sala también (como pausar en el navegador). */
    fun onAppStopped() {
        player.pause()?.let { if (isHost) emitSync(it) }
    }

    fun retry() {
        phase = RoomPhase.Checking
        viewModelScope.launch { checkRoom() }
    }

    // --- Cambiar el video de la sala (solo host, Fase 4B) -----------------------------------------

    /** Abre el cuadro "Cambiar video" y carga la biblioteca. */
    fun openChangeVideo() {
        if (!isHost) return
        changeVideoError = null
        // Un "listo" de una subida anterior ya no dice nada; un fallo sí se deja a la vista, para reintentar.
        if (flow.state is UploadState.Done) flow.dismiss()
        showChangeVideo = true
        loadChangeLibrary()
    }

    /** Cierra el cuadro. Una subida en curso sigue (se ve en el aviso de arriba de la sala). */
    fun hideChangeVideo() {
        showChangeVideo = false
        changeVideoError = null
    }

    fun loadChangeLibrary() {
        changeLibrary = LibraryState.Loading
        viewModelScope.launch {
            changeLibrary = fetchLibrary(container.api, baseUrl) { container.session.refresh() }
        }
    }

    /** Pone [item], un video que ya está en la biblioteca, como video de la sala. */
    fun changeVideoTo(item: LibraryItem) {
        if (!isHost || isChangingVideo || flow.state.isBusy()) return
        isChangingVideo = true
        changeVideoError = null
        viewModelScope.launch {
            try {
                when (val outcome = postChangeVideo(item.filename)) {
                    is FollowUpOutcome.Done -> showChangeVideo = false
                    is FollowUpOutcome.Failed -> {
                        changeVideoError = outcome.failure.message
                        // El server borra de la biblioteca un video que no pasó la validación de contenido.
                        if (outcome.failure.libraryChanged) loadChangeLibrary()
                    }
                }
            } finally {
                isChangingVideo = false
            }
        }
    }

    /** Resultado del selector del sistema para "Subir uno nuevo"; [uri] es `null` si la persona lo cerró sin elegir. */
    fun onVideoPicked(uri: Uri?) {
        if (uri == null || !isHost || isChangingVideo || flow.state.isBusy()) return
        changeVideoError = null
        flow.start(uri, UploadGoal.CHANGE_ROOM_VIDEO) { key ->
            postChangeVideo(key).also { if (it is FollowUpOutcome.Done) showChangeVideo = false }
        }
    }

    fun retryUpload() = flow.retry()

    fun cancelUpload() = flow.cancel()

    fun dismissUpload() = flow.dismiss()

    /**
     * `POST /room/:id/change-video-from-upload`. El server autoriza por el DUEÑO de la sala, no por quién es
     * host (ver `net/RoomVideoLogic.kt`): con una sala con dueño que no somos nosotros contesta 403 aunque
     * ahora seamos host. Los demás se enteran por `video-changed`, que llega a toda la sala, a nosotros también.
     */
    private suspend fun postChangeVideo(key: String): FollowUpOutcome {
        // La subida pudo tardar: si mientras tanto el host pasó a ser otro, no se le pisa el video.
        if (!isHost) {
            return FollowUpOutcome.Failed(
                RoomVideoFailure("Ya no sos el host de la sala, así que el video no se cambió.", canRetry = false),
            )
        }
        val result = container.api.postJson(baseUrl, changeVideoPath(roomId), changeVideoBody(key, hostToken))
        if (isSessionExpired(result.code)) container.session.refresh()
        return interpretChangeVideo(result.code, result.body).toFollowUp()
    }

    fun submitPassword(password: String) {
        connect(password)
    }

    /** `true` si el mensaje se mandó (la pantalla vacía el campo); `false` si no se puede mandar ahora. */
    fun sendChat(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || !isConnected || isMuted) return false
        roomSocket.sendChat(trimmed.take(MAX_MESSAGE_CHARS))
        return true
    }

    /** El server retransmite cada `typing`; se limita a uno cada 2 s para no inundar la sala. */
    fun onTyping() {
        val now = System.currentTimeMillis()
        if (!isConnected || now - lastTypingSentAt < TYPING_THROTTLE_MS) return
        lastTypingSentAt = now
        roomSocket.sendTyping()
    }

    private suspend fun checkRoom() {
        val result = container.api.get(baseUrl, "/api/room/$roomId")
        when {
            result.code == 200 -> {
                passwordProtected = parseJsonObject(result.body)?.optBoolean("passwordProtected", false) ?: false
                // Recién creada por esta persona: la contraseña ya se la sabemos, no se la volvemos a pedir.
                val known = if (passwordProtected) container.roomPasswords.take(roomId) else null
                if (passwordProtected && known == null) phase = RoomPhase.AskPassword(null) else connect(known)
            }
            result.code == 404 -> phase = RoomPhase.Failed("Esa sala no existe o ya se cerró.", canRetry = false)
            else -> phase = RoomPhase.Failed(
                apiErrorMessage(result.code, serverErrorMessage(result.body)),
                canRetry = true,
            )
        }
    }

    private fun connect(password: String?) {
        phase = RoomPhase.Connecting
        isConnected = false
        roomSocket.connect(
            baseUrl,
            JoinParams(roomId = roomId, username = username, userId = myUserId, password = password),
        )
    }

    private fun handle(event: RoomEvent) {
        when (event) {
            RoomEvent.Connected -> {
                isConnected = true
                mySocketId = roomSocket.socketId
                notice = null
            }
            is RoomEvent.Disconnected -> {
                isConnected = false
                restart = restart.onDisconnected()
                // El rol vale por socket: al reconectar el server vuelve a mandar `host-status`. Hasta entonces no hay rol confirmado.
                isHost = false
                hostToken = null
                showChangeVideo = false
                player.setHostRole(false)
                // El silencio también vale por conexión: el server lo limpia a los 15 s de haberte ido y, al volver,
                // solo avisa si SIGUES silenciado (nunca manda `muted:false`). Si no se borra acá, un silencio ya
                // levantado seguiría bloqueando el chat en la app.
                isMuted = false
                mySocketId = null
                clearModerations()
            }
            is RoomEvent.ConnectError -> {
                isConnected = false
                notice = "No se pudo conectar con el servidor. Reintentando…"
            }
            is RoomEvent.RoomError -> {
                // Si veníamos de un reinicio, el error se explica con eso (típico: la sala no se recuperó).
                val afterRestart = restart.isRejoining
                clearRestart()
                roomSocket.disconnect()
                player.clear()
                isConnected = false
                phase = if (passwordProtected && isRoomPasswordError(event.message)) {
                    RoomPhase.AskPassword(event.message)
                } else {
                    RoomPhase.Failed(if (afterRestart) roomErrorAfterRestart(event.message) else event.message, canRetry = false)
                }
            }
            is RoomEvent.ChatHistory -> {
                messages.clear()
                messages.addAll(event.messages.takeLast(MAX_MESSAGES))
                markJoined()
            }
            is RoomEvent.Chat -> {
                messages.add(event.message)
                if (messages.size > MAX_MESSAGES) messages.removeAt(0)
                // Quien acaba de escribir ya no está "escribiendo".
                if (typingUser != null && typingUser == event.message.user) typingUser = null
            }
            is RoomEvent.HostStatus -> {
                isHost = event.isHost
                hostToken = if (event.isHost) event.hostToken else null
                // Si se perdió el rol, no se queda abierto un cuadro que solo el host puede usar.
                if (!event.isHost) {
                    showChangeVideo = false
                    // Se cedió el host (o se perdió): lo que se pidió ya no se puede seguir ni medir.
                    clearModerations()
                }
                // Quien es host ya no sigue a nadie (se le quita la velocidad y cualquier corrección pendiente).
                player.setHostRole(event.isHost)
                // `host-status` llega en cada join: el server empezó de cero con este socket, así que si ya estábamos en buffering se vuelve a avisar.
                player.resendBufferingState()
                markJoined()
            }
            is RoomEvent.RoomData -> {
                videoName = videoDisplayName(event.videoFile)
                // El subtítulo va primero, para que el video cargue ya con él (y, al reconectar, para enterarse
                // de uno que cambió mientras no había conexión).
                player.setSubtitle(resolveSubtitleUrl(baseUrl, event.subtitleFile))
                // También llega al reconectar tras un corte de red: si el video ya es ese no se recarga
                // (y entonces el reproductor se alinea con la posición de la sala, ver RoomPlayer.alignToRoom).
                // `room-data` es lo último que manda el server al aceptar el `join-room`: acá termina la vuelta de un reinicio.
                val (nextRestart, afterRestart) = restart.onJoined()
                if (afterRestart) restartJob?.cancel()
                restart = nextRestart
                player.load(resolveVideoUrl(baseUrl, event.videoFile), force = false, start = event.position, afterRestart = afterRestart)
                markJoined()
            }
            is RoomEvent.VideoChanged -> {
                // El host cambió la cinta: se recarga siempre y arranca en pausa desde el principio.
                videoName = videoDisplayName(event.videoFile)
                player.load(resolveVideoUrl(baseUrl, event.videoFile), force = true)
            }
            // El host subió un subtítulo nuevo: se aplica a todos (también al host, que lo ve igual).
            is RoomEvent.SubtitleChanged -> player.setSubtitle(resolveSubtitleUrl(baseUrl, event.subtitleFile))
            // El server solo retransmite `sync` a quien no es host; igual se descarta si somos host (p. ej. durante
            // un traspaso), para no pelear con los controles locales. Aplicarlo no emite nada (ningún listener del
            // ExoPlayer emite `sync`): un seek que viene del server no puede rebotar de vuelta.
            is RoomEvent.Sync -> if (!isHost) player.applySync(event.message)
            is RoomEvent.MuteStatus -> isMuted = event.muted
            is RoomEvent.ViewerCount -> viewerCount = event.count
            is RoomEvent.ViewerList -> {
                viewers = event.viewers
                mySocketId = roomSocket.socketId
                resolveModerations(event.viewers)
            }
            is RoomEvent.Typing -> showTyping(event.username)
            is RoomEvent.ChatRateLimited -> showNotice(event.message, clearAfterMs = NOTICE_MS)
            RoomEvent.Kicked -> {
                clearRestart()
                roomSocket.disconnect()
                player.clear()
                isConnected = false
                // Ya no estamos en la sala: nada del rol ni de la lista de antes tiene que quedar a la vista.
                isHost = false
                hostToken = null
                showChangeVideo = false
                player.setHostRole(false)
                viewers = emptyList()
                mySocketId = null
                clearModerations()
                if (flow.state.canCancel()) flow.cancel()
                phase = RoomPhase.Kicked
            }
            RoomEvent.ServerRestarting -> {
                restart = restart.onAnnounced()
                // Si el server no vuelve, a los RESTART_GIVE_UP_MS se deja de mostrar el aviso y queda el genérico.
                restartJob?.cancel()
                restartJob = viewModelScope.launch {
                    delay(RESTART_GIVE_UP_MS)
                    restart = restart.giveUpBanner()
                }
            }
        }
    }

    private fun clearRestart() {
        restartJob?.cancel()
        restart = RestartState()
    }

    /** El server acepta el `join-room` mandando historial, rol y datos de la sala: la primera de esas señales abre la sala. */
    private fun markJoined() {
        if (phase is RoomPhase.Connecting) phase = RoomPhase.InRoom
    }

    private fun showNotice(text: String, clearAfterMs: Long?) {
        noticeJob?.cancel()
        notice = text
        if (clearAfterMs != null) {
            noticeJob = viewModelScope.launch {
                delay(clearAfterMs)
                notice = null
            }
        }
    }

    private fun showTyping(name: String) {
        typingJob?.cancel()
        typingUser = name
        typingJob = viewModelScope.launch {
            delay(TYPING_VISIBLE_MS)
            typingUser = null
        }
    }

    override fun onCleared() {
        flow.release()
        player.release()
        roomSocket.disconnect()
        events.close()
        super.onCleared()
    }

    companion object {
        /** Nombre del argumento de la ruta de navegación (también lo lee el `SavedStateHandle`). */
        const val ROOM_ID_ARG = "roomId"

        /** Tag de Logcat para seguir lo que se emite y el buffering (`adb logcat -s MovieNightSync`). */
        private const val SYNC_TAG = "MovieNightSync"

        private const val MAX_MESSAGES = 500
        private const val MAX_MESSAGE_CHARS = 500 // el server recorta a 500
        private const val TYPING_THROTTLE_MS = 2_000L
        private const val TYPING_VISIBLE_MS = 3_000L
        private const val NOTICE_MS = 4_000L

        /** Cuánto se espera ver reflejada una acción de moderación en `viewer-list` antes de avisar que no se confirmó. */
        private const val MODERATION_CONFIRM_MS = 5_000L
        private const val MODERATION_MESSAGE_MS = 6_000L
    }
}
