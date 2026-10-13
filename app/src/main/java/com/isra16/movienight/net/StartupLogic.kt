package com.isra16.movienight.net

import com.isra16.movienight.auth.SessionState

/*
 * Qué pantalla se muestra al abrir la app (Fase 8, paso 2). JVM puro, sin Android.
 *
 * Dos cosas corren a la vez y no se esperan entre sí: la intro (un tiempo fijo, ver INTRO_DURATION_MS) y la
 * comprobación de sesión (`GET /auth/me`, la lanza SessionViewModel al crearse). Esta función solo cruza las dos:
 *  - mientras la intro no termina se ve la intro, sea cual sea el estado de sesión (si el server responde antes,
 *    la animación NO se corta);
 *  - al terminar, manda el estado de sesión: la pantalla de carga que ya existía mientras no hay respuesta, el error
 *    de conexión si no se pudo alcanzar, el login si no hay sesión y la pantalla principal si la hay.
 * Una vez terminada, la intro no vuelve: un cambio posterior de sesión (cerrar sesión, reintentar) no la repite.
 */

/** Duración total de la intro, fija: no depende de lo que tarde el servidor. */
const val INTRO_DURATION_MS = 1000L

/** Lo que dura el movimiento del marcador de la intro; el resto del tiempo el nombre queda quieto. */
const val INTRO_ANIMATION_MS = 700

enum class StartupScreen { INTRO, LOADING, CONNECTION_ERROR, AUTH, MAIN }

fun startupScreenFor(introFinished: Boolean, session: SessionState): StartupScreen =
    if (!introFinished) {
        StartupScreen.INTRO
    } else {
        when (session) {
            SessionState.Loading -> StartupScreen.LOADING
            is SessionState.Unreachable -> StartupScreen.CONNECTION_ERROR
            SessionState.LoggedOut -> StartupScreen.AUTH
            is SessionState.LoggedIn -> StartupScreen.MAIN
        }
    }
