package com.isra16.movienight

import com.isra16.movienight.net.RoomEvent
import com.isra16.movienight.net.parseServerEvent
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomEventsTest {

    private fun parse(name: String, vararg args: Any?) = parseServerEvent(name, arrayOf(*args))

    @Test
    fun mensajeDeChatDeUnaPersona() {
        val msg = JSONObject(
            """{"system":false,"user":"isra","text":"hola","replyTo":null,"isHost":true,"userId":"u-1"}""",
        )
        val event = parse("chat-message", msg) as RoomEvent.Chat
        assertFalse(event.message.system)
        assertEquals("isra", event.message.user)
        assertEquals("hola", event.message.text)
        assertTrue(event.message.isHost)
        assertEquals("u-1", event.message.userId)
        assertNull(event.message.replyTo)
    }

    @Test
    fun mensajeDeSistemaNoTrae_usuarioNiUserId() {
        val msg = JSONObject("""{"system":true,"text":"ana se unió a la sala 🎬"}""")
        val event = parse("chat-message", msg) as RoomEvent.Chat
        assertTrue(event.message.system)
        assertEquals("ana se unió a la sala 🎬", event.message.text)
        assertNull(event.message.userId)
    }

    @Test
    fun mensajeConCita() {
        val msg = JSONObject(
            """{"system":false,"user":"ana","text":"sí!","replyTo":{"user":"isra","text":"¿la vemos?","isHost":true},"isHost":false,"userId":"u-2"}""",
        )
        val reply = (parse("chat-message", msg) as RoomEvent.Chat).message.replyTo!!
        assertEquals("isra", reply.user)
        assertEquals("¿la vemos?", reply.text)
        assertTrue(reply.isHost)
    }

    @Test
    fun historialDeChatEnOrden() {
        val history = JSONArray(
            """[{"system":true,"text":"🎬 Cinta cargada: x.mp4"},{"system":false,"user":"isra","text":"hola","userId":"u-1"}]""",
        )
        val event = parse("chat-history", history) as RoomEvent.ChatHistory
        assertEquals(2, event.messages.size)
        assertTrue(event.messages[0].system)
        assertEquals("hola", event.messages[1].text)
    }

    @Test
    fun listaDeConectados() {
        val list = JSONArray(
            """[{"id":"s1","username":"isra","isHost":true,"muted":false,"buffering":false},{"id":"s2","username":"ana","isHost":false,"muted":true,"buffering":true}]""",
        )
        val viewers = (parse("viewer-list", list) as RoomEvent.ViewerList).viewers
        assertEquals(listOf("isra", "ana"), viewers.map { it.username })
        assertTrue(viewers[0].isHost)
        assertTrue(viewers[1].muted)
        assertTrue(viewers[1].buffering)
    }

    @Test
    fun contadorDeConectados() {
        assertEquals(RoomEvent.ViewerCount(3), parse("viewer-count", 3))
    }

    @Test
    fun estadoDeHostYDeSilencio() {
        assertEquals(RoomEvent.HostStatus(true), parse("host-status", JSONObject("""{"isHost":true,"hostToken":null}""")))
        assertEquals(RoomEvent.HostStatus(false), parse("host-status", JSONObject("""{"isHost":false,"hostToken":null}""")))
        assertEquals(RoomEvent.MuteStatus(true), parse("mute-status", JSONObject("""{"muted":true}""")))
    }

    @Test
    fun datosDeLaSala() {
        val data = JSONObject("""{"videoFile":"/uploads/1__x.mp4","subtitleFile":null,"position":{"time":0,"paused":true}}""")
        assertEquals(RoomEvent.RoomData("/uploads/1__x.mp4"), parse("room-data", data))
        assertEquals(RoomEvent.RoomData(null), parse("room-data", JSONObject("""{"videoFile":null}""")))
        assertEquals(RoomEvent.VideoChanged("/uploads/2__y.mp4"), parse("video-changed", JSONObject("""{"videoFile":"/uploads/2__y.mp4"}""")))
    }

    @Test
    fun errorDeSalaYEventosSinDatos() {
        assertEquals(RoomEvent.RoomError("La sala no existe."), parse("room-error", "La sala no existe."))
        assertEquals(RoomEvent.Kicked, parse("kicked"))
        assertEquals(RoomEvent.ServerRestarting, parse("server-restarting"))
    }

    @Test
    fun escribiendoYLimiteDeMensajes() {
        assertEquals(RoomEvent.Typing("ana"), parse("typing", JSONObject("""{"username":"ana"}""")))
        val limited = parse("chat-rate-limited", JSONObject("""{"message":"Esperá un toque."}""")) as RoomEvent.ChatRateLimited
        assertEquals("Esperá un toque.", limited.message)
        // Sin mensaje en el payload se usa uno propio, nunca queda vacío.
        val generic = parse("chat-rate-limited") as RoomEvent.ChatRateLimited
        assertTrue(generic.message.isNotBlank())
    }

    @Test
    fun eventosConFormaInesperadaOQueNoSeUsanDanNull() {
        assertNull(parse("chat-message", "no soy un objeto"))
        assertNull(parse("viewer-count", "tres"))
        assertNull(parse("room-error"))
        assertNull(parse("sync", JSONObject("""{"type":"heartbeat","time":1}""")))
        assertNull(parse("evento-desconocido"))
    }
}
