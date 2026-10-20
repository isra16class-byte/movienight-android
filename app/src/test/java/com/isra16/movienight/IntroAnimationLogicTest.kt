package com.isra16.movienight

import com.isra16.movienight.net.INTRO_CLOUDS_END
import com.isra16.movienight.net.INTRO_CLOUDS_START
import com.isra16.movienight.net.INTRO_CLOUD_LEFT_START_X
import com.isra16.movienight.net.INTRO_CLOUD_RIGHT_START_X
import com.isra16.movienight.net.INTRO_LOGO_FADE_END
import com.isra16.movienight.net.INTRO_LOGO_FADE_START
import com.isra16.movienight.net.INTRO_NAME_END
import com.isra16.movienight.net.INTRO_NAME_START
import com.isra16.movienight.net.INTRO_TAGLINE_END
import com.isra16.movienight.net.INTRO_TAGLINE_START
import com.isra16.movienight.net.IntroAdvance
import com.isra16.movienight.net.introAdvanceFor
import com.isra16.movienight.net.introCloudOffsets
import com.isra16.movienight.net.introStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroAnimationLogicTest {

    private val eps = 1e-6f

    /** 0, 0.001, … 1.0 (1001 pasos): suficiente para ver una bajada en cualquier tramo. */
    private val steps: List<Float> = (0..1000).map { it / 1000f }

    private fun all(a: IntroAdvance) = listOf(a.logo, a.clouds, a.name, a.tagline)

    @Test
    fun `introStage es 0 antes de start y 0 justo en start`() {
        assertEquals(0f, introStage(0.1f, 0.2f, 0.6f), 0f)
        assertEquals(0f, introStage(0.2f, 0.2f, 0.6f), 0f)
    }

    @Test
    fun `introStage es 1 justo en end y 1 despues de end`() {
        assertEquals(1f, introStage(0.6f, 0.2f, 0.6f), 0f)
        assertEquals(1f, introStage(0.9f, 0.2f, 0.6f), 0f)
    }

    @Test
    fun `introStage es lineal entre start y end`() {
        assertEquals(0.5f, introStage(0.4f, 0.2f, 0.6f), eps)
        assertEquals(0.25f, introStage(0.3f, 0.2f, 0.6f), eps)
        assertEquals(0.75f, introStage(0.5f, 0.2f, 0.6f), eps)
    }

    @Test
    fun `introStage recorta fuera de 0 a 1 aunque el progreso se salga`() {
        assertEquals(0f, introStage(-5f, 0f, 1f), 0f)
        assertEquals(1f, introStage(7f, 0f, 1f), 0f)
    }

    @Test
    fun `introStage con rango vacio es un escalon en start`() {
        assertEquals(0f, introStage(0.49f, 0.5f, 0.5f), 0f)
        assertEquals(1f, introStage(0.5f, 0.5f, 0.5f), 0f)
        assertEquals(1f, introStage(0.9f, 0.5f, 0.5f), 0f)
    }

    @Test
    fun `introStage con rango invertido es un escalon en start`() {
        assertEquals(0f, introStage(0.49f, 0.5f, 0.2f), 0f)
        assertEquals(1f, introStage(0.5f, 0.5f, 0.2f), 0f)
        assertEquals(1f, introStage(0.9f, 0.5f, 0.2f), 0f)
    }

    @Test
    fun `introStage con un progreso que no es numero da 0`() {
        assertEquals(0f, introStage(Float.NaN, 0.2f, 0.6f), 0f)
        assertEquals(0f, introStage(Float.NaN, 0.5f, 0.5f), 0f)
    }

    @Test
    fun `los intervalos son validos y estan dentro de 0 a 1`() {
        val ranges = listOf(
            INTRO_LOGO_FADE_START to INTRO_LOGO_FADE_END,
            INTRO_CLOUDS_START to INTRO_CLOUDS_END,
            INTRO_NAME_START to INTRO_NAME_END,
            INTRO_TAGLINE_START to INTRO_TAGLINE_END,
        )
        for ((s, e) in ranges) {
            assertTrue("start $s end $e", s >= 0f && e <= 1f && s < e)
        }
    }

    @Test
    fun `el escalonado va en el orden pedido`() {
        // El logo aparece primero; el nombre antes que el eslogan; el eslogan termina al final de todo.
        assertTrue(INTRO_LOGO_FADE_START <= INTRO_CLOUDS_START)
        assertTrue(INTRO_LOGO_FADE_END < INTRO_NAME_END)
        assertTrue(INTRO_NAME_START < INTRO_TAGLINE_START)
        assertTrue(INTRO_NAME_END <= INTRO_TAGLINE_END)
        assertEquals(1f, INTRO_TAGLINE_END, 0f)
        // Las nubes ocupan casi todo el movimiento.
        assertTrue(INTRO_CLOUDS_END - INTRO_CLOUDS_START >= 0.8f)
    }

    @Test
    fun `en progreso 0 nada ha avanzado y en 1 todo termino`() {
        assertEquals(listOf(0f, 0f, 0f, 0f), all(introAdvanceFor(0f)))
        assertEquals(listOf(1f, 1f, 1f, 1f), all(introAdvanceFor(1f)))
    }

    @Test
    fun `el avance de cada elemento es monotono`() {
        val parts = listOf<(IntroAdvance) -> Float>({ it.logo }, { it.clouds }, { it.name }, { it.tagline })
        for ((i, part) in parts.withIndex()) {
            var prev = -1f
            for (p in steps) {
                val v = part(introAdvanceFor(p))
                assertTrue("elemento $i en p=$p: $v < $prev", v >= prev)
                prev = v
            }
        }
    }

    @Test
    fun `ningun avance sale de 0 a 1 ni con progresos fuera de rango`() {
        val inputs = steps + listOf(-1f, -0.001f, 1.001f, 2f, 1e9f, -1e9f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        for (p in inputs) {
            for (v in all(introAdvanceFor(p))) {
                assertTrue("p=$p v=$v", v in 0f..1f)
            }
        }
    }

    @Test
    fun `en progreso 0 los desplazamientos son los iniciales`() {
        val o = introCloudOffsets(introAdvanceFor(0f).clouds)
        assertEquals(INTRO_CLOUD_LEFT_START_X, o.left, 0f)
        assertEquals(INTRO_CLOUD_RIGHT_START_X, o.right, 0f)
    }

    @Test
    fun `en progreso 1 los desplazamientos son exactamente 0 (y 0 positivo)`() {
        val o = introCloudOffsets(introAdvanceFor(1f).clouds)
        assertEquals(0, o.left.toRawBits())
        assertEquals(0, o.right.toRawBits())
    }

    @Test
    fun `los desplazamientos siguen inicial por uno menos el avance`() {
        for (a in listOf(0.1f, 0.25f, 0.5f, 0.9f)) {
            val o = introCloudOffsets(a)
            assertEquals(INTRO_CLOUD_LEFT_START_X * (1f - a), o.left, eps)
            assertEquals(INTRO_CLOUD_RIGHT_START_X * (1f - a), o.right, eps)
        }
    }

    @Test
    fun `las nubes se abren hacia afuera y se acercan a su sitio sin pasarse`() {
        assertTrue(INTRO_CLOUD_LEFT_START_X > 0f && INTRO_CLOUD_RIGHT_START_X < 0f)
        var prevL = Float.MAX_VALUE
        var prevR = -Float.MAX_VALUE
        for (p in steps) {
            val o = introCloudOffsets(introAdvanceFor(p).clouds)
            assertTrue("izq p=$p", o.left <= prevL && o.left >= 0f && o.left <= INTRO_CLOUD_LEFT_START_X)
            assertTrue("der p=$p", o.right >= prevR && o.right <= 0f && o.right >= INTRO_CLOUD_RIGHT_START_X)
            prevL = o.left
            prevR = o.right
        }
    }

    @Test
    fun `un avance de nubes fuera de rango o no numerico se recorta`() {
        val start = introCloudOffsets(0f)
        assertEquals(start, introCloudOffsets(-3f))
        assertEquals(start, introCloudOffsets(Float.NaN))
        val end = introCloudOffsets(1f)
        assertEquals(end, introCloudOffsets(4f))
        assertEquals(end, introCloudOffsets(Float.POSITIVE_INFINITY))
    }
}
