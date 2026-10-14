package com.isra16.movienight

import com.isra16.movienight.net.GlassRect
import com.isra16.movienight.net.blurRectInPanel
import com.isra16.movienight.net.contrastOnGlass
import com.isra16.movienight.net.contrastRatio
import com.isra16.movienight.net.coverCropRect
import com.isra16.movienight.net.relativeLuminance
import com.isra16.movienight.net.veilAlphaAt
import com.isra16.movienight.net.visibleSourceFraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassLogicTest {

    private val eps = 1e-3f

    private fun assertRect(msg: String, expected: GlassRect, actual: GlassRect) {
        assertEquals("$msg left", expected.left, actual.left, eps)
        assertEquals("$msg top", expected.top, actual.top, eps)
        assertEquals("$msg width", expected.width, actual.width, eps)
        assertEquals("$msg height", expected.height, actual.height, eps)
    }

    // --- coverCropRect ---------------------------------------------------------------------------------------

    @Test
    fun `misma proporcion cubre justo la ventana sin recorte`() {
        assertRect("1:1 exacto", GlassRect(0f, 0f, 360f, 640f), coverCropRect(720, 1280, 360f, 640f))
    }

    @Test
    fun `ventana mas alta que la imagen recorta los lados y centra`() {
        // Imagen 100x100 en ventana 100x200: escala 2 -> 200x200, sobra 100 de ancho, 50 a cada lado.
        assertRect("más alta", GlassRect(-50f, 0f, 200f, 200f), coverCropRect(100, 100, 100f, 200f))
    }

    @Test
    fun `ventana mas ancha que la imagen recorta arriba y abajo y centra`() {
        // Imagen 100x200 en ventana 200x100: escala 2 -> 200x400, sobran 300 de alto, 150 arriba y 150 abajo.
        assertRect("más ancha", GlassRect(0f, -150f, 200f, 400f), coverCropRect(100, 200, 200f, 100f))
    }

    @Test
    fun `el rectangulo siempre cubre la ventana entera`() {
        val sizes = listOf(360f to 640f, 360f to 840f, 600f to 960f, 640f to 360f, 800f to 360f, 1080f to 2400f)
        for ((w, h) in sizes) {
            val r = coverCropRect(736, 1308, w, h)
            assertTrue("izquierda $w x $h", r.left <= eps)
            assertTrue("arriba $w x $h", r.top <= eps)
            assertTrue("derecha $w x $h", r.left + r.width >= w - eps)
            assertTrue("abajo $w x $h", r.top + r.height >= h - eps)
            assertEquals("proporción $w x $h", 736f / 1308f, r.width / r.height, 1e-4f)
        }
    }

    @Test
    fun `tamanos invalidos dan un rectangulo vacio`() {
        val empty = GlassRect(0f, 0f, 0f, 0f)
        assertRect("src 0", empty, coverCropRect(0, 100, 100f, 100f))
        assertRect("dst 0", empty, coverCropRect(100, 100, 0f, 100f))
        assertRect("negativo", empty, coverCropRect(100, 100, -5f, 100f))
    }

    // --- veilAlphaAt ---------------------------------------------------------------------------------------------

    @Test
    fun `el velo vale lo de cada parada en su posicion`() {
        val stops = listOf(0.2f, 0.6f, 0.4f, 0.0f, 1.0f)
        for ((i, v) in stops.withIndex()) {
            assertEquals("parada $i", v, veilAlphaAt(stops, i / 4f), 1e-6f)
        }
    }

    @Test
    fun `entre dos paradas el velo se interpola en linea recta`() {
        val stops = listOf(0.2f, 0.6f, 0.4f)
        assertEquals(0.4f, veilAlphaAt(stops, 0.25f), 1e-6f) // mitad entre 0,2 y 0,6
        assertEquals(0.5f, veilAlphaAt(stops, 0.75f), 1e-6f) // mitad entre 0,6 y 0,4
        assertEquals(0.3f, veilAlphaAt(stops, 0.125f), 1e-6f)
    }

    @Test
    fun `fuera de 0 a 1 el velo se queda en los extremos`() {
        val stops = listOf(0.3f, 0.5f, 0.7f)
        assertEquals(0.3f, veilAlphaAt(stops, -2f), 1e-6f)
        assertEquals(0.7f, veilAlphaAt(stops, 9f), 1e-6f)
    }

    @Test
    fun `lista vacia o de una sola parada`() {
        assertEquals(0f, veilAlphaAt(emptyList(), 0.5f), 0f)
        assertEquals(0.4f, veilAlphaAt(listOf(0.4f), 0.9f), 0f)
    }

    @Test
    fun `con las paradas medidas el velo nunca baja del minimo que pide cada franja`() {
        // Propiedad que usa backdrop_tools.py: cada parada es el maximo de las franjas vecinas, asi que entre paradas la
        // interpolacion no queda por debajo de lo que pide ninguna franja. Aqui, con unas necesidades inventadas.
        val need = listOf(0f, 0.5f, 0f, 0f, 0.4f, 0f, 0f, 0.1f) // 8 franjas (de 1/8 de alto)
        val steps = 4 // 5 paradas, cada una cubre las franjas que toca [(k-1)/4, (k+1)/4]
        val stops = (0..steps).map { k ->
            val lo = maxOf(0, 2 * (k - 1))
            val hi = minOf(need.size, 2 * (k + 1))
            need.subList(lo, hi).max()
        }
        for ((band, n) in need.withIndex()) {
            for (t in listOf(0.0f, 0.25f, 0.5f, 0.75f, 1.0f)) {
                val y = (band + t) / need.size
                assertTrue("franja $band, t=$t: ${veilAlphaAt(stops, y)} >= $n", veilAlphaAt(stops, y) >= n - 1e-6f)
            }
        }
    }

    // --- visibleSourceFraction -----------------------------------------------------------------------------------

    @Test
    fun `en vertical con la proporcion de la imagen se ve todo`() {
        val f = visibleSourceFraction(720, 1280, 360f, 640f)
        assertEquals(1f, f.width, eps)
        assertEquals(1f, f.height, eps)
        assertEquals(0f, f.left, eps)
        assertEquals(0f, f.top, eps)
    }

    @Test
    fun `en un telefono mas alargado se pierden los lados`() {
        val f = visibleSourceFraction(736, 1308, 360f, 840f) // 9:21
        assertEquals(1f, f.height, eps)
        assertEquals(0.762f, f.width, 2e-3f)
        assertEquals((1f - f.width) / 2f, f.left, eps)
    }

    @Test
    fun `en horizontal solo se ve una franja centrada del alto`() {
        val f = visibleSourceFraction(736, 1308, 640f, 360f) // 16:9
        assertEquals(1f, f.width, eps)
        assertEquals(0.317f, f.height, 2e-3f)
        assertEquals(0.342f, f.top, 2e-3f)
        val g = visibleSourceFraction(736, 1308, 800f, 360f) // 20:9
        assertEquals(0.253f, g.height, 2e-3f)
        assertEquals(0.373f, g.top, 2e-3f)
    }

    // --- contraste -----------------------------------------------------------------------------------------------

    @Test
    fun `luminancia de los extremos`() {
        assertEquals(0.0, relativeLuminance(0xFF000000.toInt()), 1e-9)
        assertEquals(1.0, relativeLuminance(0xFFFFFFFF.toInt()), 1e-9)
        assertEquals(0.2159, relativeLuminance(0xFF808080.toInt()), 1e-4)
    }

    @Test
    fun `el alfa no cuenta en la luminancia`() {
        assertEquals(relativeLuminance(0xFF3A6B2C.toInt()), relativeLuminance(0x203A6B2C), 1e-12)
    }

    @Test
    fun `contraste de referencia WCAG`() {
        assertEquals(21.0, contrastRatio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 1e-9)
        assertEquals(1.0, contrastRatio(0xFF123456.toInt(), 0xFF123456.toInt()), 1e-9)
        // #767676 sobre blanco es el gris más claro que llega a 4,5:1 (da 4,54).
        assertEquals(4.542, contrastRatio(0xFF767676.toInt(), 0xFFFFFFFF.toInt()), 1e-3)
    }

    @Test
    fun `el contraste no depende del orden de los colores`() {
        assertEquals(
            contrastRatio(0xFF1B6B3F.toInt(), 0xFFF5FBF5.toInt()),
            contrastRatio(0xFFF5FBF5.toInt(), 0xFF1B6B3F.toInt()),
            1e-12,
        )
    }

    @Test
    fun `sobre vidrio sin velo ni tinte es el contraste del texto con la imagen`() {
        val text = 0xFFDEE4DE.toInt()
        val img = 0xFF3A6B2C.toInt()
        assertEquals(contrastRatio(text, img), contrastOnGlass(text, img, 0xFF0E1511.toInt(), 0f, 0xFF0E1511.toInt(), 0f), 1e-9)
    }

    @Test
    fun `con tinte opaco solo importa el tinte`() {
        val text = 0xFFDEE4DE.toInt()
        val tint = 0xFF0E1511.toInt()
        val expected = contrastRatio(text, tint)
        assertEquals(expected, contrastOnGlass(text, 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0.3f, tint, 1f), 1e-9)
        assertEquals(expected, contrastOnGlass(text, 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.9f, tint, 1f), 1e-9)
    }

    @Test
    fun `valores cruzados con una cuenta aparte en Python`() {
        // Valores de referencia de la cuenta de capas (imagen -> velo -> tinte de la barra, 0 si no hay barra); NO son los
        // tokens actuales de Color.kt. Claro: texto onSurface sobre un pixel oscuro (tronco), velo 0,55 de LightBackground, sin barra.
        assertEquals(
            7.4968,
            contrastOnGlass(0xFF0B100C.toInt(), 0xFF4A3320.toInt(), 0xFFF5FBF5.toInt(), 0.55f, 0xFFF5FBF5.toInt(), 0f),
            2e-3,
        )
        // Claro: primary sobre un verde, velo 0,50, sin barra.
        assertEquals(
            5.1584,
            contrastOnGlass(0xFF053A1C.toInt(), 0xFF2E5A2A.toInt(), 0xFFF5FBF5.toInt(), 0.50f, 0xFFF5FBF5.toInt(), 0f),
            2e-3,
        )
        // Oscuro: onSurfaceVariant sobre un pixel claro, velo 0,35 y tinte de la barra 0,25 de DarkBackground.
        assertEquals(
            3.2506,
            contrastOnGlass(0xFFBFC9C1.toInt(), 0xFFB0C4DE.toInt(), 0xFF0E1511.toInt(), 0.35f, 0xFF0E1511.toInt(), 0.25f),
            2e-3,
        )
    }

    @Test
    fun `mas tinte nunca baja el contraste del texto en modo oscuro`() {
        val text = 0xFFBFC9C1.toInt()
        val bright = 0xFFB0C4DE.toInt() // un pixel claro de la copia borrosa
        var last = 0.0
        for (a in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val c = contrastOnGlass(text, bright, 0xFF0E1511.toInt(), 0.15f, 0xFF0E1511.toInt(), a)
            assertTrue("alfa $a: $c >= $last", c >= last - 1e-9)
            last = c
        }
    }

    // --- blurRectInPanel ---------------------------------------------------------------------------------------

    @Test
    fun `panel en el origen deja la copia borrosa donde esta el fondo`() {
        val bg = coverCropRect(736, 1308, 360f, 780f)
        assertRect("origen", bg, blurRectInPanel(bg, 0f, 0f))
    }

    @Test
    fun `la copia borrosa se traslada en sentido contrario al panel`() {
        val bg = GlassRect(-20f, -10f, 400f, 800f)
        assertRect("panel en (30, 100)", GlassRect(-50f, -110f, 400f, 800f), blurRectInPanel(bg, 30f, 100f))
    }

    @Test
    fun `alineacion al desplazar - un punto de la ventana cae en el mismo lugar visto por el panel o por el fondo`() {
        val bg = coverCropRect(736, 1308, 360f, 780f)
        // Punto de la ventana (x, y); el panel empieza en (24, y0). Dentro del panel ese punto está en (x - 24, y - y0).
        for (y0 in listOf(-300f, 0f, 150f, 420f)) {
            val inPanel = blurRectInPanel(bg, 24f, y0)
            for ((x, y) in listOf(100f to 200f, 300f to 500f, 24f to 0f)) {
                val u = (x - bg.left) / bg.width
                val v = (y - bg.top) / bg.height
                val xInPanel = inPanel.left + u * inPanel.width
                val yInPanel = inPanel.top + v * inPanel.height
                assertEquals("x con y0=$y0", x - 24f, xInPanel, eps)
                assertEquals("y con y0=$y0", y - y0, yInPanel, eps)
            }
        }
    }
}
