package com.isra16.movienight

import com.isra16.movienight.net.UPLOAD_PROGRESS_TITLE
import com.isra16.movienight.net.UploadActivityTracker
import com.isra16.movienight.net.UploadGoal
import com.isra16.movienight.net.UploadState
import com.isra16.movienight.net.progressNoticeFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadServiceLogicTest {

    @Test
    fun soloLoQueEstaEnMarchaNecesitaElServicio() {
        assertNull(progressNoticeFor(UploadState.Idle))
        assertNull(progressNoticeFor(UploadState.Done("a.mp4")))
        assertNull(progressNoticeFor(UploadState.Failed("a.mp4", "Se cortó.", true)))
    }

    @Test
    fun subiendoMuestraElPorcentajeEntero() {
        val n = progressNoticeFor(UploadState.Uploading("peli.mp4", 420, 1000))!!
        assertEquals(UPLOAD_PROGRESS_TITLE, n.title)
        assertEquals("peli.mp4 · 42 %", n.text)
        assertEquals(42, n.percent)
    }

    @Test
    fun elAvanceNoCambiaMientrasNoCambieElPorcentaje() {
        // Misma notificación para 42,0 % y 42,9 %: no se actualiza por cada byte.
        assertEquals(
            progressNoticeFor(UploadState.Uploading("peli.mp4", 420, 1000)),
            progressNoticeFor(UploadState.Uploading("peli.mp4", 429, 1000)),
        )
    }

    @Test
    fun alPrincipioYAlFinal() {
        assertEquals(0, progressNoticeFor(UploadState.Uploading("a.mp4", 0, 1000))!!.percent)
        assertEquals(100, progressNoticeFor(UploadState.Uploading("a.mp4", 1000, 1000))!!.percent)
        // Un archivo de tamaño 0 no rompe la cuenta.
        assertEquals(0, progressNoticeFor(UploadState.Uploading("a.mp4", 0, 0))!!.percent)
    }

    @Test
    fun terminandoMuestraLaBarraSinPorcentaje() {
        val n = progressNoticeFor(UploadState.Finishing("a.mp4", UploadGoal.CREATE_ROOM))!!
        assertEquals("Creando la sala…", n.text)
        assertNull(n.percent)
    }

    @Test
    fun unaSubidaActivaYLuegoTerminada() {
        val t = UploadActivityTracker()
        val key = Any()
        assertFalse(t.isActive)
        assertNull(t.currentNotice())
        t.update(key, UploadState.Uploading("a.mp4", 10, 100))
        assertTrue(t.isActive)
        assertEquals(10, t.currentNotice()!!.percent)
        t.update(key, UploadState.Done("a.mp4"))
        assertFalse(t.isActive)
        assertNull(t.currentNotice())
    }

    @Test
    fun fallarOCancelarDejanDeNecesitarElServicio() {
        val t = UploadActivityTracker()
        val key = Any()
        t.update(key, UploadState.Uploading("a.mp4", 10, 100))
        t.update(key, UploadState.Failed("a.mp4", "Se cortó.", true))
        assertFalse(t.isActive)
        t.update(key, UploadState.Uploading("a.mp4", 10, 100))
        t.update(key, UploadState.Idle) // cancelar, o cerrarse la pantalla dueña
        assertFalse(t.isActive)
    }

    @Test
    fun conDosSubidasElServicioSigueHastaQueTerminenAmbas() {
        val t = UploadActivityTracker()
        val home = Any()
        val room = Any()
        t.update(home, UploadState.Uploading("a.mp4", 10, 100))
        t.update(room, UploadState.Uploading("b.mp4", 50, 100))
        assertTrue(t.isActive)
        assertEquals("2 subidas en curso", t.currentNotice()!!.text)
        t.update(home, UploadState.Done("a.mp4"))
        assertTrue(t.isActive)
        assertEquals(50, t.currentNotice()!!.percent)
        t.update(room, UploadState.Idle)
        assertFalse(t.isActive)
    }

    @Test
    fun prepararYaActivaElServicioSinPorcentaje() {
        val n = progressNoticeFor(UploadState.Preparing(""))!!
        assertEquals("Preparando la subida…", n.text)
        assertNull(n.percent)
        val t = UploadActivityTracker()
        val key = Any()
        t.update(key, UploadState.Preparing("a.mp4"))
        assertTrue(t.isActive)
    }

    @Test
    fun unArchivoInvalidoQuedaInactivoApenasFalla() {
        val t = UploadActivityTracker()
        val key = Any()
        t.update(key, UploadState.Preparing(""))
        t.update(key, UploadState.Failed("a.xyz", "Formato no permitido.", false))
        assertFalse(t.isActive)
    }

    @Test
    fun unReintentoVuelveAActivarElServicio() {
        val t = UploadActivityTracker()
        val key = Any()
        t.update(key, UploadState.Uploading("a.mp4", 10, 100))
        t.update(key, UploadState.Failed("a.mp4", "Se cortó.", true))
        assertFalse(t.isActive)
        t.update(key, UploadState.Preparing("a.mp4"))
        assertTrue(t.isActive)
    }
}
