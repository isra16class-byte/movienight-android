package com.isra16.movienight.room

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.isra16.movienight.net.playbackErrorMessage
import com.isra16.movienight.net.shouldLoadVideo

/**
 * El reproductor de la sala (ExoPlayer de Media3) y lo que la pantalla necesita saber de él, como
 * estado de Compose. Lo crea y lo suelta el [RoomViewModel]: así sobrevive a girar el teléfono (el
 * video sigue por donde iba) y se libera al salir de la sala.
 *
 * Todo se llama desde el hilo principal. El ExoPlayer se crea recién con la primera cinta, para no
 * gastarlo en salas que se rechazan (contraseña mala, sala inexistente).
 *
 * Fase 3, parte A: la app es solo espectadora y el play/pause es local. La sincronización con el host
 * (eventos `sync`) llega en la parte B.
 */
class RoomPlayer(private val context: Context) {

    private var exo: ExoPlayer? = null
    private var loadedUrl: String? = null

    private var playWhenReady by mutableStateOf(false)
    private var playbackState by mutableIntStateOf(Player.STATE_IDLE)

    /** Para conectarlo al `PlayerView`. `null` hasta que la sala tenga una cinta. */
    var player by mutableStateOf<Player?>(null)
        private set

    /** ¿La sala tiene una cinta cargada en el reproductor? */
    var hasVideo by mutableStateOf(false)
        private set

    /** Mensaje para la persona si el video no se pudo cargar o reproducir. */
    var error by mutableStateOf<String?>(null)
        private set

    /** El botón debe decir "Pausar" (quiere reproducir y no terminó); si no, "Reproducir". */
    val showsPause: Boolean
        get() = playWhenReady && (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING)

    val isBuffering: Boolean
        get() = hasVideo && error == null && playbackState == Player.STATE_BUFFERING

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            this@RoomPlayer.playbackState = playbackState
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            this@RoomPlayer.playWhenReady = playWhenReady
        }

        override fun onPlayerError(error: PlaybackException) {
            this@RoomPlayer.error = playbackErrorMessage(error.errorCode)
        }
    }

    /**
     * Carga el video de [url] (ya resuelta con `resolveVideoUrl`), arranca en pausa y en el segundo 0.
     * Con [force] = false no hace nada si ya es el video cargado (ver `shouldLoadVideo`).
     * `url = null` quita la cinta.
     */
    fun load(url: String?, force: Boolean) {
        if (!shouldLoadVideo(loadedUrl, url, force)) return
        loadedUrl = url
        error = null
        if (url == null) {
            exo?.let {
                it.stop()
                it.clearMediaItems()
            }
            hasVideo = false
            return
        }
        val p = exo ?: createPlayer()
        p.pause()
        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        hasVideo = true
    }

    /** Quita la cinta y detiene el sonido (cuando se sale de la sala sin cerrar el ViewModel todavía). */
    fun clear() = load(null, force = false)

    fun togglePlay() {
        val p = exo ?: return
        if (!hasVideo || error != null) return
        if (showsPause) {
            p.pause()
            return
        }
        // play() solo pone playWhenReady = true: si el video terminó, o falló, hay que reubicarlo antes.
        when (p.playbackState) {
            Player.STATE_ENDED -> p.seekToDefaultPosition()
            Player.STATE_IDLE -> p.prepare()
        }
        p.play()
    }

    fun pause() {
        exo?.pause()
    }

    /** Vuelve a intentar cargar el video tras un error (conserva la posición). */
    fun retry() {
        val p = exo ?: return
        error = null
        p.prepare()
    }

    fun release() {
        exo?.let {
            it.removeListener(listener)
            it.release()
        }
        exo = null
        player = null
        loadedUrl = null
        hasVideo = false
    }

    private fun createPlayer(): ExoPlayer {
        val p = ExoPlayer.Builder(context).build()
        p.addListener(listener)
        exo = p
        player = p
        return p
    }
}
