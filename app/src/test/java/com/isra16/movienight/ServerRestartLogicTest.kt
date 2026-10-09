package com.isra16.movienight

import com.isra16.movienight.net.RECONNECTING_MESSAGE
import com.isra16.movienight.net.RESTART_ANNOUNCED_MESSAGE
import com.isra16.movienight.net.RESTART_RECONNECTING_MESSAGE
import com.isra16.movienight.net.RestartState
import com.isra16.movienight.net.connectionBanner
import com.isra16.movienight.net.roomErrorAfterRestart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerRestartLogicTest {

    private val announced = RestartState().onAnnounced()

    // --- Seguimiento del reinicio ---

    @Test
    fun alPrincipioNoHayReinicio() {
        val s = RestartState()
        assertFalse(s.announced)
        assertFalse(s.isRejoining)
        assertFalse(s.showBanner)
    }

    @Test
    fun anunciadoMuestraElAvisoPeroTodaviaNoEsLaVuelta() {
        assertTrue(announced.showBanner)
        assertFalse(announced.isRejoining)
    }

    @Test
    fun cicloCompletoAnunciarCortarYUnirse() {
        val cut = announced.onDisconnected()
        assertTrue(cut.isRejoining)
        assertTrue(cut.showBanner)
        val (next, afterRestart) = cut.onJoined()
        assertTrue(afterRestart)
        assertEquals(RestartState(), next)
        assertFalse(next.showBanner)
    }

    @Test
    fun unaCaidaSinReinicioAnunciadoNoEsUnReinicio() {
        val s = RestartState().onDisconnected()
        assertFalse(s.announced)
        assertFalse(s.isRejoining)
        val (next, afterRestart) = s.onJoined()
        assertFalse(afterRestart)
        assertEquals(RestartState(), next)
    }

    @Test
    fun unJoinQueYaVeniaEnCaminoNoCierraElAviso() {
        // room-data llega después de `server-restarting` pero antes del corte: no es la vuelta.
        val (next, afterRestart) = announced.onJoined()
        assertFalse(afterRestart)
        assertEquals(announced, next)
        assertTrue(next.showBanner)
    }

    @Test
    fun unSegundoAvisoReiniciaElSeguimiento() {
        val again = announced.onDisconnected().onAnnounced()
        assertTrue(again.announced)
        assertFalse(again.disconnected)
    }

    @Test
    fun alRendirseElAvisoSeOcultaPeroLaVueltaSigueSiendoDeReinicio() {
        val gaveUp = announced.onDisconnected().giveUpBanner()
        assertFalse(gaveUp.showBanner)
        assertTrue(gaveUp.isRejoining)
        val (next, afterRestart) = gaveUp.onJoined()
        assertTrue(afterRestart)
        assertEquals(RestartState(), next)
    }

    @Test
    fun rendirseSinReinicioNoHaceNada() {
        assertEquals(RestartState(), RestartState().giveUpBanner())
    }

    // --- Franja de aviso ---

    @Test
    fun sinReinicioElAvisoSigueLaReglaDeSiempre() {
        val none = RestartState()
        assertNull(connectionBanner(true, none, null))
        assertEquals("hola", connectionBanner(true, none, "hola"))
        assertEquals(RECONNECTING_MESSAGE, connectionBanner(false, none, null))
        assertEquals("No se pudo conectar", connectionBanner(false, none, "No se pudo conectar"))
    }

    @Test
    fun anunciadoYConectadoAvisaQueVaAReiniciar() {
        assertEquals(RESTART_ANNOUNCED_MESSAGE, connectionBanner(true, announced, null))
    }

    @Test
    fun anunciadoYDesconectadoDiceQueEstaReiniciando() {
        assertEquals(RESTART_RECONNECTING_MESSAGE, connectionBanner(false, announced.onDisconnected(), null))
    }

    @Test
    fun losErroresDeConexionNoPisanElAvisoDeReinicio() {
        // Mientras el server está caído llegan `connect_error` seguidos: el aviso sigue siendo el del reinicio.
        val cut = announced.onDisconnected()
        assertEquals(
            RESTART_RECONNECTING_MESSAGE,
            connectionBanner(false, cut, "No se pudo conectar con el servidor. Reintentando…"),
        )
    }

    @Test
    fun unAvisoDeLimiteDeMensajesNoBorraElDeReinicio() {
        assertEquals(RESTART_ANNOUNCED_MESSAGE, connectionBanner(true, announced, "Esperá un momento"))
    }

    @Test
    fun alVolverElAvisoDesaparece() {
        val (joined, _) = announced.onDisconnected().onJoined()
        assertNull(connectionBanner(true, joined, null))
    }

    @Test
    fun siElServerNoVuelveSeCaeAlAvisoGenerico() {
        val gaveUp = announced.onDisconnected().giveUpBanner()
        assertEquals(RECONNECTING_MESSAGE, connectionBanner(false, gaveUp, null))
        assertEquals("No se pudo conectar", connectionBanner(false, gaveUp, "No se pudo conectar"))
    }

    // --- room-error al volver ---

    @Test
    fun elErrorDeSalaTrasUnReinicioExplicaElContexto() {
        val msg = roomErrorAfterRestart("La sala no existe.")
        assertTrue(msg.startsWith("La sala no existe."))
        assertTrue(msg.contains("reiniciando"))
    }
}
