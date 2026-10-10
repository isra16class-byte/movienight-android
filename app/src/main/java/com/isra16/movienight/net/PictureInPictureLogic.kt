package com.isra16.movienight.net

/**
 * Lógica pura del mini-reproductor flotante (Fase 7A, Picture-in-Picture): cuándo la app puede entrar en PiP,
 * con qué relación de aspecto y cuándo hay que pausar al dejar de verse. Sin nada de Android para poder
 * probarla en la JVM; el cableado con la actividad está en `MainActivity` y `ui/room/PictureInPicture.kt`.
 *
 * Decisión de la persona: al entrar en PiP NO se pausa nada (ni el video local ni la sala, aunque sea host).
 * Se pausa, como hasta ahora, cuando la ventana flotante se cierra o la app deja de verse del todo.
 */

/** Lo que hay que saber de la sala para decidir si una ventana flotante tiene sentido ahora. */
data class PipConditions(
    /** La pantalla de la sala está en fase `InRoom` (no comprobando, ni contraseña, ni error, ni "te sacaron"). */
    val inRoom: Boolean,
    /** Hay una cinta cargada en el reproductor. */
    val videoLoaded: Boolean,
    /** El video falló (error de carga o de reproducción). */
    val videoFailed: Boolean,
    /** El video se está reproduciendo (o esperando datos para reproducir). */
    val playing: Boolean,
)

/**
 * ¿Hay que entrar en PiP cuando la persona deja la app? Solo con la sala abierta, el video cargado y sano y
 * reproduciéndose, y solo si el sistema deja usar PiP ([available]: función presente y no desactivada para la
 * app en Ajustes). Si da `false` la app se comporta como siempre: al dejar de verse, el video se pausa.
 */
fun shouldEnterPip(conditions: PipConditions, available: Boolean): Boolean =
    available && conditions.inRoom && conditions.videoLoaded && !conditions.videoFailed && conditions.playing

/** Relación de aspecto de la ventana flotante (ancho:alto). */
data class PipAspect(val width: Int, val height: Int)

/** La que se usa mientras no se conoce el tamaño del video. */
val PIP_DEFAULT_ASPECT = PipAspect(16, 9)

private const val PIP_MAX_RATIO = 2.39
private const val PIP_MIN_RATIO = 1 / 2.39

/**
 * Relación de aspecto de la ventana para un video de [videoWidth] x [videoHeight] (ya con la forma del píxel
 * aplicada). Si no se conoce, 16:9. Android rechaza (con una excepción) lo que se salga de 1:2,39 a 2,39:1,
 * así que un video más ancho o más alto se acota un poco por dentro de esos límites.
 */
fun pipAspectFor(videoWidth: Int, videoHeight: Int): PipAspect {
    if (videoWidth <= 0 || videoHeight <= 0) return PIP_DEFAULT_ASPECT
    val ratio = videoWidth.toDouble() / videoHeight
    return when {
        ratio > PIP_MAX_RATIO -> PipAspect(238, 100)
        ratio < PIP_MIN_RATIO -> PipAspect(100, 238)
        else -> PipAspect(videoWidth, videoHeight)
    }
}

/** Lo que la pantalla de la sala le pide a la actividad sobre PiP; la actividad lo aplica al sistema. */
data class PipRequest(val conditions: PipConditions, val aspect: PipAspect) {
    companion object {
        /** Sin ventana flotante posible (pantalla principal, login, sala con error, salir de la sala...). */
        val OFF = PipRequest(
            PipConditions(inRoom = false, videoLoaded = false, videoFailed = false, playing = false),
            PIP_DEFAULT_ASPECT,
        )
    }
}

/** Qué se muestra dentro de la ventana flotante (nunca chat, cabecera ni controles). */
enum class PipContent { VIDEO, KICKED, ROOM_UNAVAILABLE, NO_VIDEO, VIDEO_ERROR }

/**
 * Si la ventana ya está abierta y la sala deja de estar sana (te sacaron, se cerró, el video falló), no se
 * puede cerrar sola: se deja un mensaje corto en lugar de la pantalla completa dibujada en miniatura.
 */
fun pipContentFor(inRoom: Boolean, kicked: Boolean, videoLoaded: Boolean, videoFailed: Boolean): PipContent = when {
    kicked -> PipContent.KICKED
    !inRoom -> PipContent.ROOM_UNAVAILABLE
    !videoLoaded -> PipContent.NO_VIDEO
    videoFailed -> PipContent.VIDEO_ERROR
    else -> PipContent.VIDEO
}

/**
 * Decide cuándo pausar el video al dejar de verse la actividad, teniendo en cuenta la ventana flotante. Sustituye
 * a la condición anterior ("ON_STOP y no es un cambio de configuración").
 *
 * Qué se sabe y qué no del orden de eventos de Android:
 *  - al entrar en PiP la actividad pasa por `onPause` y el cambio de modo, y NO debería pasar por `onStop`;
 *  - al cerrar la ventana (la X) la actividad se detiene (`onStop`), y el cambio de modo a "no PiP" llega antes
 *    o después según versión: por eso hay dos caminos (ver [onStopped] y [onPipModeChanged]);
 *  - al tocar la ventana para volver a pantalla completa NO hay `onStop`.
 * El orden real en el emulador se confirma con los logs de `MovieNightPip`.
 *
 * Todo se llama desde el hilo principal. [startInPip] = la actividad nació ya dentro de la ventana flotante
 * (se recreó estando en PiP).
 */
class PauseOnStopTracker(startInPip: Boolean = false) {
    private var inPip = startInPip

    /** Se detuvo estando en PiP y todavía no se sabe si fue por cerrar la ventana: se decide al llegar el cambio de modo. */
    private var stoppedInPip = false

    /**
     * `ON_STOP`. `true` = hay que pausar ya. [inPipNow] = lo que dice la actividad en este instante
     * (`isInPictureInPictureMode`); [screenOn] = la pantalla está encendida.
     *
     * Sin PiP, o con la pantalla apagada, se pausa (el video no debe seguir sonando con la pantalla apagada). Un
     * cambio de configuración (girar el teléfono) no pausa. Detenida estando en PiP con la pantalla encendida:
     * puede ser que se cerró la ventana (llega el cambio de modo enseguida) o una parada que no es de cierre; no se
     * pausa todavía, se decide en [onPipModeChanged].
     */
    fun onStopped(changingConfigurations: Boolean, inPipNow: Boolean, screenOn: Boolean): Boolean {
        if (changingConfigurations) return false
        if (!inPip && !inPipNow) return true
        if (!screenOn) return true
        stoppedInPip = true
        return false
    }

    /** `ON_START`: la actividad volvió a verse; una parada anterior en PiP ya no cuenta. */
    fun onStarted() {
        stoppedInPip = false
    }

    /**
     * Cambio de modo PiP. `true` = hay que pausar ya. Al entrar nunca se pausa. Al salir se pausa si la actividad
     * está detenida (la ventana se cerró) o si ya se había detenido estando en PiP; si salió porque la persona
     * tocó la ventana (la actividad sigue visible), no. Un cambio de configuración nunca pausa.
     */
    fun onPipModeChanged(inPip: Boolean, activityStopped: Boolean, changingConfigurations: Boolean): Boolean {
        this.inPip = inPip
        if (inPip) return false
        val pause = (stoppedInPip || activityStopped) && !changingConfigurations
        stoppedInPip = false
        return pause
    }
}
