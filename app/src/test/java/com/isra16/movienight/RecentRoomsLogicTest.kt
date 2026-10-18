package com.isra16.movienight

import com.isra16.movienight.net.RECENT_ROOMS_MAX
import com.isra16.movienight.net.RecentRoom
import com.isra16.movienight.net.RecentRoomItem
import com.isra16.movienight.net.RoomCheckStatus
import com.isra16.movienight.net.applyRoomCheck
import com.isra16.movienight.net.decodeRecentRooms
import com.isra16.movienight.net.encodeRecentRooms
import com.isra16.movienight.net.normalizeRecentRoomId
import com.isra16.movienight.net.recentRoomSubtitle
import com.isra16.movienight.net.recentRoomTitle
import com.isra16.movienight.net.relativeTimeLabel
import com.isra16.movienight.net.removeRecentRoom
import com.isra16.movienight.net.roomCheckFailedByNetwork
import com.isra16.movienight.net.roomCheckStatus
import com.isra16.movienight.net.toItem
import com.isra16.movienight.net.upsertRecentRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentRoomsLogicTest {

    private fun room(id: String, name: String = "peli", at: Long = 1000L) = RecentRoom(id, name, at)

    // --- upsert / remove ---

    @Test
    fun upsertAgregaUnaSalaNuevaAlFrente() {
        val r = upsertRecentRoom(listOf(room("aaaaaa", at = 1)), "bbbbbb", "otra", 5)
        assertEquals(listOf(RecentRoom("bbbbbb", "otra", 5), room("aaaaaa", at = 1)), r)
    }

    @Test
    fun upsertDeUnaExistenteLaSubeAlFrenteSinDuplicarYActualizaLaHora() {
        val r = upsertRecentRoom(listOf(room("aaaaaa", at = 1), room("bbbbbb", at = 2)), "bbbbbb", "nueva", 9)
        assertEquals(listOf("bbbbbb", "aaaaaa"), r.map { it.roomId })
        assertEquals(9L, r[0].lastJoinedMillis)
        assertEquals("nueva", r[0].videoName)
    }

    @Test
    fun upsertConNombreVacioOSoloEspaciosConservaElAnterior() {
        val base = listOf(room("aaaaaa", "mi-peli.mp4", 1))
        assertEquals("mi-peli.mp4", upsertRecentRoom(base, "aaaaaa", "", 2)[0].videoName)
        assertEquals("mi-peli.mp4", upsertRecentRoom(base, "aaaaaa", "   ", 2)[0].videoName)
    }

    @Test
    fun upsertDeSalaNuevaSinNombreQuedaSinNombre() {
        assertEquals("", upsertRecentRoom(emptyList(), "aaaaaa", "", 1)[0].videoName)
    }

    @Test
    fun upsertConTopeDescartaLaMasAntigua() {
        var list = emptyList<RecentRoom>()
        for (i in 1..RECENT_ROOMS_MAX) list = upsertRecentRoom(list, "sala$i", "v$i", i.toLong())
        assertEquals(RECENT_ROOMS_MAX, list.size)
        list = upsertRecentRoom(list, "nueva1", "x", 100)
        assertEquals(RECENT_ROOMS_MAX, list.size)
        assertEquals("nueva1", list.first().roomId)
        assertFalse(list.any { it.roomId == "sala1" })
        assertTrue(list.any { it.roomId == "sala2" })
    }

    @Test
    fun elTopeEsDeVeinteSalas() {
        assertEquals(20, RECENT_ROOMS_MAX)
        var list = emptyList<RecentRoom>()
        for (i in 1..25) list = upsertRecentRoom(list, "sala$i", "v", i.toLong())
        assertEquals(20, list.size)
        assertEquals("sala25", list.first().roomId)
        assertEquals("sala6", list.last().roomId)
    }

    @Test
    fun upsertDeUnaExistenteConLaListaLlenaNoDescartaNada() {
        var list = emptyList<RecentRoom>()
        for (i in 1..RECENT_ROOMS_MAX) list = upsertRecentRoom(list, "sala$i", "v", i.toLong())
        val r = upsertRecentRoom(list, "sala1", "v", 99)
        assertEquals(RECENT_ROOMS_MAX, r.size)
        assertEquals("sala1", r.first().roomId)
    }

    @Test
    fun removeQuitaSoloEsaSala() {
        val r = removeRecentRoom(listOf(room("aaaaaa"), room("bbbbbb")), "aaaaaa")
        assertEquals(listOf("bbbbbb"), r.map { it.roomId })
        assertEquals(2, removeRecentRoom(listOf(room("aaaaaa"), room("bbbbbb")), "zzzzzz").size)
    }

    @Test
    fun normalizaElIdComoAlUnirse() {
        assertEquals("a1b2c3", normalizeRecentRoomId("A1B2C3"))
        assertEquals("a1b2c3", normalizeRecentRoomId("https://x.com/room/A1B2C3?x=1"))
        assertEquals("MiSala", normalizeRecentRoomId(" MiSala "))
    }

    // --- encode / decode ---

    @Test
    fun encodeDecodeRoundTripConTabSaltoDeLineaYEmoji() {
        val list = listOf(
            RecentRoom("aaaaaa", "peli\tcon\ttab.mp4", 10),
            RecentRoom("bbbbbb", "dos\nlineas\r\nraras", 20),
            RecentRoom("cccccc", "🎬 Película ñandú 😀", 30),
            RecentRoom("dddddd", "", 40),
        )
        val back = decodeRecentRooms(encodeRecentRooms(list))
        assertEquals(
            listOf(
                RecentRoom("aaaaaa", "peli con tab.mp4", 10),
                RecentRoom("bbbbbb", "dos lineas  raras", 20),
                RecentRoom("cccccc", "🎬 Película ñandú 😀", 30),
                RecentRoom("dddddd", "", 40),
            ),
            back,
        )
    }

    @Test
    fun decodeDeNullVacioYBasuraNoLanzaYDevuelveVacio() {
        assertEquals(emptyList<RecentRoom>(), decodeRecentRooms(null))
        assertEquals(emptyList<RecentRoom>(), decodeRecentRooms(""))
        assertEquals(emptyList<RecentRoom>(), decodeRecentRooms("basura sin tabuladores"))
        assertEquals(emptyList<RecentRoom>(), decodeRecentRooms("\n\n\t\t\n"))
    }

    @Test
    fun decodeIgnoraLasLineasRotasYConservaLasBuenas() {
        val raw = "aaaaaa\tbuena\t10\n" +
            "solo\tdos\n" +
            "bbbbbb\tfecha mala\tabc\n" +
            "cccccc\tnegativa\t-5\n" +
            "\tsin id\t1\n" +
            "con espacio\tx\t1\n" +
            "dddddd\tmuchos\tcampos\t1\n" +
            "eeeeee\totra buena\t20"
        assertEquals(listOf(RecentRoom("aaaaaa", "buena", 10), RecentRoom("eeeeee", "otra buena", 20)), decodeRecentRooms(raw))
    }

    @Test
    fun decodeNoDevuelveMasDelTope() {
        val raw = (1..(RECENT_ROOMS_MAX + 5)).joinToString("\n") { "s$it\tv\t$it" }
        assertEquals(RECENT_ROOMS_MAX, decodeRecentRooms(raw).size)
    }

    // --- comprobación de cada sala ---

    @Test
    fun roomCheckStatusSoloUn404SignificaQueYaNoExiste() {
        assertEquals(RoomCheckStatus.ACTIVE, roomCheckStatus(200))
        assertEquals(RoomCheckStatus.GONE, roomCheckStatus(404))
        for (c in listOf(-1, 429, 500, 502, 503, 504, 401, 403, 400, 204, 301)) {
            assertEquals("código $c", RoomCheckStatus.UNKNOWN, roomCheckStatus(c))
        }
    }

    @Test
    fun soloElCodigoMenosUnoEsFallaDeRed() {
        assertTrue(roomCheckFailedByNetwork(-1))
        assertFalse(roomCheckFailedByNetwork(500))
        assertFalse(roomCheckFailedByNetwork(404))
    }

    private fun items() = listOf(
        room("aaaaaa").toItem(),
        room("bbbbbb").toItem(),
    )

    @Test
    fun applyRoomCheckGoneQuitaSoloEsaSala() {
        val r = applyRoomCheck(items(), "aaaaaa", RoomCheckStatus.GONE, null)
        assertEquals(listOf("bbbbbb"), r.map { it.roomId })
    }

    @Test
    fun applyRoomCheckActiveActualizaLaContrasena() {
        val r = applyRoomCheck(items(), "bbbbbb", RoomCheckStatus.ACTIVE, true)
        assertTrue(r[1].passwordProtected)
        assertEquals(RoomCheckStatus.ACTIVE, r[1].status)
        assertFalse(r[0].passwordProtected)
        // Y puede dejar de tenerla.
        assertFalse(applyRoomCheck(r, "bbbbbb", RoomCheckStatus.ACTIVE, false)[1].passwordProtected)
    }

    @Test
    fun applyRoomCheckActiveSinPoderLeerLaContrasenaConservaLoAnterior() {
        val con = applyRoomCheck(items(), "bbbbbb", RoomCheckStatus.ACTIVE, true)
        assertTrue(applyRoomCheck(con, "bbbbbb", RoomCheckStatus.ACTIVE, null)[1].passwordProtected)
    }

    @Test
    fun applyRoomCheckUnknownLaConservaYLaMarca() {
        val activa = applyRoomCheck(items(), "aaaaaa", RoomCheckStatus.ACTIVE, true)
        val r = applyRoomCheck(activa, "aaaaaa", RoomCheckStatus.UNKNOWN, null)
        assertEquals(2, r.size)
        assertEquals(RoomCheckStatus.UNKNOWN, r[0].status)
        assertTrue(r[0].passwordProtected)
    }

    @Test
    fun applyRoomCheckDeUnaSalaQuitadaNoLaVuelveAAgregar() {
        val sinA = items().filter { it.roomId != "aaaaaa" }
        assertEquals(listOf("bbbbbb"), applyRoomCheck(sinA, "aaaaaa", RoomCheckStatus.ACTIVE, true).map { it.roomId })
        assertEquals(listOf("bbbbbb"), applyRoomCheck(sinA, "aaaaaa", RoomCheckStatus.UNKNOWN, null).map { it.roomId })
    }

    @Test
    fun toItemConservaLoUltimoQueSeSupo() {
        val previous = RecentRoomItem("aaaaaa", "x", 1, passwordProtected = true, status = RoomCheckStatus.ACTIVE)
        val fresh = room("aaaaaa", "y", 5).toItem(previous)
        assertTrue(fresh.passwordProtected)
        assertEquals(RoomCheckStatus.ACTIVE, fresh.status)
        assertEquals("y", fresh.videoName)
        assertEquals(5L, fresh.lastJoinedMillis)
        assertFalse(room("aaaaaa").toItem().passwordProtected)
    }

    // --- textos de la fila ---

    @Test
    fun tituloYSubtitulo() {
        val con = RecentRoomItem("a1b2c3", "mi-peli.mp4", 0, passwordProtected = true)
        val sin = RecentRoomItem("a1b2c3", "  ", 0)
        assertEquals("mi-peli.mp4", recentRoomTitle(con))
        assertEquals("Sala a1b2c3", recentRoomTitle(sin))
        val now = 2 * 3_600_000L
        assertEquals("Sala a1b2c3 · Última vez: hace 2 h · Con contraseña", recentRoomSubtitle(con, now))
        assertEquals("Sala a1b2c3 · Última vez: hace 2 h", recentRoomSubtitle(sin, now))
    }

    // --- tiempo relativo: cada límite ---

    private val s = 1000L
    private val min = 60 * s
    private val h = 60 * min
    private val d = 24 * h

    @Test
    fun relativeTimeCadaLimite() {
        val now = 10 * d
        fun ago(ms: Long) = relativeTimeLabel(now, now - ms)
        assertEquals("hace un momento", ago(0))
        assertEquals("hace un momento", ago(59 * s))
        assertEquals("hace un momento", ago(59 * s + 999))
        assertEquals("hace 1 min", ago(60 * s))
        assertEquals("hace 59 min", ago(59 * min))
        assertEquals("hace 59 min", ago(60 * min - 1))
        assertEquals("hace 1 h", ago(60 * min))
        assertEquals("hace 23 h", ago(23 * h))
        assertEquals("hace 23 h", ago(24 * h - 1))
        assertEquals("hace 1 día", ago(24 * h))
        assertEquals("hace 1 día", ago(47 * h))
        assertEquals("hace 2 días", ago(48 * h))
        assertEquals("hace 9 días", ago(9 * d + h))
    }

    @Test
    fun relativeTimeConHoraFuturaDiceHaceUnMomento() {
        assertEquals("hace un momento", relativeTimeLabel(1000, 5000))
    }
}
