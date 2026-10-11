package com.isra16.movienight

import com.isra16.movienight.net.CINEMA_ASPECT
import com.isra16.movienight.net.VideoFit
import com.isra16.movienight.net.videoFitButtonLabel
import com.isra16.movienight.net.videoFitName
import com.isra16.movienight.net.videoScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFitLogicTest {

    // Pantallas (ventana en horizontal, en px) y videos (en px) de ejemplo. No son "el teléfono de alguien": son
    // proporciones distintas a propósito para comprobar que los niveles no dependen de un solo aparato.
    private val phone20x9 = 2400f to 1080f // 2,22:1
    private val phone16x9 = 1920f to 1080f // 1,78:1
    private val tablet16x10 = 1280f to 800f // 1,6:1
    private val tablet4x3 = 1024f to 768f // 1,33:1
    private val foldableSquare = 1000f to 1000f // 1:1
    private val screens = listOf(phone20x9, phone16x9, tablet16x10, tablet4x3, foldableSquare)

    private val video16x9 = 1920f to 1080f
    private val video4x3 = 1440f to 1080f
    private val videoScope = 1912f to 800f // 2,39:1 exacto, ya recortado (sin franjas incrustadas)
    private val video185 = 1998f to 1080f // 1,85:1
    private val videoUhd = 3840f to 2160f
    private val videoVertical = 1080f to 1920f // 9:16
    private val videos = listOf(video16x9, video4x3, videoScope, video185, videoUhd, videoVertical)

    private fun scale(fit: VideoFit, screen: Pair<Float, Float>, video: Pair<Float, Float>) =
        videoScale(fit, screen.first, screen.second, video.first, video.second)!!

    /** Fracción del video que se ve en cada eje (1 = entero, menos = recortado). */
    private fun visible(fit: VideoFit, screen: Pair<Float, Float>, video: Pair<Float, Float>): Pair<Float, Float> {
        val s = scale(fit, screen, video)
        return minOf(1f, screen.first / (video.first * s)) to minOf(1f, screen.second / (video.second * s))
    }

    @Test
    fun `la rueda de niveles da la vuelta completa`() {
        assertEquals(VideoFit.MEDIUM, VideoFit.FIT.next())
        assertEquals(VideoFit.FILL, VideoFit.MEDIUM.next())
        assertEquals(VideoFit.CINEMA, VideoFit.FILL.next())
        assertEquals(VideoFit.FIT, VideoFit.CINEMA.next())
        assertEquals(4, VideoFit.entries.size)
    }

    @Test
    fun `solo medio y cine usan tamano propio`() {
        assertEquals(listOf(false, true, false, true), VideoFit.entries.map { it.usesCustomScale })
    }

    @Test
    fun `los textos`() {
        assertEquals(listOf("Entero", "Medio", "Llenar", "Cine"), VideoFit.entries.map { videoFitName(it) })
        assertEquals("Ajuste del video: Entero. Toca para pasar a Medio", videoFitButtonLabel(VideoFit.FIT))
        assertEquals("Ajuste del video: Cine. Toca para pasar a Entero", videoFitButtonLabel(VideoFit.CINEMA))
    }

    @Test
    fun `tamanos inutilizables no dan escala`() {
        assertNull(videoScale(VideoFit.MEDIUM, 2400f, 1080f, 0f, 0f))
        assertNull(videoScale(VideoFit.CINEMA, 0f, 1080f, 1920f, 1080f))
        assertNull(videoScale(VideoFit.FIT, 2400f, -1f, 1920f, 1080f))
        assertNull(videoScale(VideoFit.FILL, Float.NaN, 1080f, 1920f, 1080f))
        assertNull(videoScale(VideoFit.FIT, 2400f, 1080f, Float.POSITIVE_INFINITY, 1080f))
    }

    @Test
    fun `entero nunca recorta y llenar y cine siempre cubren la ventana, en cualquier pantalla y video`() {
        for (screen in screens) for (video in videos) {
            val (fw, fh) = visible(VideoFit.FIT, screen, video)
            assertTrue("FIT entero $screen $video", fw == 1f || fh == 1f)
            val fit = scale(VideoFit.FIT, screen, video)
            assertTrue("FIT cabe $screen $video", video.first * fit <= screen.first + 0.01f && video.second * fit <= screen.second + 0.01f)
            for (mode in listOf(VideoFit.FILL, VideoFit.CINEMA)) {
                val s = scale(mode, screen, video)
                assertTrue("$mode cubre el ancho $screen $video", video.first * s >= screen.first - 0.01f)
                assertTrue("$mode cubre el alto $screen $video", video.second * s >= screen.second - 0.01f)
            }
        }
    }

    @Test
    fun `los niveles van de menos a mas zoom y medio queda entre entero y llenar`() {
        for (screen in screens) for (video in videos) {
            val fit = scale(VideoFit.FIT, screen, video)
            val medium = scale(VideoFit.MEDIUM, screen, video)
            val fill = scale(VideoFit.FILL, screen, video)
            val cinema = scale(VideoFit.CINEMA, screen, video)
            assertTrue("fit<=medio $screen $video", fit <= medium + 1e-6f)
            assertTrue("medio<=llenar $screen $video", medium <= fill + 1e-6f)
            assertTrue("llenar<=cine $screen $video", fill <= cinema + 1e-6f)
        }
    }

    @Test
    fun `si el video ya tiene la proporcion de la ventana todos los niveles dan lo mismo salvo cine`() {
        val a = scale(VideoFit.FIT, phone16x9, video16x9)
        assertEquals(1f, a, 1e-6f)
        assertEquals(a, scale(VideoFit.MEDIUM, phone16x9, video16x9), 1e-6f)
        assertEquals(a, scale(VideoFit.FILL, phone16x9, video16x9), 1e-6f)
        // Cine sí agranda: trata el cuadro como 2,39:1 aunque el video no tenga franjas.
        assertEquals(1080f * CINEMA_ASPECT / 1920f, scale(VideoFit.CINEMA, phone16x9, video16x9), 1e-5f)
    }

    // --- Los números que se dieron al proponer los niveles (pantalla de unos 2,22:1) ---

    @Test
    fun `16 9 en pantalla 20 9 llenar recorta el 20 por ciento de alto y entero usa el 80 por ciento del ancho`() {
        val (fitW, fitH) = visible(VideoFit.FIT, phone20x9, video16x9)
        assertEquals(1f, fitH, 1e-4f)
        // El video entero ocupa 1,78/2,22 = 80 % del ancho de la ventana.
        assertEquals(0.8f, video16x9.first * scale(VideoFit.FIT, phone20x9, video16x9) / phone20x9.first, 1e-3f)
        assertEquals(1f, fitW, 1e-4f)
        val (fillW, fillH) = visible(VideoFit.FILL, phone20x9, video16x9)
        assertEquals(1f, fillW, 1e-4f)
        assertEquals(0.8f, fillH, 1e-3f) // se ve el 80 % del alto: recorta el 20 %
    }

    @Test
    fun `4 3 en pantalla 20 9 llenar recorta el 40 por ciento y medio recorta menos`() {
        val fillH = visible(VideoFit.FILL, phone20x9, video4x3).second
        assertEquals(0.6f, fillH, 1e-2f)
        val mediumH = visible(VideoFit.MEDIUM, phone20x9, video4x3).second
        assertTrue("medio recorta menos que llenar", mediumH > fillH)
        assertTrue("medio recorta algo", mediumH < 1f)
        // Y deja menos franjas a los lados que entero.
        val fitUsed = video4x3.first * scale(VideoFit.FIT, phone20x9, video4x3) / phone20x9.first
        val mediumUsed = minOf(1f, video4x3.first * scale(VideoFit.MEDIUM, phone20x9, video4x3) / phone20x9.first)
        assertTrue(mediumUsed > fitUsed)
    }

    @Test
    fun `pelicula 2 39 ya recortada en pantalla 20 9 llenar recorta el 7 por ciento de ancho y cine da lo mismo que llenar`() {
        // Una película de 2,35:1 (1920 x 817) queda a menos del 2 % de diferencia entre cine y llenar: no hay saltos bruscos.
        val near = videoScale(VideoFit.CINEMA, 2400f, 1080f, 1920f, 817f)!! / videoScale(VideoFit.FILL, 2400f, 1080f, 1920f, 817f)!!
        assertTrue("cine continuo cerca de 2,39", near in 1f..1.02f)
        val fillW = visible(VideoFit.FILL, phone20x9, videoScope).first
        assertEquals(0.93f, fillW, 1e-2f)
        assertEquals(scale(VideoFit.FILL, phone20x9, videoScope), scale(VideoFit.CINEMA, phone20x9, videoScope), 1e-6f)
    }

    @Test
    fun `16 9 con franjas de cine incrustadas en pantalla 20 9 cine las quita y llenar deja el 3 5 por ciento`() {
        // Cuadro de 1920 x 1080 con la película en el centro (1920 / 2,39 = 803 px de alto): 138,5 px de franja arriba y abajo.
        val contentH = 1920f / CINEMA_ASPECT
        val bar = (1080f - contentH) / 2f
        // Llenar: se ve 864 de 1080 px de alto, así que de cada franja quedan (864 - 803) / 2 = 30 px.
        val fillVisibleH = 1080f * visible(VideoFit.FILL, phone20x9, video16x9).second
        val remaining = (fillVisibleH - contentH) / 2f
        assertEquals(30.5f, remaining, 1f)
        assertTrue(remaining < bar)
        assertEquals(0.035f, remaining / fillVisibleH, 5e-3f)
        // Cine: lo que se ve es exactamente el cuadro útil: no queda franja incrustada.
        val cinemaVisibleH = 1080f * visible(VideoFit.CINEMA, phone20x9, video16x9).second
        assertEquals(contentH, cinemaVisibleH, 1f)
    }

    @Test
    fun `en una pantalla 16 9 cine recorta mucho de los lados y llenar no recorta un 16 9`() {
        assertEquals(1f, visible(VideoFit.FILL, phone16x9, video16x9).first, 1e-4f)
        // Cuadro 2,39:1 en una ventana 1,78:1: se ve 1,78/2,39 = 74 % del ancho de ese cuadro.
        assertEquals(0.745f, visible(VideoFit.CINEMA, phone16x9, 1920f to 803f).first, 1e-2f)
    }

    @Test
    fun `video vertical 9 16 los niveles no rompen nada`() {
        for (fit in VideoFit.entries) {
            val s = scale(fit, phone20x9, videoVertical)
            assertTrue("$fit positivo", s > 0f && s.isFinite())
        }
    }

    @Test
    fun `da igual la resolucion, solo cuenta la proporcion`() {
        for (fit in VideoFit.entries) {
            val hd = scale(fit, phone20x9, video16x9)
            val uhd = scale(fit, phone20x9, videoUhd)
            // En UHD cada píxel del video es la mitad de grande en pantalla; el video dibujado mide lo mismo.
            assertEquals(video16x9.first * hd, videoUhd.first * uhd, 0.5f)
            assertEquals(video16x9.second * hd, videoUhd.second * uhd, 0.5f)
        }
    }
}
