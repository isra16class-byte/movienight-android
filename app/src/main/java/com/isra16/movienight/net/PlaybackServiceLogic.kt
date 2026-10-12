package com.isra16.movienight.net

import androidx.media3.common.Player

/*
 * Fase 7B: lógica pura (sin Android) del audio que sigue con la pantalla apagada. Qué decide cuándo arranca y se
 * detiene el servicio de reproducción, qué dice la notificación y qué pausas del sistema se propagan a la sala.
 */

/** Título de la notificación si la sala todavía no tiene el nombre del video. */
const val PLAYBACK_DEFAULT_TITLE = "MovieNight"
const val PLAYBACK_TEXT_PLAYING = "Reproduciendo"
const val PLAYBACK_TEXT_PAUSED = "En pausa"

/** Título de la notificación: el nombre del video, o "MovieNight" si está vacío. */
fun playbackNotificationTitle(videoName: String): String = videoName.trim().ifEmpty { PLAYBACK_DEFAULT_TITLE }

/** Texto de la notificación según [playing] (el video quiere reproducir). */
fun playbackNotificationText(playing: Boolean): String = if (playing) PLAYBACK_TEXT_PLAYING else PLAYBACK_TEXT_PAUSED

/**
 * Lo que mira el puente para decidir si hay servicio. [inRoom] = la sala está sana (`RoomPhase.InRoom`); [hasVideo] =
 * hay cinta cargada; [hasError] = el video falló; [playing] = el video está reproduciendo (botón en "Pausar");
 * [appVisible] = hay una pantalla de la app a la vista.
 */
data class PlaybackSnapshot(
    val inRoom: Boolean,
    val hasVideo: Boolean,
    val hasError: Boolean,
    val playing: Boolean,
    val appVisible: Boolean,
)

enum class PlaybackServiceAction { START, STOP, NONE }

/**
 * Arrancar exige TODO: sala sana, cinta cargada, sin error, reproduciendo Y la app a la vista (Android 12+ no deja
 * arrancar un servicio en primer plano desde segundo plano). Detenerlo, en cambio, solo exige que la sala deje de
 * estar sana, sin cinta o con error: pausar NO lo detiene (la notificación se queda y se puede deslizar), y apagar la
 * pantalla tampoco. [startRefused] = Android ya rechazó un arranque: no se reintenta mientras siga reproduciendo.
 */
fun playbackServiceAction(active: Boolean, s: PlaybackSnapshot, startRefused: Boolean = false): PlaybackServiceAction {
    val healthy = s.inRoom && s.hasVideo && !s.hasError
    return when {
        active && !healthy -> PlaybackServiceAction.STOP
        !active && healthy && s.playing && s.appVisible && !startRefused -> PlaybackServiceAction.START
        else -> PlaybackServiceAction.NONE
    }
}

/**
 * ¿Este cambio de `playWhenReady` es una pausa deliberada del sistema que hay que propagar a la sala? Solo la pérdida
 * PERMANENTE del foco de audio (otra app de música tomó el audio) y desenchufar los auriculares. La pérdida pasajera
 * (una llamada) ExoPlayer la suprime y se reanuda sola sin cambiar `playWhenReady`: no llega acá ni se toca.
 */
fun isSystemPause(playWhenReady: Boolean, reason: Int): Boolean =
    !playWhenReady && (
        reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
            reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
        )
