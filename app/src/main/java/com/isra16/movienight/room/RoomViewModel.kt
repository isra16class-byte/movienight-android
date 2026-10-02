package com.isra16.movienight.room

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.isra16.movienight.MovieNightApp
import com.isra16.movienight.auth.SessionState
import com.isra16.movienight.auth.defaultUsername
import com.isra16.movienight.net.ChatMessage
import com.isra16.movienight.net.JoinParams
import com.isra16.movienight.net.RoomEvent
import com.isra16.movienight.net.RoomSocket
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

    /** No se pudo entrar. [canRetry] solo si tiene sentido volver a intentar (ej. fue un problema de red). */
    data class Failed(val message: String, val canRetry: Boolean) : RoomPhase
}

/**
 * Una sala: comprueba que exista, pide la contraseña si hace falta, abre el socket y mantiene el
 * estado del chat. Vive mientras la pantalla esté en la pila de navegación: al salir, [onCleared]
 * cierra el socket y el server anuncia que la persona salió.
 *
 * El video lo reproduce [player] (Fase 3A: como espectadora, sin sincronizar con el host todavía).
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

    /** Aviso transitorio (límite de mensajes, servidor reiniciando, reintentando conexión). */
    var notice by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch { for (event in events) handle(event) }
        viewModelScope.launch { checkRoom() }
    }

    fun retry() {
        phase = RoomPhase.Checking
        viewModelScope.launch { checkRoom() }
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
                notice = null
            }
            is RoomEvent.Disconnected -> isConnected = false
            is RoomEvent.ConnectError -> {
                isConnected = false
                notice = "No se pudo conectar con el servidor. Reintentando…"
            }
            is RoomEvent.RoomError -> {
                roomSocket.disconnect()
                player.clear()
                isConnected = false
                phase = if (passwordProtected && isRoomPasswordError(event.message)) {
                    RoomPhase.AskPassword(event.message)
                } else {
                    RoomPhase.Failed(event.message, canRetry = false)
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
                markJoined()
            }
            is RoomEvent.RoomData -> {
                videoName = videoDisplayName(event.videoFile)
                // También llega al reconectar tras un corte de red: si el video ya es ese no se recarga.
                player.load(resolveVideoUrl(baseUrl, event.videoFile), force = false)
                markJoined()
            }
            is RoomEvent.VideoChanged -> {
                // El host cambió la cinta: se recarga siempre y arranca en pausa desde el principio.
                videoName = videoDisplayName(event.videoFile)
                player.load(resolveVideoUrl(baseUrl, event.videoFile), force = true)
            }
            is RoomEvent.MuteStatus -> isMuted = event.muted
            is RoomEvent.ViewerCount -> viewerCount = event.count
            is RoomEvent.ViewerList -> viewers = event.viewers
            is RoomEvent.Typing -> showTyping(event.username)
            is RoomEvent.ChatRateLimited -> showNotice(event.message, clearAfterMs = NOTICE_MS)
            RoomEvent.Kicked -> {
                roomSocket.disconnect()
                player.clear()
                isConnected = false
                phase = RoomPhase.Failed("El host te sacó de la sala.", canRetry = false)
            }
            RoomEvent.ServerRestarting ->
                showNotice("El servidor se está reiniciando. Te reconectamos en un momento…", clearAfterMs = null)
        }
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
        player.release()
        roomSocket.disconnect()
        events.close()
        super.onCleared()
    }

    companion object {
        /** Nombre del argumento de la ruta de navegación (también lo lee el `SavedStateHandle`). */
        const val ROOM_ID_ARG = "roomId"

        private const val MAX_MESSAGES = 500
        private const val MAX_MESSAGE_CHARS = 500 // el server recorta a 500
        private const val TYPING_THROTTLE_MS = 2_000L
        private const val TYPING_VISIBLE_MS = 3_000L
        private const val NOTICE_MS = 4_000L
    }
}
