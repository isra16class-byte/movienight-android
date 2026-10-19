package com.isra16.movienight

import com.isra16.movienight.net.DEFAULT_HOME_TAB
import com.isra16.movienight.net.HomeTab
import com.isra16.movienight.net.homeTabAt
import com.isra16.movienight.net.showHomeBottomBar
import com.isra16.movienight.net.showHomeHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTabLogicTest {
    @Test
    fun `el orden y los nombres son los de la barra`() {
        assertEquals(listOf("Salas", "Biblioteca", "Subidas", "Perfil"), HomeTab.entries.map { it.label })
    }

    @Test
    fun `cada numero guardado devuelve su pestana`() {
        HomeTab.entries.forEach { assertEquals(it, homeTabAt(it.ordinal)) }
    }

    @Test
    fun `un numero fuera de rango da la pestana por defecto`() {
        assertEquals(DEFAULT_HOME_TAB, homeTabAt(-1))
        assertEquals(DEFAULT_HOME_TAB, homeTabAt(HomeTab.entries.size))
        assertEquals(DEFAULT_HOME_TAB, homeTabAt(Int.MAX_VALUE))
    }

    @Test
    fun `se abre en Salas`() {
        assertEquals(HomeTab.ROOMS, DEFAULT_HOME_TAB)
        assertEquals(0, DEFAULT_HOME_TAB.ordinal)
    }

    @Test
    fun `la barra se esconde solo con el teclado abierto`() {
        assertTrue(showHomeBottomBar(0))
        assertTrue(showHomeBottomBar(-5))
        assertFalse(showHomeBottomBar(1))
        assertFalse(showHomeBottomBar(900))
    }

    @Test
    fun `el encabezado sale en todas las pestanas menos en Perfil`() {
        assertTrue(showHomeHeader(HomeTab.ROOMS))
        assertTrue(showHomeHeader(HomeTab.LIBRARY))
        assertTrue(showHomeHeader(HomeTab.UPLOADS))
        assertFalse(showHomeHeader(HomeTab.PROFILE))
        assertEquals(listOf(HomeTab.PROFILE), HomeTab.entries.filterNot { showHomeHeader(it) })
    }
}
