package com.isra16.movienight

import com.isra16.movienight.auth.DISPLAY_NAME_MAX_LENGTH
import com.isra16.movienight.auth.displayNameError
import com.isra16.movienight.auth.effectiveUsername
import com.isra16.movienight.auth.limitDisplayNameInput
import com.isra16.movienight.auth.normalizeDisplayName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileNameLogicTest {

    private val emoji = "\uD83D\uDE00" // 😀: un par sustituto (2 unidades UTF-16)

    @Test
    fun quitaEspaciosDeLosBordesYColapsaLosRepetidos() {
        assertEquals("Ana Pérez", normalizeDisplayName("  Ana    Pérez  "))
    }

    @Test
    fun tabuladoresYSaltosDeLineaPasanAUnEspacio() {
        assertEquals("Ana Pérez", normalizeDisplayName("Ana\tPérez"))
        assertEquals("Ana Pérez", normalizeDisplayName("Ana\r\n\nPérez"))
        assertEquals("Ana", normalizeDisplayName("\nAna\n"))
    }

    @Test
    fun quitaLosCaracteresDeControl() {
        assertEquals("Ana", normalizeDisplayName("A\u0000n\u0007a"))
        assertEquals("Ana", normalizeDisplayName("A\u0085na"))
    }

    @Test
    fun unCaracterOMenosNoValeYLosEspaciosSolosTampoco() {
        assertNull(normalizeDisplayName(""))
        assertNull(normalizeDisplayName("   "))
        assertNull(normalizeDisplayName("\n\t"))
        assertNull(normalizeDisplayName("a"))
        assertNull(normalizeDisplayName("  a  "))
        assertNull(normalizeDisplayName(emoji)) // un emoji es un solo carácter
        assertEquals("ab", normalizeDisplayName("ab"))
        assertEquals("a$emoji", normalizeDisplayName("a$emoji"))
    }

    @Test
    fun exactamente40SeConservaY41SeCorta() {
        val c40 = "a".repeat(40)
        assertEquals(c40, normalizeDisplayName(c40))
        assertEquals(c40, normalizeDisplayName(c40 + "b"))
        assertEquals(40, normalizeDisplayName("a".repeat(60))!!.length)
    }

    @Test
    fun elRecorteNoParteUnEmojiEnElBorde() {
        // 39 letras + emoji = 41 unidades: el emoji no cabe entero, se descarta completo (no queda media pareja).
        val cortado = normalizeDisplayName("a".repeat(39) + emoji)!!
        assertEquals("a".repeat(39), cortado)
        assertFalse(cortado.any { Character.isSurrogate(it) })
        // 38 letras + emoji = 40 unidades: cabe justo.
        val justo = "a".repeat(38) + emoji
        assertEquals(justo, normalizeDisplayName(justo))
        assertEquals(40, justo.length)
    }

    @Test
    fun alCortarNoQuedaUnEspacioAlFinal() {
        assertEquals("a".repeat(39), normalizeDisplayName("a".repeat(39) + " bbb"))
    }

    @Test
    fun limitDisplayNameInputNoTocaLosEspaciosPeroSiCorta() {
        assertEquals("ana ", limitDisplayNameInput("ana "))
        assertEquals("a".repeat(40), limitDisplayNameInput("a".repeat(60)))
        assertEquals("a".repeat(39), limitDisplayNameInput("a".repeat(39) + emoji))
        assertEquals(DISPLAY_NAME_MAX_LENGTH, 40)
    }

    @Test
    fun errorEnCadaCaso() {
        assertEquals("Escribe un nombre.", displayNameError(""))
        assertEquals("Escribe un nombre.", displayNameError("   \n "))
        assertEquals("Usa al menos 2 caracteres.", displayNameError("a"))
        assertEquals("Usa al menos 2 caracteres.", displayNameError(" a "))
        assertEquals("Usa al menos 2 caracteres.", displayNameError(emoji))
        assertNull(displayNameError("ab"))
        assertNull(displayNameError("a".repeat(60))) // el tope se aplica al escribir, sin mensaje
    }

    @Test
    fun losMensajesNoTienenVoseoNiTecnicismos() {
        val voseo = Regex("(?U)\\b(vos|sos|escribí|usá|elegí|tenés)\\b", RegexOption.IGNORE_CASE)
        for (m in listOf(displayNameError("")!!, displayNameError("a")!!)) {
            assertFalse(m, voseo.containsMatchIn(m))
            assertTrue(m, m.length <= 40)
        }
    }

    @Test
    fun elNombreEfectivoUsaElElegidoSiVale() {
        assertEquals("Ana", effectiveUsername("Ana", "isra@ejemplo.com"))
        assertEquals("Ana Pérez", effectiveUsername("  Ana   Pérez ", "isra@ejemplo.com"))
    }

    @Test
    fun elNombreEfectivoCaeAlDelCorreoSiNoHayNadaOValeNada() {
        assertEquals("isra", effectiveUsername(null, "isra@ejemplo.com"))
        assertEquals("isra", effectiveUsername("", "isra@ejemplo.com"))
        assertEquals("isra", effectiveUsername("   ", "isra@ejemplo.com"))
        assertEquals("isra", effectiveUsername("a", "isra@ejemplo.com"))
        assertEquals("Anónimo", effectiveUsername(null, ""))
    }
}
