package com.isra16.movienight

import com.isra16.movienight.net.RoomEvent
import com.isra16.movienight.net.RoomPosition
import com.isra16.movienight.net.SyncMessage
import com.isra16.movienight.net.SyncType
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
        assertEquals(
            RoomEvent.RoomData("/uploads/1__x.mp4", RoomPosition(timeMs = 0, paused = true)),
            parse("room-data", data),
        )
        // Sin `position` (o con una forma rara) el video igual se carga, solo que sin posición inicial.
        assertEquals(RoomEvent.RoomData(null, null), parse("room-data", JSONObject("""{"videoFile":null}""")))
        assertEquals(
            RoomEvent.RoomData("/uploads/1__x.mp4", null),
            parse("room-data", JSONObject("""{"videoFile":"/uploads/1__x.mp4","position":null}""")),
        )
        assertEquals(RoomEvent.VideoChanged("/uploads/2__y.mp4"), parse("video-changed", JSONObject("""{"videoFile":"/uploads/2__y.mp4"}""")))
    }

    @Test
    fun roomDataConLaPosicionDondeVaLaSala() {
        val data = JSONObject("""{"videoFile":"/uploads/1__x.mp4","subtitleFile":null,"position":{"time":125.4,"paused":false}}""")
        val event = parse("room-data", data) as RoomEvent.RoomData
        assertEquals(RoomPosition(timeMs = 125_400, paused = false), event.position)
    }

    @Test
    fun syncDelHost() {
        val play = parse("sync", JSONObject("""{"type":"play","time":12.5}""")) as RoomEvent.Sync
        assertEquals(SyncMessage(SyncType.PLAY, 12_500, null), play.message)
        val beat = parse("sync", JSONObject("""{"type":"heartbeat","time":60,"paused":true}""")) as RoomEvent.Sync
        assertEquals(SyncMessage(SyncType.HEARTBEAT, 60_000, true), beat.message)
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
        assertNull(parse("sync", "no soy un objeto"))
        assertNull(parse("sync", JSONObject("""{"type":"heartbeat"}""")))
        assertNull(parse("reaction", JSONObject("""{"emoji":"x"}""")))
        assertNull(parse("evento-desconocido"))
    }

    // --- Fase 3C: subtítulos ---

    @Test
    fun subtituloNuevo() {
        assertEquals(
            RoomEvent.SubtitleChanged("/uploads/ab12cd34.vtt"),
            parse("subtitle-changed", JSONObject("""{"subtitleFile":"/uploads/ab12cd34.vtt"}""")),
        )
    }

    @Test
    fun subtituloConFormaRaraSeDescarta() {
        assertNull(parse("subtitle-changed", JSONObject("""{"subtitleFile":null}""")))
        assertNull(parse("subtitle-changed", JSONObject("""{"subtitleFile":""}""")))
        assertNull(parse("subtitle-changed", JSONObject("""{}""")))
        assertNull(parse("subtitle-changed", "/uploads/ab12cd34.vtt")) // el server manda un objeto, no un texto plano
    }

    @Test
    fun datosDeLaSalaTraenElSubtitulo() {
        val data = JSONObject("""{"videoFile":"/uploads/1__x.mp4","subtitleFile":"/uploads/ab12cd34.vtt","position":{"time":12.5,"paused":false}}""")
        assertEquals(
            RoomEvent.RoomData("/uploads/1__x.mp4", RoomPosition(12_500, paused = false), "/uploads/ab12cd34.vtt"),
            parse("room-data", data),
        )
        assertNull((parse("room-data", JSONObject("""{"videoFile":"/uploads/1__x.mp4","subtitleFile":null}""")) as RoomEvent.RoomData).subtitleFile)
    }

    @Test
    fun subtitleChangedEstaEntreLosEventosEscuchados() {
        assertTrue("subtitle-changed" in com.isra16.movienight.net.HANDLED_SERVER_EVENTS)
    }
}
