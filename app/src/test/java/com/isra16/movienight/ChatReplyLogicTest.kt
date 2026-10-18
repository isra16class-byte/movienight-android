package com.isra16.movienight

import com.isra16.movienight.net.ChatMessage
import com.isra16.movienight.net.REPLY_QUOTE_MAX_CHARS
import com.isra16.movienight.net.REPLY_SWIPE_MAX_DP
import com.isra16.movienight.net.REPLY_SWIPE_THRESHOLD_DP
import com.isra16.movienight.net.ReplyTo
import com.isra16.movienight.net.canReplyTo
import com.isra16.movienight.net.replyFor
import com.isra16.movienight.net.replyIconAlpha
import com.isra16.movienight.net.replySwipeDirection
import com.isra16.movienight.net.replySwipeOffset
import com.isra16.movienight.net.replySwipeReached
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatReplyLogicTest {

    private fun msg(text: String, system: Boolean = false, user: String = "Ana", isHost: Boolean = false) =
        ChatMessage(system = system, user = user, text = text, isHost = isHost, userId = "u1", replyTo = null)

    @Test
    fun `el tope de la cita es 100`() {
        assertEquals(100, REPLY_QUOTE_MAX_CHARS)
    }

    @Test
    fun `un mensaje del sistema no se puede responder`() {
        assertFalse(canReplyTo(msg("Ana se unió a la sala", system = true)))
        assertNull(replyFor(msg("Ana se unió a la sala", system = true)))
    }

    @Test
    fun `un texto vacio o en blanco no se puede responder`() {
        for (t in listOf("", " ", "   ", "\n", " \t\n ")) {
            assertFalse("[$t]", canReplyTo(msg(t)))
            assertNull("[$t]", replyFor(msg(t)))
        }
    }

    @Test
    fun `un mensaje normal se puede responder`() {
        assertTrue(canReplyTo(msg("hola")))
        assertNotNull(replyFor(msg("hola")))
    }

    @Test
    fun `user e isHost se conservan tal cual`() {
        assertEquals(ReplyTo("Marta", "hola", true), replyFor(msg("hola", user = "Marta", isHost = true)))
        assertEquals(ReplyTo("  Luis ", "hola", false), replyFor(msg("hola", user = "  Luis ", isHost = false)))
    }

    @Test
    fun `justo 100 caracteres no se recorta`() {
        val t = "a".repeat(100)
        assertEquals(t, replyFor(msg(t))!!.text)
    }

    @Test
    fun `101 caracteres dan 99 mas puntos suspensivos`() {
        val r = replyFor(msg("a".repeat(101)))!!.text
        assertEquals("a".repeat(99) + "…", r)
        assertEquals(100, r.length)
    }

    @Test
    fun `los saltos de linea y espacios repetidos pasan a un espacio y se hace trim`() {
        assertEquals("hola que tal", replyFor(msg("  hola\n\nque   tal \n"))!!.text)
        assertEquals("a b c", replyFor(msg("a\tb\r\nc"))!!.text)
    }

    @Test
    fun `el colapso va antes del recorte`() {
        // 60 letras + 40 saltos de linea + 60 letras: colapsado son 121 -> se recorta; sin colapsar serian 160.
        val t = "a".repeat(60) + "\n".repeat(40) + "b".repeat(60)
        val r = replyFor(msg(t))!!.text
        assertEquals("a".repeat(60) + " " + "b".repeat(38) + "…", r)
    }

    @Test
    fun `un emoji en el borde del recorte no se parte`() {
        // 98 letras + emoji (2 unidades) + "zz": el corte a 99 unidades caeria en medio del emoji.
        val emoji = "😀"
        assertEquals(2, emoji.length)
        val r = replyFor(msg("a".repeat(98) + emoji + "zz"))!!.text
        assertEquals("a".repeat(98) + "…", r)
        assertFalse(Character.isHighSurrogate(r[r.length - 2]))
        // 97 letras + emoji + "zz": el emoji cabe entero (99 unidades) y se conserva.
        val r2 = replyFor(msg("a".repeat(97) + emoji + "zz"))!!.text
        assertEquals("a".repeat(97) + emoji + "…", r2)
    }

    @Test
    fun `el texto recortado nunca pasa de 100 unidades`() {
        for (extra in 0..10) {
            val r = replyFor(msg("😀".repeat(40 + extra)))!!.text
            assertTrue("extra=$extra largo=${r.length}", r.length <= 100)
        }
    }

    @Test
    fun `el desplazamiento solo es hacia la derecha`() {
        assertEquals(0f, replySwipeOffset(-30f, 200f), 1e-6f)
        assertEquals(0f, replySwipeOffset(0f, 200f), 1e-6f)
    }

    @Test
    fun `el desplazamiento tiene resistencia y tope`() {
        assertEquals(40f, replySwipeOffset(50f, 200f), 1e-4f) // 0,8 * 50
        assertEquals(72f, replySwipeOffset(500f, 72f), 1e-4f)
        assertEquals(0f, replySwipeOffset(50f, 0f), 1e-6f)
    }

    @Test
    fun `se responde solo al llegar al umbral`() {
        assertTrue(REPLY_SWIPE_THRESHOLD_DP < REPLY_SWIPE_MAX_DP)
        assertEquals(56f, REPLY_SWIPE_THRESHOLD_DP, 1e-6f)
        assertEquals(72f, REPLY_SWIPE_MAX_DP, 1e-6f)
        assertFalse(replySwipeReached(55.9f, 56f))
        assertTrue(replySwipeReached(56f, 56f))
        assertTrue(replySwipeReached(72f, 56f))
        assertFalse(replySwipeReached(10f, 0f))
    }

    @Test
    fun `el umbral se alcanza con un arrastre real posible`() {
        // 70 dp de dedo * 0,8 = 56 dp: justo el umbral; el tope de 72 dp lo permite.
        assertTrue(replySwipeReached(replySwipeOffset(70f, 72f), 56f))
        assertFalse(replySwipeReached(replySwipeOffset(69f, 72f), 56f))
    }

    @Test
    fun `los mensajes propios se deslizan a la izquierda y los ajenos a la derecha`() {
        assertEquals(-1f, replySwipeDirection(true), 0f)
        assertEquals(1f, replySwipeDirection(false), 0f)
    }

    @Test
    fun `con la direccion aplicada el arrastre hacia el lado correcto avanza y hacia el otro no`() {
        // Misma cuenta que MessageRow: raw = (raw + arrastre * dirección).coerceAtLeast(0).
        fun offsetFor(drag: Float, mine: Boolean) = replySwipeOffset((drag * replySwipeDirection(mine)).coerceAtLeast(0f), 72f)
        assertEquals(40f, offsetFor(50f, mine = false), 1e-4f)
        assertEquals(0f, offsetFor(-50f, mine = false), 1e-6f)
        assertEquals(40f, offsetFor(-50f, mine = true), 1e-4f)
        assertEquals(0f, offsetFor(50f, mine = true), 1e-6f)
    }

    @Test
    fun `el icono aparece con el avance`() {
        assertEquals(0f, replyIconAlpha(0f, 56f), 1e-6f)
        assertEquals(0.5f, replyIconAlpha(28f, 56f), 1e-6f)
        assertEquals(1f, replyIconAlpha(56f, 56f), 1e-6f)
        assertEquals(1f, replyIconAlpha(72f, 56f), 1e-6f)
        assertEquals(0f, replyIconAlpha(10f, 0f), 1e-6f)
    }
}
