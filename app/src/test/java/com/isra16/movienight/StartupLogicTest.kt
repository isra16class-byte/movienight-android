package com.isra16.movienight

import com.isra16.movienight.auth.SessionState
import com.isra16.movienight.net.INTRO_ANIMATION_MS
import com.isra16.movienight.net.INTRO_DURATION_MS
import com.isra16.movienight.net.StartupScreen
import com.isra16.movienight.net.startupScreenFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupLogicTest {

    private val loggedIn = SessionState.LoggedIn(id = "u1", email = "a@b.com")
    private val unreachable = SessionState.Unreachable("sin red")

    private val allSessions: List<SessionState> =
        listOf(SessionState.Loading, SessionState.LoggedOut, loggedIn, unreachable)

    @Test
    fun `mientras la intro no termina siempre se ve la intro, con cualquier estado de sesion`() {
        for (s in allSessions) {
            assertEquals("sesión $s", StartupScreen.INTRO, startupScreenFor(introFinished = false, session = s))
        }
    }

    @Test
    fun `si el servidor responde antes de que termine la intro, la intro no se corta`() {
        assertEquals(StartupScreen.INTRO, startupScreenFor(false, loggedIn))
        assertEquals(StartupScreen.INTRO, startupScreenFor(false, SessionState.LoggedOut))
        assertEquals(StartupScreen.INTRO, startupScreenFor(false, unreachable))
    }

    @Test
    fun `terminada la intro sin respuesta del servidor se ve la pantalla de carga`() {
        assertEquals(StartupScreen.LOADING, startupScreenFor(true, SessionState.Loading))
    }

    @Test
    fun `terminada la intro con sesion va a la pantalla principal`() {
        assertEquals(StartupScreen.MAIN, startupScreenFor(true, loggedIn))
    }

    @Test
    fun `terminada la intro sin sesion va al login`() {
        assertEquals(StartupScreen.AUTH, startupScreenFor(true, SessionState.LoggedOut))
    }

    @Test
    fun `terminada la intro con el servidor inalcanzable se ve el error de conexion`() {
        assertEquals(StartupScreen.CONNECTION_ERROR, startupScreenFor(true, unreachable))
    }

    @Test
    fun `la secuencia de una respuesta lenta pasa por intro, carga y destino`() {
        // Servidor lento: la intro termina con la sesión todavía en Loading.
        assertEquals(StartupScreen.INTRO, startupScreenFor(false, SessionState.Loading))
        assertEquals(StartupScreen.LOADING, startupScreenFor(true, SessionState.Loading))
        assertEquals(StartupScreen.MAIN, startupScreenFor(true, loggedIn))
    }

    @Test
    fun `reintentar tras el error vuelve a la carga y la intro no se repite`() {
        assertEquals(StartupScreen.CONNECTION_ERROR, startupScreenFor(true, unreachable))
        // Reintentar pone la sesión en Loading; la intro ya terminó y no vuelve.
        assertEquals(StartupScreen.LOADING, startupScreenFor(true, SessionState.Loading))
        assertEquals(StartupScreen.AUTH, startupScreenFor(true, SessionState.LoggedOut))
    }

    @Test
    fun `cerrar sesion despues de la intro va al login sin intro`() {
        assertEquals(StartupScreen.MAIN, startupScreenFor(true, loggedIn))
        assertEquals(StartupScreen.AUTH, startupScreenFor(true, SessionState.LoggedOut))
    }

    @Test
    fun `terminada la intro cada estado de sesion tiene una pantalla distinta`() {
        val screens = allSessions.map { startupScreenFor(true, it) }
        assertEquals(allSessions.size, screens.toSet().size)
        assertTrue(StartupScreen.INTRO !in screens)
    }

    @Test
    fun `tiempos de la intro coherentes`() {
        assertEquals(1000L, INTRO_DURATION_MS)
        assertTrue(INTRO_ANIMATION_MS > 0)
        assertTrue(INTRO_ANIMATION_MS <= INTRO_DURATION_MS)
    }
}
