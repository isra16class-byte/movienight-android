package com.isra16.movienight

import com.isra16.movienight.net.ChatMessage
import com.isra16.movienight.net.DANMAKU_HOST_COLOR
import com.isra16.movienight.net.DANMAKU_LANES
import com.isra16.movienight.net.DANMAKU_LANE_RESERVE_MS
import com.isra16.movienight.net.DANMAKU_MAX_ACTIVE
import com.isra16.movienight.net.DanmakuLanes
import com.isra16.movienight.net.danmakuDurationMs
import com.isra16.movienight.net.danmakuNameColor
import com.isra16.movienight.net.danmakuText
import com.isra16.movienight.net.danmakuTopFraction
import com.isra16.movienight.net.shouldShowDanmaku
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuLogicTest {

    private fun chat(
        text: String,
        user: String = "Ana",
        system: Boolean = false,
        isHost: Boolean = false,
    ) = ChatMessage(system = system, user = user, text = text, isHost = isHost, userId = "u1", replyTo = null)

    // --- valores que la web (room.html) también usa -----------------------------------------------

    @Test
    fun `los valores copiados de la web`() {
        assertEquals(6, DANMAKU_LANES)
        assertEquals(1_300L, DANMAKU_LANE_RESERVE_MS)
        assertEquals(12, DANMAKU_MAX_ACTIVE)
    }

    @Test
    fun `altura de cada carril como en la web`() {
        // La web: top = 10 + lane * 7.5 (en %).
        val esperado = listOf(0.10f, 0.175f, 0.25f, 0.325f, 0.40f, 0.475f)
        esperado.forEachIndexed { lane, top -> assertEquals(top, danmakuTopFraction(lane), 1e-6f) }
    }

    @Test
    fun `duracion segun el largo del texto`() {
        // La web: min(15, max(7, largo / 10 + 6)) segundos. Los valores salen de ejecutar esa fórmula en node.
        assertEquals(7_000L, danmakuDurationMs(0))
        assertEquals(7_000L, danmakuDurationMs(5))
        assertEquals(7_000L, danmakuDurationMs(10))
        assertEquals(7_100L, danmakuDurationMs(11))
        assertEquals(10_000L, danmakuDurationMs(40))
        assertEquals(14_000L, danmakuDurationMs(80))
        assertEquals(15_000L, danmakuDurationMs(90))
        assertEquals(15_000L, danmakuDurationMs(91))
        assertEquals(15_000L, danmakuDurationMs(500))
    }

    // --- qué mensajes se muestran -----------------------------------------------------------------

    @Test
    fun `los mensajes de sistema no se muestran`() {
        assertFalse(shouldShowDanmaku(chat("Ana se unió a la sala", system = true)))
    }

    @Test
    fun `un mensaje vacio no se muestra y uno normal si`() {
        assertFalse(shouldShowDanmaku(chat("   ")))
        assertTrue(shouldShowDanmaku(chat("jajaja")))
    }

    @Test
    fun `los saltos de linea pasan a espacios`() {
        assertEquals("uno dos tres cuatro", danmakuText("uno\ndos\r\ntres\rcuatro").replace("  ", " "))
        assertFalse(danmakuText("a\nb").contains('\n'))
    }

    // --- colores del nombre (mismos que la web) ---------------------------------------------------

    @Test
    fun `el host siempre es cian`() {
        assertEquals(0xFF00E5FF.toInt(), DANMAKU_HOST_COLOR)
        assertEquals(DANMAKU_HOST_COLOR, danmakuNameColor("Ana", isHost = true))
        assertEquals(DANMAKU_HOST_COLOR, danmakuNameColor("", isHost = true))
    }

    @Test
    fun `el color por nombre coincide con la web`() {
        // Valores obtenidos ejecutando usernameColor() de room.html en node (incluye hashes negativos).
        val esperados = mapOf(
            "Ana" to 0xFFFFE066,
            "Luis" to 0xFFFF2E9A,
            "Zoe" to 0xFF5EC8FF,
            "" to 0xFFFF2E9A,
            "Mar\u00EDa" to 0xFFB18AFF,
            "Pepe_99" to 0xFFB18AFF,
            "Isra" to 0xFFB18AFF,
            "x" to 0xFFB18AFF,
            // hash negativo (desborda 32 bits)
            "un nombre largu\u00EDsimo que seguro desborda el hash de 32 bits s\u00ED o s\u00ED 12345" to 0xFFFF7A45,
            "\uD83D\uDE00 Sofi" to 0xFFFF5C72,
        )
        esperados.forEach { (nombre, color) ->
            assertEquals("color de '$nombre'", color.toInt(), danmakuNameColor(nombre, isHost = false))
        }
    }

    // --- carriles ---------------------------------------------------------------------------------

    @Test
    fun `los carriles se reparten en orden mientras estan libres`() {
        val lanes = DanmakuLanes()
        val usados = (0 until DANMAKU_LANES).map { lanes.pick(now = 10_000) }
        assertEquals(listOf(0, 1, 2, 3, 4, 5), usados)
    }

    @Test
    fun `un carril se reserva 1300 ms y luego vuelve a estar libre`() {
        val lanes = DanmakuLanes()
        assertEquals(0, lanes.pick(now = 10_000))
        // Un instante antes de cumplirse la reserva del carril 0 todavía no está libre: se usa el 1.
        assertEquals(1, lanes.pick(now = 10_000 + DANMAKU_LANE_RESERVE_MS - 1))
        // Justo al cumplirse ya se puede reusar (la web compara con <=).
        assertEquals(0, lanes.pick(now = 10_000 + DANMAKU_LANE_RESERVE_MS))
    }

    @Test
    fun `con todos ocupados se usa el que se libera antes`() {
        val lanes = DanmakuLanes()
        // Seis mensajes escalonados cada 100 ms: los carriles quedan libres a 11300, 11400, ... 11800.
        (0 until DANMAKU_LANES).forEach { i -> assertEquals(i, lanes.pick(now = 10_000L + i * 100)) }
        // Séptimo a los 10 500 ms: todos ocupados -> el 0 (se libera a 11 300) y pasa a liberarse a 11 800.
        assertEquals(0, lanes.pick(now = 10_500))
        // Octavo a los 10 600 ms: ahora el que se libera antes es el 1 (11 400).
        assertEquals(1, lanes.pick(now = 10_600))
    }

    @Test
    fun `clear reinicia los carriles`() {
        val lanes = DanmakuLanes()
        (0 until DANMAKU_LANES).forEach { lanes.pick(now = 10_000) }
        lanes.clear()
        assertEquals(0, lanes.pick(now = 10_001))
    }

    // --- plan: qué se muestra y cómo --------------------------------------------------------------

    @Test
    fun `plan arma el comentario con carril, duracion y color`() {
        val lanes = DanmakuLanes()
        val spec = lanes.plan(chat("hola a todos", user = "Ana"), activeCount = 0, now = 10_000)
        assertNotNull(spec)
        spec!!
        assertEquals(0, spec.lane)
        assertEquals(0.10f, spec.topFraction, 1e-6f)
        assertEquals(danmakuDurationMs("hola a todos".length), spec.durationMs)
        assertEquals("Ana", spec.user)
        assertEquals("hola a todos", spec.text)
        assertEquals(0xFFFFE066.toInt(), spec.nameColor)
    }

    @Test
    fun `plan usa el color de host y limpia los saltos de linea`() {
        val spec = DanmakuLanes().plan(chat("uno\ndos", user = "Isra", isHost = true), activeCount = 0, now = 10_000)!!
        assertEquals(DANMAKU_HOST_COLOR, spec.nameColor)
        assertFalse(spec.text.contains('\n'))
    }

    @Test
    fun `plan descarta sistema y vacios sin gastar carril`() {
        val lanes = DanmakuLanes()
        assertNull(lanes.plan(chat("alguien salió", system = true), activeCount = 0, now = 10_000))
        assertNull(lanes.plan(chat("  "), activeCount = 0, now = 10_000))
        // El carril 0 sigue libre: el primer mensaje real lo toma.
        assertEquals(0, lanes.plan(chat("hola"), activeCount = 0, now = 10_000)!!.lane)
    }

    @Test
    fun `plan respeta el tope de comentarios a la vez sin gastar carril`() {
        val lanes = DanmakuLanes()
        assertNull(lanes.plan(chat("uno"), activeCount = DANMAKU_MAX_ACTIVE, now = 10_000))
        assertNull(lanes.plan(chat("dos"), activeCount = DANMAKU_MAX_ACTIVE + 3, now = 10_000))
        // Con un lugar libre sí entra, y toma el carril 0 (los descartados no lo reservaron).
        val spec = lanes.plan(chat("tres"), activeCount = DANMAKU_MAX_ACTIVE - 1, now = 10_000)
        assertNotNull(spec)
        assertEquals(0, spec!!.lane)
    }

    @Test
    fun `muchos mensajes en el mismo instante recorren los carriles y despues se encima el primero`() {
        val lanes = DanmakuLanes()
        val carriles = (0 until 8).map { i -> lanes.plan(chat("m$i"), activeCount = i, now = 10_000)!!.lane }
        // Seis carriles distintos; con todos ocupados y empatados se repite el primero (igual que la web).
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 0, 0), carriles)
    }
}
