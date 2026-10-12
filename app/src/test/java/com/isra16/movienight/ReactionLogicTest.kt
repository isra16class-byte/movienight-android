package com.isra16.movienight

import com.isra16.movienight.net.REACTION_DURATION_MS
import com.isra16.movienight.net.REACTION_BAR_EMOJIS
import com.isra16.movienight.net.REACTION_PANEL_EMOJIS
import com.isra16.movienight.net.REACTION_PIP_FONT_SP
import com.isra16.movienight.net.REACTION_PIP_MAX_ACTIVE
import com.isra16.movienight.net.ReactionSurface
import com.isra16.movienight.net.reactionLookFor
import com.isra16.movienight.net.reactionTravel
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
    fun laBarraTieneLos14EmojisDeLaWebEnSuOrden() {
        assertEquals(
            listOf("😂", "😭", "😍", "😱", "🔥", "❤️", "👍", "👏", "🙌", "💀", "🎉", "😅", "😠", "😴"),
            REACTION_BAR_EMOJIS,
        )
        assertEquals(REACTION_BAR_EMOJIS.size, REACTION_BAR_EMOJIS.toSet().size)
    }

    @Test
    fun elPanelTieneLosPrimeros8DeLaBarra() {
        assertEquals(listOf("😂", "😭", "😍", "😱", "🔥", "❤️", "👍", "👏"), REACTION_PANEL_EMOJIS)
        assertEquals(REACTION_BAR_EMOJIS.take(8), REACTION_PANEL_EMOJIS)
    }

    @Test
    fun elPanelQuedaEnDosFilasDeCuatro() {
        val filas = REACTION_PANEL_EMOJIS.chunked(REACTION_PANEL_COLUMNS)
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
        for (e in REACTION_BAR_EMOJIS) assertTrue(isSendableReaction(e))
        assertTrue(isSendableReaction("😴")) // el último de la barra, que el panel no ofrece
        assertFalse(isSendableReaction("🤯")) // no está en la web
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

    @Test
    fun planRespetaElTopeQueSePasa() {
        assertNotNull(planReaction("🔥", 7, 0.5f, maxActive = REACTION_PIP_MAX_ACTIVE))
        assertNull(planReaction("🔥", 8, 0.5f, maxActive = REACTION_PIP_MAX_ACTIVE))
    }

    @Test
    fun elAspectoDeLaPantallaCompletaNoCambio() {
        val look = reactionLookFor(ReactionSurface.FULLSCREEN)
        assertEquals(30f, look.fontSp, 0f)
        assertEquals(20, look.maxActive)
        val t = reactionTravel(ReactionSurface.FULLSCREEN, heightDp = 360f, fontSp = look.fontSp)
        assertEquals(70f, t.bottomDp, 0f)
        assertEquals(280f, t.riseDp, 0f)
        // El alto no importa en pantalla completa.
        assertEquals(t, reactionTravel(ReactionSurface.FULLSCREEN, heightDp = 50f, fontSp = 30f))
    }

    @Test
    fun enLaVistaVerticalElRecorridoSeAdaptaAlAltoDelVideo() {
        val look = reactionLookFor(ReactionSurface.PORTRAIT)
        assertEquals(30f, look.fontSp, 0f)
        assertEquals(20, look.maxActive)
        val t = reactionTravel(ReactionSurface.PORTRAIT, heightDp = 225f, fontSp = look.fontSp)
        assertEquals(33.75f, t.bottomDp, 1e-4f)
        assertEquals(153.75f, t.riseDp, 1e-4f) // 225 - 33,75 - 37,5
        // El emoji entero queda dentro de la caja al terminar de subir.
        assertTrue(t.bottomDp + t.riseDp + 30f * 1.25f <= 225f + 1e-4f)
    }

    @Test
    fun conElTecladoAbiertoElVideoVerticalEsBajoYElEmojiSigueDentro() {
        val t = reactionTravel(ReactionSurface.PORTRAIT, heightDp = 90f, fontSp = 30f)
        assertEquals(13.5f, t.bottomDp, 1e-4f)
        assertTrue(t.riseDp >= 0f)
        assertTrue(t.bottomDp + t.riseDp + 37.5f <= 90f + 1e-4f)
    }

    @Test
    fun elRecorridoNuncaSuperaLosTopesDeLaWeb() {
        val t = reactionTravel(ReactionSurface.PORTRAIT, heightDp = 2000f, fontSp = 30f)
        assertEquals(70f, t.bottomDp, 0f)
        assertEquals(280f, t.riseDp, 0f)
    }

    @Test
    fun unaCajaMinusculaNoDaNumerosNegativos() {
        val t = reactionTravel(ReactionSurface.PIP, heightDp = 0f, fontSp = 18f)
        assertEquals(4f, t.bottomDp, 0f)
        assertEquals(0f, t.riseDp, 0f)
        val t2 = reactionTravel(ReactionSurface.PIP, heightDp = 20f, fontSp = 18f)
        assertTrue(t2.riseDp >= 0f)
    }

    @Test
    fun enLaVentanaFlotanteElEmojiEsChicoYHayMenos() {
        val look = reactionLookFor(ReactionSurface.PIP)
        assertEquals(REACTION_PIP_FONT_SP, look.fontSp, 0f)
        assertEquals(18f, look.fontSp, 0f)
        assertEquals(8, look.maxActive)
        val t = reactionTravel(ReactionSurface.PIP, heightDp = 135f, fontSp = look.fontSp)
        assertEquals(20.25f, t.bottomDp, 1e-4f)
        assertEquals(92.25f, t.riseDp, 1e-4f) // 135 - 20,25 - 22,5
        assertTrue(t.bottomDp + t.riseDp + 18f * 1.25f <= 135f + 1e-4f)
    }
}
