package com.isra16.movienight

import com.isra16.movienight.net.INTRO_CENTER_X_UNITS
import com.isra16.movienight.net.INTRO_EXIT_ZOOM
import com.isra16.movienight.net.INTRO_EXTRA_CLOUDS
import com.isra16.movienight.net.INTRO_LOGO_MAX_HEIGHT_FRACTION
import com.isra16.movienight.net.INTRO_LOGO_UNITS
import com.isra16.movienight.net.INTRO_NAME_MAX_FONT_SCALE
import com.isra16.movienight.net.INTRO_NAME_TOP_UNITS
import com.isra16.movienight.net.introExtraCloudDx
import com.isra16.movienight.net.introExtraCloudDy
import com.isra16.movienight.net.introExtraTravelUnits
import com.isra16.movienight.net.introLogoSize
import com.isra16.movienight.net.introLogoTop
import com.isra16.movienight.net.introTextReserve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroSceneLogicTest {

    private val eps = 1e-3f
    private val steps: List<Float> = (0..100).map { it / 100f }

    @Test
    fun `hay diez nubes extra, alternan lado y bajan por la pantalla`() {
        assertEquals(10, INTRO_EXTRA_CLOUDS.size)
        INTRO_EXTRA_CLOUDS.forEachIndexed { i, c ->
            assertEquals("lado de la $i", i % 2 == 0, c.onLeft)
            assertTrue("y de la $i", c.yFraction > 0f && c.yFraction < 1f)
            assertTrue("escala de la $i", c.scale in 1f..2.5f)
            if (i > 0) assertTrue("orden en y de la $i", c.yFraction > INTRO_EXTRA_CLOUDS[i - 1].yFraction)
        }
    }

    @Test
    fun `al inicio las nubes extra estan juntas en el centro de la pantalla`() {
        for (c in INTRO_EXTRA_CLOUDS) {
            val center = c.slotCenterX + introExtraCloudDx(c, 0f, 200f)
            assertTrue("centro ${center} de $c", kotlin.math.abs(center - INTRO_CENTER_X_UNITS) <= 16f)
        }
    }

    @Test
    fun `las nubes extra salen hacia su lado, de forma monotona`() {
        for (c in INTRO_EXTRA_CLOUDS) {
            var prev = introExtraCloudDx(c, 0f, 150f)
            for (a in steps) {
                val dx = introExtraCloudDx(c, a, 150f)
                assertTrue("$c a=$a", if (c.onLeft) dx <= prev else dx >= prev)
                prev = dx
            }
        }
    }

    @Test
    fun `al final todas las nubes extra quedan fuera de la pantalla, en cualquier ancho`() {
        for (visibleWidth in listOf(108f, 140f, 180f, 302f, 400f)) {
            val travel = introExtraTravelUnits(visibleWidth)
            val left = INTRO_CENTER_X_UNITS - visibleWidth / 2f
            val right = INTRO_CENTER_X_UNITS + visibleWidth / 2f
            for (c in INTRO_EXTRA_CLOUDS) {
                val center = c.slotCenterX + introExtraCloudDx(c, 1f, travel)
                if (c.onLeft) assertTrue("$c w=$visibleWidth", center + c.halfWidth <= left)
                else assertTrue("$c w=$visibleWidth", center - c.halfWidth >= right)
            }
        }
    }

    @Test
    fun `un avance fuera de rango o no numerico se recorta`() {
        val c = INTRO_EXTRA_CLOUDS[0]
        assertEquals(introExtraCloudDx(c, 0f, 100f), introExtraCloudDx(c, -2f, 100f), 0f)
        assertEquals(introExtraCloudDx(c, 0f, 100f), introExtraCloudDx(c, Float.NaN, 100f), 0f)
        assertEquals(introExtraCloudDx(c, 1f, 100f), introExtraCloudDx(c, 5f, 100f), 0f)
    }

    @Test
    fun `el recorrido crece con el ancho de la pantalla`() {
        assertTrue(introExtraTravelUnits(300f) > introExtraTravelUnits(108f))
    }

    @Test
    fun `el desplazamiento en y deja el centro de la nube a su fraccion de la altura`() {
        val screenH = 2000f
        val originY = 380f
        val unit = 3.6f
        for (c in INTRO_EXTRA_CLOUDS) {
            val dy = introExtraCloudDy(c, screenH, originY, unit)
            val centerPx = originY + (c.slotCenterY + dy) * unit
            assertEquals(c.yFraction * screenH, centerPx, eps)
        }
    }

    @Test
    fun `el logo no pasa del ancho ni de la mitad de la altura`() {
        for ((w, h) in listOf(360f to 800f, 412f to 915f, 360f to 640f, 800f to 360f, 915f to 412f, 200f to 100f)) {
            val s = introLogoSize(w, h)
            assertTrue("$w x $h -> $s", s <= w && s <= h * INTRO_LOGO_MAX_HEIGHT_FRACTION + 1e-4f && s > 0f)
        }
        assertEquals(360f, introLogoSize(360f, 800f), 0f)
        assertEquals(180f, introLogoSize(800f, 360f), 0f)
        assertEquals(0f, introLogoSize(-5f, 100f), 0f)
    }

    @Test
    fun `la reserva de texto crece con la letra y el nombre deja de crecer a su tope`() {
        assertTrue(introTextReserve(2f) > introTextReserve(1.3f))
        assertTrue(introTextReserve(1.3f) > introTextReserve(1f))
        assertEquals(introTextReserve(1f), introTextReserve(0.5f), 0f)
        assertEquals(introTextReserve(1f), introTextReserve(Float.NaN), 0f)
        // Entre 1,3 y 2 solo crece el eslogan (48 dp por unidad de escala), no el nombre.
        assertEquals(48f * 0.7f, introTextReserve(2f) - introTextReserve(INTRO_NAME_MAX_FONT_SCALE), eps)
    }

    @Test
    fun `el conjunto queda centrado, nunca arriba de 0 y cabe cuando hay altura`() {
        for ((w, h) in listOf(360f to 800f, 412f to 915f, 360f to 640f, 800f to 360f)) {
            for (fs in listOf(1f, 1.3f, 2f)) {
                val size = introLogoSize(w, h)
                val reserve = introTextReserve(fs)
                val top = introLogoTop(h, size, reserve)
                assertTrue(top >= 0f)
                val content = size * (INTRO_NAME_TOP_UNITS / INTRO_LOGO_UNITS) + reserve
                if (content <= h) {
                    assertEquals("centrado $w x $h fs $fs", h - (top + content), top, eps)
                    assertTrue("cabe $w x $h fs $fs", top + content <= h + eps)
                }
            }
        }
        assertEquals(0f, introLogoTop(100f, 80f, 500f), 0f)
    }

    @Test
    fun `el acercamiento final es leve`() {
        assertTrue(INTRO_EXIT_ZOOM > 1f && INTRO_EXIT_ZOOM <= 1.1f)
    }
}
