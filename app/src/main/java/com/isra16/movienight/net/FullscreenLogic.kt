package com.isra16.movienight.net

import kotlin.math.abs

/*
 * Lógica pura de la pantalla completa de la sala (Fase 7C): qué vista se muestra, qué orientación se pide y cuándo
 * se ven los controles. Sin imports de Android para poder probarla con tests unitarios; la UI (RoomFullscreen.kt) solo
 * le pasa los hechos de Android (orientación actual, ángulo del teléfono, reloj).
 */

/** Las dos vistas de la sala: la normal (cabecera, video, chat) o solo el video a pantalla completa. */
enum class RoomLayout { NORMAL, FULLSCREEN }

/**
 * Qué vista mostrar. Pantalla completa si estás en la sala y (la pediste con el botón o el teléfono está en
 * horizontal): la vista normal no sirve en horizontal. Fuera de la sala (contraseña, error, expulsado) siempre la normal.
 */
fun roomLayoutFor(inRoom: Boolean, fullscreenRequested: Boolean, landscape: Boolean): RoomLayout =
    if (inRoom && (fullscreenRequested || landscape)) RoomLayout.FULLSCREEN else RoomLayout.NORMAL

/** Qué orientación se le pide al sistema: libre, horizontal fija o vertical fija. */
enum class OrientationRequest { FREE, LANDSCAPE, PORTRAIT }

/**
 * Pantalla completa pedida con el botón: horizontal fija (aunque el giro automático esté apagado). Después de salir
 * ([FullscreenState.exit]) se retiene la vertical hasta que el teléfono esté derecho. Fuera de la sala, libre.
 */
fun orientationRequestFor(inRoom: Boolean, fullscreenRequested: Boolean, portraitHold: Boolean): OrientationRequest = when {
    !inRoom -> OrientationRequest.FREE
    fullscreenRequested -> OrientationRequest.LANDSCAPE
    portraitHold -> OrientationRequest.PORTRAIT
    else -> OrientationRequest.FREE
}

/** Tolerancia, en grados, para dar por "derecho" al teléfono al soltar la vertical retenida. */
const val PORTRAIT_RELEASE_TOLERANCE_DEG = 30

/**
 * `true` si el ángulo físico del teléfono (`OrientationEventListener`, 0..359; negativo = desconocido, por ejemplo
 * boca arriba sobre la mesa) está cerca de vertical (derecho o boca abajo). Con el ángulo desconocido no se suelta.
 */
fun shouldReleasePortraitHold(degrees: Int): Boolean {
    if (degrees < 0) return false
    val d = degrees % 360
    val fromUpright = minOf(d, 360 - d)
    val fromUpsideDown = abs(d - 180)
    return fromUpright <= PORTRAIT_RELEASE_TOLERANCE_DEG || fromUpsideDown <= PORTRAIT_RELEASE_TOLERANCE_DEG
}

/**
 * Estado de la pantalla completa: [requested] = la persona la pidió con el botón; [portraitHold] = salió de ella y se
 * retiene la vertical (si no, con el teléfono acostado la regla "horizontal = pantalla completa" lo devolvería).
 */
data class FullscreenState(val requested: Boolean = false, val portraitHold: Boolean = false) {
    /** Botón de pantalla completa. */
    fun enter() = FullscreenState(requested = true, portraitHold = false)

    /** ✕ o Atrás: volver a la vista vertical. */
    fun exit() = FullscreenState(requested = false, portraitHold = true)

    /** Ángulo físico nuevo: si ya está derecho se suelta la vertical retenida. */
    fun onPhysicalAngle(degrees: Int): FullscreenState =
        if (portraitHold && shouldReleasePortraitHold(degrees)) copy(portraitHold = false) else this
}

/** Cuánto dura visible el grupo de controles (barra, ✕, etiqueta de host). */
const val CONTROLS_VISIBLE_MS = 3_000L

/** Cuánto dura visible el grupo lateral (contador de conectados): leerlo lleva más que un vistazo a la barra. */
const val ASIDE_VISIBLE_MS = 5_000L

/**
 * Los dos grupos de controles de la pantalla completa, cada uno con su propio reloj (como en la web: con un solo reloj,
 * lo que lleva más tiempo leer desaparecía a mitad de uso). Los tiempos son marcas absolutas del reloj que le pase la UI.
 * Con un cuadro abierto ([pinAside]) el grupo lateral queda fijo y no corre ningún reloj.
 */
data class OverlayState(val controlsUntilMs: Long = 0L, val asideUntilMs: Long = 0L) {

    fun controlsVisible(now: Long): Boolean = now < controlsUntilMs

    fun asideVisible(now: Long): Boolean = now < asideUntilMs

    /** Tocar el video: si algo se ve, se esconde todo; si no, aparecen los dos grupos. */
    fun onTapVideo(now: Long): OverlayState =
        if (controlsVisible(now) || asideVisible(now)) OverlayState() else shown(now)

    /** Se tocó (o se arrastró) un control: se refresca solo el reloj de su grupo. */
    fun touchControls(now: Long) = copy(controlsUntilMs = now + CONTROLS_VISIBLE_MS)

    fun touchAside(now: Long) = copy(asideUntilMs = now + ASIDE_VISIBLE_MS)

    /** Cuadro abierto (lista de conectados): el grupo lateral fijo, los controles escondidos y ningún reloj corriendo. */
    fun pinAside() = OverlayState(controlsUntilMs = 0L, asideUntilMs = Long.MAX_VALUE)

    /** Se cerró el cuadro: el grupo lateral vuelve a tener su reloj normal. Si no estaba fijo, no cambia nada. */
    fun unpinAside(now: Long): OverlayState =
        if (asideUntilMs == Long.MAX_VALUE) copy(asideUntilMs = now + ASIDE_VISIBLE_MS) else this

    /** La próxima marca en la que algo cambia de visible a oculto, o `null` si no hay nada que esperar. */
    fun nextExpiry(now: Long): Long? =
        listOf(controlsUntilMs, asideUntilMs)
            .filter { it > now && it != Long.MAX_VALUE }
            .minOrNull()

    companion object {
        /** Los dos grupos visibles a partir de [now] (al entrar en pantalla completa y al tocar el video). */
        fun shown(now: Long) = OverlayState(controlsUntilMs = now + CONTROLS_VISIBLE_MS, asideUntilMs = now + ASIDE_VISIBLE_MS)
    }
}
