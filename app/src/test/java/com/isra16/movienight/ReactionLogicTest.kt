package com.isra16.movienight

import com.isra16.movienight.net.REACTION_DURATION_MS
import com.isra16.movienight.net.REACTION_EMOJIS
import com.isra16.movienight.net.REACTION_LEFT_MAX_FRACTION
import com.isra16.movienight.net.REACTION_MAX_ACTIVE
import com.isra16.movienight.net.REACTION_MAX_CHARS
import com.isra16.movienight.net.REACTION_PANEL_COLUMNS
import com.isra16.movienight.net.isSendableReaction
import com.isra16.movienight.net.parseReactionEmoji
import com.isra16.movienight.net.planReaction
import com.isra16.movienight.net.reactionLeftFraction
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionLogicTest {

    @Test
    fun laListaTieneLos8EmojisDeLaWebEnSuOrden() {
        assertEquals(listOf("😂", "😭", "😍", "😱", "🔥", "❤️", "👍", "👏"), REACTION_EMOJIS)
        assertEquals(REACTION_EMOJIS.size, REACTION_EMOJIS.toSet().size)
    }

    @Test
    fun elPanelQuedaEnDosFilasDeCuatro() {
        val filas = REACTION_EMOJIS.chunked(REACTION_PANEL_COLUMNS)
        assertEquals(listOf(4, 4), filas.map { it.size })
    }

    @Test
    fun losNumerosDeLaWebSeMantienen() {
        assertEquals(2_500L, REACTION_DURATION_MS)
        assertEquals(0.8f, REACTION_LEFT_MAX_FRACTION, 0f)
        assertEquals(20, REACTION_MAX_ACTIVE)
    }

    @Test
    fun parseAceptaUnEmojiYLoDevuelveTalCual() {
        assertEquals("🔥", parseReactionEmoji("🔥"))
        assertEquals("❤️", parseReactionEmoji("❤️"))
        assertEquals("👍🏽", parseReactionEmoji("👍🏽"))
    }

    @Test
    fun parseQuitaEspaciosDeLosBordes() {
        assertEquals("😂", parseReactionEmoji("  😂 "))
    }

    @Test
    fun parseRechazaLoQueNoEsTextoUsable() {
        assertNull(parseReactionEmoji(null))
        assertNull(parseReactionEmoji(""))
        assertNull(parseReactionEmoji("   "))
        assertNull(parseReactionEmoji(5))
        assertNull(parseReactionEmoji(JSONObject("""{"emoji":"🔥"}""")))
    }

    @Test
    fun parseRechazaTextoDemasiadoLargo() {
        assertNotNull(parseReactionEmoji("a".repeat(REACTION_MAX_CHARS)))
        assertNull(parseReactionEmoji("a".repeat(REACTION_MAX_CHARS + 1)))
    }

    @Test
    fun soloSePuedenMandarLosEmojisDeLaLista() {
        for (e in REACTION_EMOJIS) assertTrue(isSendableReaction(e))
        assertFalse(isSendableReaction("🙌")) // está en la web pero no (todavía) en la app
        assertFalse(isSendableReaction(""))
        assertFalse(isSendableReaction("hola"))
    }

    @Test
    fun laPosicionHorizontalVaDeCeroAOchentaPorCiento() {
        assertEquals(0f, reactionLeftFraction(0f), 0f)
        assertEquals(0.4f, reactionLeftFraction(0.5f), 1e-6f)
        assertEquals(0.8f, reactionLeftFraction(1f), 1e-6f)
    }

    @Test
    fun unAzarFueraDeRangoNoSacaAlEmojiDeLaPantalla() {
        assertEquals(0f, reactionLeftFraction(-3f), 0f)
        assertEquals(0.8f, reactionLeftFraction(7f), 1e-6f)
    }

    @Test
    fun planUsaLaDuracionDeLaWebYElAzarRecibido() {
        val spec = planReaction("🔥", activeCount = 0, random = 0.25f)!!
        assertEquals("🔥", spec.emoji)
        assertEquals(0.2f, spec.leftFraction, 1e-6f)
        assertEquals(2_500L, spec.durationMs)
    }

    @Test
    fun planDescartaCuandoYaHayElMaximo() {
        assertNotNull(planReaction("🔥", REACTION_MAX_ACTIVE - 1, 0.5f))
        assertNull(planReaction("🔥", REACTION_MAX_ACTIVE, 0.5f))
        assertNull(planReaction("🔥", REACTION_MAX_ACTIVE + 5, 0.5f))
    }
}
