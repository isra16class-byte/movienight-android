package com.isra16.movienight.net

import io.socket.client.IO
import io.socket.client.Socket
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URI

/** Datos que el server espera en `join-room` (ver docs/API-CONTRATO.md). */
data class JoinParams(
    val roomId: String,
    val username: String,
    val userId: String,
    val password: String? = null,
    val hostToken: String? = null,
)

/**
 * Wrapper mínimo sobre socket.io-client-java para el spike de la Fase 1.
 *
 * Los callbacks llegan en hilos de la librería (no en el principal): quien los reciba tiene que
 * pasar al hilo principal antes de tocar la UI.
 *
 * IMPORTANTE: el [client] tiene que ser el que lleva el CookieJar. Si no se le pasa a Socket.IO como
 * `callFactory` (long-polling) y `webSocketFactory` (upgrade), el handshake sale sin la cookie
 * `movienight.sid` y el server no reconoce la sesión.
 */
class RoomSocket(
    private val client: OkHttpClient,
    private val onLog: (String) -> Unit,
    private val onConnectionChanged: (connected: Boolean) -> Unit,
) {
    private var socket: Socket? = null
    private var heartbeatLogged = false

    val isConnected: Boolean get() = socket?.connected() == true

    fun connect(baseUrl: String, join: JoinParams) {
        disconnect()
        heartbeatLogged = false

        val options = IO.Options().apply {
            forceNew = true
            reconnection = true
            callFactory = client
            webSocketFactory = client
        }
        val s = try {
            IO.socket(URI.create(baseUrl), options)
        } catch (e: IllegalArgumentException) {
            onLog("✗ URL inválida para Socket.IO: ${e.message}")
            return
        }

        // Conexión / desconexión. `connect` también se dispara en cada reconexión automática, y el
        // server no recuerda a qué sala pertenecía el socket viejo, así que se reenvía join-room.
        s.on(Socket.EVENT_CONNECT) {
            onLog("● connect (id ${s.id()})")
            onConnectionChanged(true)
            emitJoin(s, join)
        }
        s.on(Socket.EVENT_DISCONNECT) { args ->
            onLog("○ disconnect ${format(args)}")
            onConnectionChanged(false)
        }
        s.on(Socket.EVENT_CONNECT_ERROR) { args ->
            onLog("✗ connect_error ${format(args)}")
        }

        // Eventos servidor → cliente del contrato.
        for (event in SERVER_EVENTS) {
            s.on(event) { args ->
                if (event == "sync" && isHeartbeat(args)) {
                    // Llega cada 4s: solo se muestra el primero para no tapar el resto del log.
                    if (!heartbeatLogged) {
                        heartbeatLogged = true
                        onLog("← sync (heartbeat) ${format(args)}  [los siguientes no se muestran]")
                    }
                } else {
                    onLog("← $event ${format(args)}")
                }
            }
        }

        socket = s
        onLog("→ conectando a $baseUrl …")
        s.connect()
    }

    fun disconnect() {
        socket?.let {
            it.off()
            it.disconnect()
        }
        val wasConnected = socket != null
        socket = null
        if (wasConnected) onConnectionChanged(false)
    }

    fun sendChat(text: String) {
        socket?.emit("chat-message", JSONObject().put("text", text))
    }

    fun sendTyping() {
        socket?.emit("typing")
    }

    fun sendReaction(emoji: String) {
        socket?.emit("reaction", emoji)
    }

    private fun emitJoin(s: Socket, join: JoinParams) {
        val payload = JSONObject()
            .put("roomId", join.roomId)
            .put("username", join.username)
            .put("userId", join.userId)
        if (!join.password.isNullOrEmpty()) payload.put("password", join.password)
        if (!join.hostToken.isNullOrEmpty()) payload.put("hostToken", join.hostToken)
        // No se loguea el payload completo: puede llevar la contraseña de la sala.
        onLog("→ join-room (sala ${join.roomId}, usuario ${join.username})")
        s.emit("join-room", payload)
    }

    private fun isHeartbeat(args: Array<out Any?>): Boolean {
        val first = args.firstOrNull()
        return first is JSONObject && first.optString("type") == "heartbeat"
    }

    private fun format(args: Array<out Any?>): String =
        args.joinToString(" ") { it.toString() }.take(MAX_LOG_CHARS)

    private companion object {
        const val MAX_LOG_CHARS = 400

        val SERVER_EVENTS = listOf(
            "room-error",
            "chat-history",
            "chat-message",
            "chat-rate-limited",
            "host-status",
            "room-data",
            "mute-status",
            "viewer-count",
            "viewer-list",
            "sync",
            "video-changed",
            "subtitle-changed",
            "typing",
            "reaction",
            "kicked",
            "server-restarting",
        )
    }
}
