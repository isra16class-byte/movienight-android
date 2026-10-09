package com.isra16.movienight.net

import org.json.JSONArray
import org.json.JSONObject

/** Cita de otro mensaje (el server la guarda "congelada" al momento de responder). */
data class ReplyTo(val user: String, val text: String, val isHost: Boolean)

/**
 * Un mensaje del chat. [system] = avisos del server ("X se unió a la sala"): solo traen [text].
 * [userId] es el id persistente de quien escribió (no el nombre: dos personas pueden elegir el mismo).
 */
data class ChatMessage(
    val system: Boolean,
    val user: String,
    val text: String,
    val isHost: Boolean,
    val userId: String?,
    val replyTo: ReplyTo?,
)

/** Un integrante conectado a la sala (`viewer-list`). */
data class Viewer(
    val id: String,
    val username: String,
    val isHost: Boolean,
    val muted: Boolean,
    val buffering: Boolean,
)

/** Eventos servidor -> cliente de la sala que la app usa (ver docs/API-CONTRATO.md). */
sealed interface RoomEvent {
    /** Socket conectado (también tras cada reconexión automática). */
    data object Connected : RoomEvent
    data class Disconnected(val reason: String) : RoomEvent
    data class ConnectError(val message: String) : RoomEvent

    /** `room-error`: el `join-room` fue rechazado (contraseña incorrecta, sala inexistente...). */
    data class RoomError(val message: String) : RoomEvent
    data class ChatHistory(val messages: List<ChatMessage>) : RoomEvent
    data class Chat(val message: ChatMessage) : RoomEvent
    data class ChatRateLimited(val message: String) : RoomEvent
    /**
     * `host-status`. [hostToken] solo viene cuando [isHost] es `true`. Hace falta para cambiar el video en
     * una sala SIN dueño (anónima, creada en la web sin cuenta); en una sala con dueño el server lo ignora.
     * Es un secreto de la sala: se guarda solo en memoria y nunca se escribe en el log.
     */
    data class HostStatus(val isHost: Boolean, val hostToken: String? = null) : RoomEvent
    /** `room-data`: la cinta y, si el server la mandó, dónde va el video ([position], en el momento del join). */
    data class RoomData(
        val videoFile: String?,
        val position: RoomPosition? = null,
        val subtitleFile: String? = null,
    ) : RoomEvent
    data class VideoChanged(val videoFile: String?) : RoomEvent

    /** `subtitle-changed`: el host subió un subtítulo nuevo (el server siempre lo convierte a `.vtt`). */
    data class SubtitleChanged(val subtitleFile: String) : RoomEvent

    /** `sync` del host (play, pause, seek o heartbeat). Solo llega a quien NO es host. */
    data class Sync(val message: SyncMessage) : RoomEvent
    data class MuteStatus(val muted: Boolean) : RoomEvent
    data class ViewerCount(val count: Int) : RoomEvent
    data class ViewerList(val viewers: List<Viewer>) : RoomEvent
    data class Typing(val username: String) : RoomEvent
    data object Kicked : RoomEvent
    data object ServerRestarting : RoomEvent
}

/** Eventos del server que [parseServerEvent] sabe interpretar (los demás —`reaction`...— llegan después). */
val HANDLED_SERVER_EVENTS: List<String> = listOf(
    "room-error", "chat-history", "chat-message", "chat-rate-limited", "host-status", "room-data",
    "video-changed", "subtitle-changed", "sync", "mute-status", "viewer-count", "viewer-list", "typing", "kicked", "server-restarting",
)

/**
 * Traduce un evento de Socket.IO (nombre + argumentos tal como los entrega la librería) a un
 * [RoomEvent]; `null` si no es de los que se usan o llegó con una forma inesperada.
 */
fun parseServerEvent(name: String, args: Array<out Any?>): RoomEvent? {
    val first = args.firstOrNull()
    return when (name) {
        "room-error" -> (first as? String)?.let { RoomEvent.RoomError(it) }
        "chat-history" -> (first as? JSONArray)?.let { RoomEvent.ChatHistory(parseChatHistory(it)) }
        "chat-message" -> (first as? JSONObject)?.let { RoomEvent.Chat(parseChatMessage(it)) }
        "chat-rate-limited" -> RoomEvent.ChatRateLimited(
            (first as? JSONObject)?.optString("message", "")?.takeIf { it.isNotBlank() }
                ?: "Estás mandando mensajes muy rápido, esperá un toque.",
        )
        "host-status" -> (first as? JSONObject)?.let {
            val isHost = it.optBoolean("isHost", false)
            RoomEvent.HostStatus(isHost, if (isHost) it.optStringOrNull("hostToken") else null)
        }
        "room-data" -> (first as? JSONObject)?.let {
            RoomEvent.RoomData(
                it.optStringOrNull("videoFile"),
                parseRoomPosition(it.optJSONObject("position")),
                it.optStringOrNull("subtitleFile"),
            )
        }
        "video-changed" -> (first as? JSONObject)?.let { RoomEvent.VideoChanged(it.optStringOrNull("videoFile")) }
        "subtitle-changed" -> (first as? JSONObject)?.optStringOrNull("subtitleFile")?.let { RoomEvent.SubtitleChanged(it) }
        "sync" -> (first as? JSONObject)?.let(::parseSyncMessage)?.let { RoomEvent.Sync(it) }
        "mute-status" -> (first as? JSONObject)?.let { RoomEvent.MuteStatus(it.optBoolean("muted", false)) }
        "viewer-count" -> (first as? Number)?.let { RoomEvent.ViewerCount(it.toInt()) }
        "viewer-list" -> (first as? JSONArray)?.let { RoomEvent.ViewerList(parseViewers(it)) }
        "typing" -> (first as? JSONObject)?.optStringOrNull("username")?.let { RoomEvent.Typing(it) }
        "kicked" -> RoomEvent.Kicked
        "server-restarting" -> RoomEvent.ServerRestarting
        else -> null
    }
}

fun parseChatMessage(obj: JSONObject): ChatMessage {
    val reply = obj.optJSONObject("replyTo")?.let {
        ReplyTo(
            user = it.optString("user", ""),
            text = it.optString("text", ""),
            isHost = it.optBoolean("isHost", false),
        )
    }
    return ChatMessage(
        system = obj.optBoolean("system", false),
        user = obj.optString("user", ""),
        text = obj.optString("text", ""),
        isHost = obj.optBoolean("isHost", false),
        userId = obj.optStringOrNull("userId"),
        replyTo = reply,
    )
}

fun parseChatHistory(array: JSONArray): List<ChatMessage> =
    (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let(::parseChatMessage) }

fun parseViewers(array: JSONArray): List<Viewer> =
    (0 until array.length()).mapNotNull { i ->
        array.optJSONObject(i)?.let {
            Viewer(
                id = it.optString("id", ""),
                username = it.optString("username", "Anónimo"),
                isHost = it.optBoolean("isHost", false),
                muted = it.optBoolean("muted", false),
                buffering = it.optBoolean("buffering", false),
            )
        }
    }

/** `optString` que devuelve `null` si la clave falta o vale `null` (org.json de Android devuelve "null"). */
private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key, "").takeIf { it.isNotEmpty() }
