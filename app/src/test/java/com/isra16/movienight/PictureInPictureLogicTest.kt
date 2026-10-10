package com.isra16.movienight

import com.isra16.movienight.net.PIP_DEFAULT_ASPECT
import com.isra16.movienight.net.PauseOnStopTracker
import com.isra16.movienight.net.PipAspect
import com.isra16.movienight.net.PipConditions
import com.isra16.movienight.net.PipContent
import com.isra16.movienight.net.PipRequest
import com.isra16.movienight.net.pipAspectFor
import com.isra16.movienight.net.pipContentFor
import com.isra16.movienight.net.shouldEnterPip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureInPictureLogicTest {

    private val playing = PipConditions(inRoom = true, videoLoaded = true, videoFailed = false, playing = true)

    // --- cuándo entrar -------------------------------------------------------------------------

    @Test
    fun entraSoloConLaSalaAbiertaYElVideoReproduciendose() {
        assertTrue(shouldEnterPip(playing, available = true))
    }

    @Test
    fun noEntraSiFaltaAlgunaCondicion() {
        assertFalse("fuera de la sala", shouldEnterPip(playing.copy(inRoom = false), available = true))
        assertFalse("sin video cargado", shouldEnterPip(playing.copy(videoLoaded = false), available = true))
        assertFalse("video con error", shouldEnterPip(playing.copy(videoFailed = true), available = true))
        assertFalse("en pausa", shouldEnterPip(playing.copy(playing = false), available = true))
    }

    @Test
    fun laPantallaPrincipalElLoginYLasSalasConErrorNuncaEntran() {
        // PipRequest.OFF es lo que se manda al salir de la sala o al no estar en ella.
        assertFalse(shouldEnterPip(PipRequest.OFF.conditions, available = true))
    }

    @Test
    fun sinPipDisponibleNoEntraAunqueTodoEsteBien() {
        assertFalse(shouldEnterPip(playing, available = false))
    }

    @Test
    fun sinPipDisponibleLaAppSeComportaComoHoyYPausaAlDejarDeVerse() {
        // No se entra en PiP, así que al detenerse la actividad no está en PiP y se pausa, sin dejar nada pendiente.
        assertFalse(shouldEnterPip(playing, available = false))
        val tracker = PauseOnStopTracker()
        assertTrue(tracker.onStopped(changingConfigurations = false, inPipNow = false, screenOn = true))
        // Y al volver a verse no queda nada raro: un cierre posterior se decide igual.
        tracker.onStarted()
        assertTrue(tracker.onStopped(changingConfigurations = false, inPipNow = false, screenOn = true))
    }

    @Test
    fun siEntrarFallaLaActividadSigueSinPiPYSePausaAlDetenerse() {
        // Si enterPictureInPictureMode falla (excepción o false) no llega ningún cambio de modo: solo el onStop.
        val tracker = PauseOnStopTracker()
        assertTrue(tracker.onStopped(changingConfigurations = false, inPipNow = false, screenOn = true))
    }

    // --- relación de aspecto -------------------------------------------------------------------

    @Test
    fun sinTamanoConocidoSeUsa16a9() {
        assertEquals(PIP_DEFAULT_ASPECT, pipAspectFor(0, 0))
        assertEquals(PipAspect(16, 9), pipAspectFor(0, 1080))
        assertEquals(PipAspect(16, 9), pipAspectFor(1920, 0))
        assertEquals(PipAspect(16, 9), pipAspectFor(-1, -1))
    }

    @Test
    fun conTamanoConocidoSeUsaElDelVideo() {
        assertEquals(PipAspect(1920, 1080), pipAspectFor(1920, 1080))
        assertEquals(PipAspect(1080, 1920), pipAspectFor(1080, 1920)) // vertical
        assertEquals(PipAspect(640, 480), pipAspectFor(640, 480))
    }

    @Test
    fun loQueAndroidRechazaSeAcotaDentroDeLosLimites() {
        // Panorámico extremo (más de 2,39:1) y vertical extremo (menos de 1:2,39).
        assertEquals(PipAspect(238, 100), pipAspectFor(3000, 1000))
        assertEquals(PipAspect(100, 238), pipAspectFor(1000, 3000))
        // Justo el límite del cine (2,39:1) todavía se respeta tal cual.
        assertEquals(PipAspect(239, 100), pipAspectFor(239, 100))
    }

    @Test
    fun elResultadoSiempreEsAceptableParaAndroid() {
        for ((w, h) in listOf(1 to 1, 1920 to 1080, 3000 to 1000, 1000 to 3000, 10000 to 1, 1 to 10000, 2390 to 1000)) {
            val a = pipAspectFor(w, h)
            val ratio = a.width.toDouble() / a.height
            assertTrue("$w x $h -> $a", ratio <= 2.39 && ratio >= 1 / 2.39)
        }
    }

    // --- qué se muestra en la ventana ----------------------------------------------------------

    @Test
    fun enLaVentanaSoloSeMuestraElVideoSiLaSalaEstaSana() {
        assertEquals(PipContent.VIDEO, pipContentFor(inRoom = true, kicked = false, videoLoaded = true, videoFailed = false))
        assertEquals(PipContent.KICKED, pipContentFor(inRoom = false, kicked = true, videoLoaded = false, videoFailed = false))
        assertEquals(PipContent.ROOM_UNAVAILABLE, pipContentFor(inRoom = false, kicked = false, videoLoaded = false, videoFailed = false))
        assertEquals(PipContent.NO_VIDEO, pipContentFor(inRoom = true, kicked = false, videoLoaded = false, videoFailed = false))
        assertEquals(PipContent.VIDEO_ERROR, pipContentFor(inRoom = true, kicked = false, videoLoaded = true, videoFailed = true))
    }

    // --- cuándo pausar -------------------------------------------------------------------------

    @Test
    fun sinPiPDetenerseSigueSiendoPausar() {
        assertTrue(PauseOnStopTracker().onStopped(changingConfigurations = false, inPipNow = false, screenOn = true))
    }

    @Test
    fun girarElTelefonoNoPausa() {
        assertFalse(PauseOnStopTracker().onStopped(changingConfigurations = true, inPipNow = false, screenOn = true))
    }

    @Test
    fun entrarYVolverTocandoLaVentanaNuncaPausa() {
        val t = PauseOnStopTracker()
        assertFalse(t.onPipModeChanged(inPip = true, activityStopped = false, changingConfigurations = false))
        // Tocar la ventana: la actividad sigue visible, no hay onStop.
        assertFalse(t.onPipModeChanged(inPip = false, activityStopped = false, changingConfigurations = false))
    }

    @Test
    fun cerrarLaVentanaPausa_ordenOnStopYDespuesElCambioDeModo() {
        val t = PauseOnStopTracker()
        t.onPipModeChanged(inPip = true, activityStopped = false, changingConfigurations = false)
        // La actividad se detiene con la ventana todavía "en PiP": no se pausa aún...
        assertFalse(t.onStopped(changingConfigurations = false, inPipNow = true, screenOn = true))
        // ...y al llegar el cambio de modo, con la actividad detenida, sí.
        assertTrue(t.onPipModeChanged(inPip = false, activityStopped = true, changingConfigurations = false))
    }

    @Test
    fun cerrarLaVentanaPausa_ordenCambioDeModoYDespuesOnStop() {
        val t = PauseOnStopTracker()
        t.onPipModeChanged(inPip = true, activityStopped = false, changingConfigurations = false)
        // El cambio de modo llega primero (la actividad aún no se detuvo): todavía no hay nada que pausar...
        assertFalse(t.onPipModeChanged(inPip = false, activityStopped = false, changingConfigurations = false))
        // ...y el onStop siguiente ya no está en PiP: pausa como hoy.
        assertTrue(t.onStopped(changingConfigurations = false, inPipNow = false, screenOn = true))
    }

    @Test
    fun unCambioDeModoConLaActividadYaDetenidaPausa() {
        // Si el onStop se vio sin PiP (por ejemplo, el cambio de modo llegó antes) y luego llega un cambio de modo
        // con la actividad ya detenida, se vuelve a pedir pausar: es inofensivo (pausar dos veces no emite dos veces).
        val t = PauseOnStopTracker()
        assertTrue(t.onPipModeChanged(inPip = false, activityStopped = true, changingConfigurations = false))
    }

    @Test
    fun conLaPantallaApagadaEstandoEnPiPSePausa() {
        val t = PauseOnStopTracker()
        t.onPipModeChanged(inPip = true, activityStopped = false, changingConfigurations = false)
        assertTrue(t.onStopped(changingConfigurations = false, inPipNow = true, screenOn = false))
    }

    @Test
    fun unaParadaEnPiPQueNoEraUnCierreSeOlvidaAlVolverAVerse() {
        val t = PauseOnStopTracker()
        t.onPipModeChanged(inPip = true, activityStopped = false, changingConfigurations = false)
        assertFalse(t.onStopped(changingConfigurations = false, inPipNow = true, screenOn = true))
        t.onStarted() // la actividad volvió a verse
        // Tocar la ventana ahora no debe pausar por la parada vieja.
        assertFalse(t.onPipModeChanged(inPip = false, activityStopped = false, changingConfigurations = false))
    }

    @Test
    fun siLaActividadSeRecreaEnPiPNiLaParadaNiElCambioDeModoPausan() {
        // Sin configChanges, Android puede recrear la actividad al entrar o salir de PiP.
        val t = PauseOnStopTracker(startInPip = true)
        assertFalse(t.onStopped(changingConfigurations = true, inPipNow = true, screenOn = true))
        assertFalse(t.onPipModeChanged(inPip = false, activityStopped = true, changingConfigurations = true))
    }

    @Test
    fun siLaActividadNaceEnPiPYSeDetieneSinCambioDeConfiguracionNoPausaTodavia() {
        val t = PauseOnStopTracker(startInPip = true)
        assertFalse(t.onStopped(changingConfigurations = false, inPipNow = false, screenOn = true))
        assertTrue(t.onPipModeChanged(inPip = false, activityStopped = true, changingConfigurations = false))
    }
}
