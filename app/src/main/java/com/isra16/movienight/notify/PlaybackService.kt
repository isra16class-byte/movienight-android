package com.isra16.movienight.notify

import android.content.Intent
import androidx.annotation.OptIn
import androidx.core.app.ServiceCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.isra16.movienight.MovieNightApp
import com.isra16.movienight.R

/**
 * Servicio de reproducción (Fase 7B): pone una `MediaSession` sobre el ExoPlayer de la sala para que el audio siga
 * con la pantalla apagada o en otra app, con una notificación que permite reproducir/pausar. NO crea ni es dueño del
 * reproductor: lo toma de [PlaybackBridge] y, al destruirse, solo suelta la sesión (nunca el ExoPlayer, que es del
 * `RoomPlayer`). Quien lo arranca y lo detiene es el puente, según el estado de la sala.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    private val bridge: PlaybackBridge
        get() = (application as MovieNightApp).container.playbackBridge

    override fun onCreate() {
        super.onCreate()
        val player = bridge.sessionPlayer
        if (player == null) {
            // Nadie pidió el servicio (o la sala ya terminó): no queda nada que mostrar.
            stopSelf()
            return
        }
        bridge.attach(this)
        setListener(object : MediaSessionService.Listener {
            // Android 12+ no deja promover a primer plano desde segundo plano: la sala sigue como antes de la 7B.
            override fun onForegroundServiceStartNotAllowedException() {
                bridge.onStartRefused()
            }
        })
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).setChannelName(R.string.playback_channel_name).build(),
        )
        val builder = MediaSession.Builder(this, player).setId("movienight-room")
        launchAppPendingIntent(this)?.let { builder.setSessionActivity(it) }
        session = builder.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** El puente pide parar (salir de la sala, expulsión, error, sin cinta, cerrar sesión): sin notificación zombi. */
    fun stopFromBridge() {
        session?.let {
            removeSession(it)
            it.release()
        }
        session = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Quitar la app de recientes siempre detiene el audio y el servicio (como la 6C con la subida). */
    override fun onTaskRemoved(rootIntent: Intent?) {
        bridge.detachControls()
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        // Solo se suelta la sesión: el ExoPlayer es del RoomPlayer, que lo libera al salir de la sala.
        session?.release()
        session = null
        bridge.detach(this)
        super.onDestroy()
    }
}
