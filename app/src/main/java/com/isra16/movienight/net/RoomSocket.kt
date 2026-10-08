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
)

/**
 * Wrapper sobre socket.io-client-java para una sala: conecta, hace `join-room` y entrega los eventos
 * del server ya interpretados como [RoomEvent].
 *
 * Los callbacks llegan en hilos de la librería (no en el principal), siempre en orden: quien los
 * reciba tiene que pasar al hilo principal antes de tocar la UI.
 *
 * IMPORTANTE: el [client] tiene que ser el que lleva el CookieJar. Si no se le pasa a Socket.IO como
 * `callFactory` (long-polling) y `webSocketFactory` (upgrade), el handshake sale sin la cookie
 * `movienight.sid` y el server no reconoce la sesión (la sala quedaría sin host para su dueño).
 */
class RoomSocket(
    private val client: OkHttpClient,
    private val onEvent: (RoomEvent) -> Unit,
) {
    private var socket: Socket? = null

    val isConnected: Boolean get() = socket?.connected() == true

    fun connect(baseUrl: String, join: JoinParams) {
        disconnect()

        val options = IO.Options().apply {
            forceNew = true
            reconnection = true
            callFactory = client
            webSocketFactory = client
        }
        val s = try {
            IO.socket(URI.create(baseUrl), options)
        } catch (e: IllegalArgumentException) {
            onEvent(RoomEvent.ConnectError("URL inválida para Socket.IO: ${e.message}"))
            return
        }

        // `connect` también se dispara en cada reconexión automática, y el server no recuerda a qué
        // sala pertenecía el socket viejo, así que se reenvía join-room cada vez.
        s.on(Socket.EVENT_CONNECT) {
            onEvent(RoomEvent.Connected)
            emitJoin(s, join)
        }
        s.on(Socket.EVENT_DISCONNECT) { args ->
            onEvent(RoomEvent.Disconnected(args.firstOrNull()?.toString().orEmpty()))
        }
        s.on(Socket.EVENT_CONNECT_ERROR) { args ->
            onEvent(RoomEvent.ConnectError(args.firstOrNull()?.toString().orEmpty()))
        }
        for (name in HANDLED_SERVER_EVENTS) {
            s.on(name) { args -> parseServerEvent(name, args)?.let(onEvent) }
        }

        socket = s
        s.connect()
    }

    /** Cierra el socket sin emitir [RoomEvent.Disconnected] (es una salida pedida, no una caída). */
    fun disconnect() {
        socket?.let {
            it.off()
            it.disconnect()
        }
        socket = null
    }

    fun sendChat(text: String) {
        socket?.emit("chat-message", JSONObject().put("text", text))
    }

    fun sendTyping() {
        socket?.emit("typing")
    }

    /**
     * Emite un `sync` del host. No hace nada si el socket no está conectado: socket.io dejaría el
     * mensaje en cola y lo soltaría al reconectar, con una posición vieja y antes del `join-room`.
     * Quien llama decide si corresponde (ver `canEmitSync`).
     */
    fun sendSync(msg: SyncMessage) {
        val s = socket?.takeIf { it.connected() } ?: return
        s.emit("sync", toSyncPayload(msg))
    }

    /** `buffering-status` (booleano plano). Igual que [sendSync], no se encola si no hay conexión. */
    fun sendBuffering(buffering: Boolean) {
        val s = socket?.takeIf { it.connected() } ?: return
        s.emit("buffering-status", buffering)
    }

    private fun emitJoin(s: Socket, join: JoinParams) {
        val payload = JSONObject()
            .put("roomId", join.roomId)
            .put("username", join.username)
            .put("userId", join.userId)
        if (!join.password.isNullOrEmpty()) payload.put("password", join.password)
        s.emit("join-room", payload)
    }
}
