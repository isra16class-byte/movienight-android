package com.isra16.movienight

import androidx.media3.common.Player
import com.isra16.movienight.net.PLAYBACK_DEFAULT_TITLE
import com.isra16.movienight.net.PauseOnStopTracker
import com.isra16.movienight.net.PlaybackServiceAction
import com.isra16.movienight.net.PlaybackSnapshot
import com.isra16.movienight.net.isSystemPause
import com.isra16.movienight.net.playbackNotificationText
import com.isra16.movienight.net.playbackNotificationTitle
import com.isra16.movienight.net.playbackServiceAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackServiceLogicTest {

    private val healthy = PlaybackSnapshot(inRoom = true, hasVideo = true, hasError = false, playing = true, appVisible = true)

    // --- cuándo arranca ------------------------------------------------------------------------

    @Test
    fun arrancaConSalaSanaCintaSinErrorReproduciendoYAppVisible() {
        assertEquals(PlaybackServiceAction.START, playbackServiceAction(active = false, healthy))
    }

    @Test
    fun noArrancaSiFaltaAlgunaCondicion() {
        assertEquals(PlaybackServiceAction.NONE, playbackServiceAction(false, healthy.copy(inRoom = false)))
        assertEquals(PlaybackServiceAction.NONE, playbackServiceAction(false, healthy.copy(hasVideo = false)))
        assertEquals(PlaybackServiceAction.NONE, playbackServiceAction(false, healthy.copy(hasError = true)))
        assertEquals(PlaybackServiceAction.NONE, playbackServiceAction(false, healthy.copy(playing = false)))
        // Android 12+: sin la app a la vista no se puede arrancar un servicio en primer plano.
        assertEquals(PlaybackServiceAction.NONE, playbackServiceAction(false, healthy.copy(appVisible = false)))
    }

    @Test
    fun trasUnArranqueRechazadoNoSeReintenta() {
        assertEquals(PlaybackServiceAction.NONE, playbackServiceAction(false, healthy, startRefused = true))
    }

    // --- cuándo se detiene ---------------------------------------------------------------------

    @Test
    fun pausarNoLoDetiene() {
        assertEquals(PlaybackServiceAction.NONE, playbackServiceAction(true, healthy.copy(playing = false)))
    }

    @Test
    fun conLaAppFueraDePantallaSigue() {
        assertEquals(PlaybackServiceAction.NONE, playbackServiceAction(true, healthy.copy(appVisible = false)))
    }

    @Test
    fun seDetieneSiLaSalaDejaDeEstarSana() {
        // Salir, expulsión, RoomError / Failed (fase distinta de InRoom).
        assertEquals(PlaybackServiceAction.STOP, playbackServiceAction(true, healthy.copy(inRoom = false)))
        // Video con error o sin cinta.
        assertEquals(PlaybackServiceAction.STOP, playbackServiceAction(true, healthy.copy(hasError = true)))
        assertEquals(PlaybackServiceAction.STOP, playbackServiceAction(true, healthy.copy(hasVideo = false)))
        // Aunque estuviera pausado o la app fuera de pantalla.
        assertEquals(PlaybackServiceAction.STOP, playbackServiceAction(true, healthy.copy(inRoom = false, playing = false, appVisible = false)))
    }

    @Test
    fun sinServicioYSinSalaNoHaceNada() {
        assertEquals(PlaybackServiceAction.NONE, playbackServiceAction(false, healthy.copy(inRoom = false, playing = false)))
    }

    // --- notificación --------------------------------------------------------------------------

    @Test
    fun elTituloEsElNombreDelVideoOMovieNight() {
        assertEquals("peli.mp4", playbackNotificationTitle("peli.mp4"))
        assertEquals(PLAYBACK_DEFAULT_TITLE, playbackNotificationTitle(""))
        assertEquals(PLAYBACK_DEFAULT_TITLE, playbackNotificationTitle("   "))
        assertEquals("MovieNight", PLAYBACK_DEFAULT_TITLE)
    }

    @Test
    fun elTextoDiceReproduciendoOEnPausa() {
        assertEquals("Reproduciendo", playbackNotificationText(true))
        assertEquals("En pausa", playbackNotificationText(false))
    }

    // --- pausas del sistema --------------------------------------------------------------------

    @Test
    fun soloSePropagaLaPerdidaPermanenteDeFocoYLosAuriculares() {
        assertTrue(isSystemPause(false, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS))
        assertTrue(isSystemPause(false, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY))
    }

    @Test
    fun lasPausasYReproduccionesDeLaPersonaOLaSalaNoSonDelSistema() {
        assertFalse(isSystemPause(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST))
        assertFalse(isSystemPause(false, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE))
        assertFalse(isSystemPause(false, Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM))
    }

    @Test
    fun reanudarNuncaEsUnaPausaDelSistema() {
        assertFalse(isSystemPause(true, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS))
        assertFalse(isSystemPause(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST))
    }

    // --- pausar al dejar de verse la app, con el servicio activo -------------------------------

    @Test
    fun conElServicioActivoHomeOtraAppOPantallaApagadaNoPausan() {
        val t = PauseOnStopTracker()
        assertFalse(t.onStopped(changingConfigurations = false, inPipNow = false, screenOn = true, backgroundAudioActive = true))
        assertFalse(t.onStopped(changingConfigurations = false, inPipNow = false, screenOn = false, backgroundAudioActive = true))
    }

    @Test
    fun sinElServicioSigueSiendoPausar() {
        assertTrue(PauseOnStopTracker().onStopped(changingConfigurations = false, inPipNow = false, screenOn = true, backgroundAudioActive = false))
        assertTrue(PauseOnStopTracker().onStopped(changingConfigurations = false, inPipNow = false, screenOn = false, backgroundAudioActive = false))
    }

    @Test
    fun cerrarLaVentanaFlotanteConLaXSigueSiendoPausarAunqueHayaServicio() {
        val t = PauseOnStopTracker()
        assertFalse(t.onPipModeChanged(inPip = true, activityStopped = false, changingConfigurations = false))
        // La ventana se cierra con la X: la actividad queda detenida y el modo sale de PiP.
        assertTrue(t.onPipModeChanged(inPip = false, activityStopped = true, changingConfigurations = false))
    }

    @Test
    fun cerrarLaVentanaPorElCaminoDelOnStopTambienPausaConServicio() {
        val t = PauseOnStopTracker()
        t.onPipModeChanged(inPip = true, activityStopped = false, changingConfigurations = false)
        assertFalse(t.onStopped(changingConfigurations = false, inPipNow = true, screenOn = true, backgroundAudioActive = true))
        assertTrue(t.onPipModeChanged(inPip = false, activityStopped = false, changingConfigurations = false))
    }
}
