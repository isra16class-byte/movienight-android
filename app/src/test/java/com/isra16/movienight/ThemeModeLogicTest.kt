package com.isra16.movienight

import com.isra16.movienight.net.DEFAULT_THEME_MODE
import com.isra16.movienight.net.ThemeMode
import com.isra16.movienight.net.themeModeFor
import com.isra16.movienight.net.themeModeFromSwitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeLogicTest {

    @Test
    fun `el primer arranque es oscuro`() {
        assertEquals(ThemeMode.DARK, DEFAULT_THEME_MODE)
        assertEquals(ThemeMode.DARK, themeModeFor(null))
    }

    @Test
    fun `lo guardado se respeta`() {
        assertEquals(ThemeMode.LIGHT, themeModeFor("light"))
        assertEquals(ThemeMode.DARK, themeModeFor("dark"))
    }

    @Test
    fun `un valor que no se reconoce da el modo por defecto`() {
        assertEquals(DEFAULT_THEME_MODE, themeModeFor(""))
        assertEquals(DEFAULT_THEME_MODE, themeModeFor("system"))
        assertEquals(DEFAULT_THEME_MODE, themeModeFor("LIGHT"))
        assertEquals(DEFAULT_THEME_MODE, themeModeFor(" light"))
        assertEquals(DEFAULT_THEME_MODE, themeModeFor("true"))
    }

    @Test
    fun `lo que se guarda se lee igual`() {
        for (mode in ThemeMode.entries) {
            assertEquals(mode, themeModeFor(mode.storageValue))
        }
    }

    @Test
    fun `los textos guardados son los de siempre`() {
        // Cambiarlos dejaría sin efecto lo que las personas ya tienen guardado.
        assertEquals("light", ThemeMode.LIGHT.storageValue)
        assertEquals("dark", ThemeMode.DARK.storageValue)
    }

    @Test
    fun `los dos modos guardan textos distintos`() {
        assertNotEquals(ThemeMode.LIGHT.storageValue, ThemeMode.DARK.storageValue)
    }

    @Test
    fun `alternar va de un modo al otro y vuelve`() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.DARK.toggled())
        assertEquals(ThemeMode.DARK, ThemeMode.LIGHT.toggled())
        assertEquals(ThemeMode.DARK, ThemeMode.DARK.toggled().toggled())
    }

    @Test
    fun `isDark solo es verdadero en oscuro`() {
        assertTrue(ThemeMode.DARK.isDark)
        assertFalse(ThemeMode.LIGHT.isDark)
    }

    @Test
    fun `el interruptor activado es oscuro y apagado es claro`() {
        assertEquals(ThemeMode.DARK, themeModeFromSwitch(true))
        assertEquals(ThemeMode.LIGHT, themeModeFromSwitch(false))
    }

    @Test
    fun `el interruptor y el modo coinciden`() {
        for (mode in ThemeMode.entries) {
            assertEquals(mode, themeModeFromSwitch(mode.isDark))
        }
    }
}
