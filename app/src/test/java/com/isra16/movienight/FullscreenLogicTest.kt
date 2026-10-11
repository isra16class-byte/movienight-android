package com.isra16.movienight

import com.isra16.movienight.net.ASIDE_VISIBLE_MS
import com.isra16.movienight.net.CONTROLS_VISIBLE_MS
import com.isra16.movienight.net.FullscreenState
import com.isra16.movienight.net.OrientationRequest
import com.isra16.movienight.net.OverlayState
import com.isra16.movienight.net.RoomLayout
import com.isra16.movienight.net.orientationRequestFor
import com.isra16.movienight.net.roomLayoutFor
import com.isra16.movienight.net.shouldReleasePortraitHold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FullscreenLogicTest {

    // --- qué vista ------------------------------------------------------------------------------

    @Test
    fun enVerticalSinPedirlaSeVeLaVistaNormal() {
        assertEquals(RoomLayout.NORMAL, roomLayoutFor(inRoom = true, fullscreenRequested = false, landscape = false))
    }

    @Test
    fun enHorizontalSeVePantallaCompletaAunqueNoSePida() {
        assertEquals(RoomLayout.FULLSCREEN, roomLayoutFor(inRoom = true, fullscreenRequested = false, landscape = true))
    }

    @Test
    fun conElBotonSeVePantallaCompletaAunqueElTelefonoAunNoGire() {
        assertEquals(RoomLayout.FULLSCREEN, roomLayoutFor(inRoom = true, fullscreenRequested = true, landscape = false))
    }

    @Test
    fun fueraDeLaSalaSiempreSeVeLaVistaNormal() {
        assertEquals(RoomLayout.NORMAL, roomLayoutFor(inRoom = false, fullscreenRequested = true, landscape = true))
        assertEquals(RoomLayout.NORMAL, roomLayoutFor(inRoom = false, fullscreenRequested = false, landscape = true))
    }

    // --- qué orientación se pide ----------------------------------------------------------------

    @Test
    fun conElBotonSePideHorizontalFija() {
        assertEquals(OrientationRequest.LANDSCAPE, orientationRequestFor(inRoom = true, fullscreenRequested = true, portraitHold = false))
    }

    @Test
    fun despuesDeSalirSeRetieneLaVertical() {
        assertEquals(OrientationRequest.PORTRAIT, orientationRequestFor(inRoom = true, fullscreenRequested = false, portraitHold = true))
    }

    @Test
    fun sinBotonNiRetencionLaOrientacionEsLibre() {
        assertEquals(OrientationRequest.FREE, orientationRequestFor(inRoom = true, fullscreenRequested = false, portraitHold = false))
    }

    @Test
    fun fueraDeLaSalaLaOrientacionEsLibre() {
        assertEquals(OrientationRequest.FREE, orientationRequestFor(inRoom = false, fullscreenRequested = true, portraitHold = false))
        assertEquals(OrientationRequest.FREE, orientationRequestFor(inRoom = false, fullscreenRequested = false, portraitHold = true))
    }

    // --- salir con ✕ / Atrás --------------------------------------------------------------------

    @Test
    fun salirPideLaVerticalYDejaDeQuererPantallaCompleta() {
        val after = FullscreenState().enter().exit()
        assertFalse(after.requested)
        assertTrue(after.portraitHold)
    }

    @Test
    fun salirDesdeElGiroAutomaticoTambienRetieneLaVertical() {
        // Nunca se pulsó el botón (requested = false): igual hay que pedir la vertical, o el teléfono acostado volvería solo.
        val after = FullscreenState().exit()
        assertTrue(after.portraitHold)
    }

    @Test
    fun entrarDeNuevoSueltaLaRetencion() {
        val after = FullscreenState().exit().enter()
        assertTrue(after.requested)
        assertFalse(after.portraitHold)
    }

    @Test
    fun laVerticalRetenidaSeSueltaCuandoElTelefonoQuedaDerecho() {
        val held = FullscreenState().exit()
        assertTrue(held.onPhysicalAngle(90).portraitHold) // acostado: se mantiene
        assertTrue(held.onPhysicalAngle(270).portraitHold)
        assertTrue(held.onPhysicalAngle(-1).portraitHold) // desconocido: se mantiene
        assertFalse(held.onPhysicalAngle(0).portraitHold)
        assertFalse(held.onPhysicalAngle(15).portraitHold)
    }

    @Test
    fun elAnguloNoCambiaNadaSiNoHayRetencion() {
        val free = FullscreenState(requested = true)
        assertEquals(free, free.onPhysicalAngle(0))
    }

    @Test
    fun anguloDerechoConTolerancia() {
        assertTrue(shouldReleasePortraitHold(0))
        assertTrue(shouldReleasePortraitHold(30))
        assertTrue(shouldReleasePortraitHold(330))
        assertTrue(shouldReleasePortraitHold(359))
        assertTrue(shouldReleasePortraitHold(180)) // boca abajo también es vertical
        assertTrue(shouldReleasePortraitHold(155))
        assertFalse(shouldReleasePortraitHold(31))
        assertFalse(shouldReleasePortraitHold(90))
        assertFalse(shouldReleasePortraitHold(270))
        assertFalse(shouldReleasePortraitHold(-1))
    }

    // --- controles: dos grupos, cada uno con su reloj -------------------------------------------

    @Test
    fun alEntrarSeVenLosDosGruposYCadaUnoSeEscondeASuTiempo() {
        val s = OverlayState.shown(1_000L)
        assertTrue(s.controlsVisible(1_000L))
        assertTrue(s.asideVisible(1_000L))
        // A los 3 s se esconden los controles; el grupo lateral sigue hasta los 5 s.
        assertFalse(s.controlsVisible(1_000L + CONTROLS_VISIBLE_MS))
        assertTrue(s.asideVisible(1_000L + CONTROLS_VISIBLE_MS))
        assertFalse(s.asideVisible(1_000L + ASIDE_VISIBLE_MS))
    }

    @Test
    fun losTiemposSonTresYCincoSegundos() {
        assertEquals(3_000L, CONTROLS_VISIBLE_MS)
        assertEquals(5_000L, ASIDE_VISIBLE_MS)
    }

    @Test
    fun tocarElVideoConAlgoVisibleEscondeTodo() {
        val s = OverlayState.shown(0L).onTapVideo(1_000L)
        assertFalse(s.controlsVisible(1_000L))
        assertFalse(s.asideVisible(1_000L))
    }

    @Test
    fun tocarElVideoConTodoOcultoMuestraLosDosGrupos() {
        val s = OverlayState().onTapVideo(10_000L)
        assertTrue(s.controlsVisible(10_000L))
        assertTrue(s.asideVisible(10_000L))
    }

    @Test
    fun tocarElVideoConSoloUnGrupoVisibleTambienEscondeTodo() {
        // A los 4 s los controles ya se escondieron pero el grupo lateral sigue visible.
        val s = OverlayState.shown(0L).onTapVideo(4_000L)
        assertFalse(s.asideVisible(4_000L))
        assertFalse(s.controlsVisible(4_000L))
    }

    @Test
    fun tocarUnControlRefrescaSoloSuGrupo() {
        val s = OverlayState.shown(0L).touchControls(2_500L)
        assertTrue(s.controlsVisible(2_500L + CONTROLS_VISIBLE_MS - 1))
        assertEquals(OverlayState.shown(0L).asideUntilMs, s.asideUntilMs) // el otro reloj no se tocó
    }

    @Test
    fun tocarElContadorRefrescaSoloElGrupoLateral() {
        val s = OverlayState.shown(0L).touchAside(4_000L)
        assertEquals(4_000L + ASIDE_VISIBLE_MS, s.asideUntilMs)
        assertEquals(CONTROLS_VISIBLE_MS, s.controlsUntilMs)
    }

    @Test
    fun conUnCuadroAbiertoElGrupoLateralQuedaFijoYNoCorreNingunReloj() {
        val s = OverlayState.shown(0L).pinAside()
        assertTrue(s.asideVisible(1_000_000L))
        assertFalse(s.controlsVisible(0L))
        assertNull(s.nextExpiry(0L))
    }

    @Test
    fun alCerrarElCuadroElGrupoLateralVuelveAContarSusCincoSegundos() {
        val s = OverlayState.shown(0L).pinAside().unpinAside(20_000L)
        assertTrue(s.asideVisible(20_000L + ASIDE_VISIBLE_MS - 1))
        assertFalse(s.asideVisible(20_000L + ASIDE_VISIBLE_MS))
    }

    @Test
    fun soltarSinHaberFijadoNoCambiaNada() {
        val s = OverlayState.shown(0L)
        assertEquals(s, s.unpinAside(1_000L))
    }

    @Test
    fun laProximaExpiracionEsLaMasCercanaFutura() {
        val s = OverlayState.shown(0L)
        assertEquals(CONTROLS_VISIBLE_MS, s.nextExpiry(0L))
        assertEquals(ASIDE_VISIBLE_MS, s.nextExpiry(CONTROLS_VISIBLE_MS))
        assertNull(s.nextExpiry(ASIDE_VISIBLE_MS))
        assertNull(OverlayState().nextExpiry(0L))
    }
}
