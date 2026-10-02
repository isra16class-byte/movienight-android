package com.isra16.movienight.room

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.isra16.movienight.net.RoomPosition
import com.isra16.movienight.net.SyncMessage
import com.isra16.movienight.net.planSync
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
 * Fase 3, parte B: la app SIGUE a la sala (recibe `sync` del host, ver [applySync]) pero todavía no
 * emite nada. Para que en la parte C emitir no cause bucles hay dos caminos separados y nunca se mezclan:
 *  - lo que viene del server entra solo por [applySync] y [load] (con la posición de `room-data`);
 *  - lo que hace la persona entra solo por [togglePlay] (y, en la 3C, los saltos del host).
 * Ningún listener del ExoPlayer emite hacia el server: así lo que se aplica por orden del server nunca
 * se confunde con una acción de la persona (la web lo resuelve con una bandera `ignoreSync` y un
 * `setTimeout` de 300 ms; acá no hace falta ninguna de las dos). En la 3C, el emitir se engancha solo
 * al camino de la persona.
 */
class RoomPlayer(private val context: Context) {

    private var exo: ExoPlayer? = null
    private var loadedUrl: String? = null

    private var playWhenReady by mutableStateOf(false)
    private var playbackState by mutableIntStateOf(Player.STATE_IDLE)

    /** Posición y duración (ms) para la barra de progreso de solo lectura; [refreshProgress] las actualiza. */
    var positionMs by mutableLongStateOf(0L)
        private set

    /** 0 si todavía no se conoce. */
    var durationMs by mutableLongStateOf(0L)
        private set

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
     * Carga el video de [url] (ya resuelta con `resolveVideoUrl`). Con [start] (la `position` de
     * `room-data`) arranca donde va la sala y reproduce si la sala no está en pausa; sin [start]
     * arranca en pausa y en el segundo 0 (cinta nueva). Con [force] = false no hace nada si ya es el
     * video cargado (ver `shouldLoadVideo`): al reconectar, el heartbeat siguiente lo realinea.
     * `url = null` quita la cinta.
     */
    fun load(url: String?, force: Boolean, start: RoomPosition? = null) {
        if (!shouldLoadVideo(loadedUrl, url, force)) return
        loadedUrl = url
        error = null
        positionMs = 0L
        durationMs = 0L
        if (url == null) {
            exo?.let {
                it.stop()
                it.clearMediaItems()
            }
            hasVideo = false
            return
        }
        val p = exo ?: createPlayer()
        p.playWhenReady = start?.paused == false
        p.setPlaybackSpeed(1f)
        p.setMediaItem(MediaItem.fromUri(url), start?.timeMs ?: 0L)
        p.prepare()
        hasVideo = true
    }

    /**
     * Sigue al host: aplica un `sync` (play, pause, seek o heartbeat) con la corrección de desfase de
     * `planSync` (umbrales documentados en `SyncLogic.kt`). Solo llamar si la persona NO es host. No emite
     * nada. Se ignora si no hay video o falló (al llegar el próximo heartbeat ya habrá con qué alinearse).
     */
    fun applySync(msg: SyncMessage) {
        val p = exo ?: return
        if (!hasVideo || error != null) return
        val plan = planSync(msg, p.currentPosition)
        plan.seekToMs?.let { p.seekTo(it) }
        if (p.playbackParameters.speed != plan.speed) p.setPlaybackSpeed(plan.speed)
        when (plan.play) {
            true -> {
                // Tras un error o un stop() el reproductor queda en IDLE: reproducir sin preparar no hace nada.
                if (p.playbackState == Player.STATE_IDLE) p.prepare()
                p.play()
            }
            false -> p.pause()
            null -> Unit
        }
    }

    /** Vuelve la velocidad a 1.0 (por ejemplo al pasar a ser host, que ya no sigue a nadie). */
    fun resetSpeed() {
        exo?.setPlaybackSpeed(1f)
    }

    /** Actualiza [positionMs] y [durationMs]; la pantalla lo llama unas veces por segundo. */
    fun refreshProgress() {
        val p = exo
        if (p == null || !hasVideo) {
            positionMs = 0L
            durationMs = 0L
            return
        }
        positionMs = p.currentPosition.coerceAtLeast(0L)
        durationMs = p.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
    }

    /** Quita la cinta y detiene el sonido (cuando se sale de la sala sin cerrar el ViewModel todavía). */
    fun clear() = load(null, force = false)

    /** Play/pause de la persona. En la 3B solo lo usa quien es host y es local (no se emite todavía). */
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
        positionMs = 0L
        durationMs = 0L
    }

    private fun createPlayer(): ExoPlayer {
        val p = ExoPlayer.Builder(context).build()
        p.addListener(listener)
        exo = p
        player = p
        return p
    }
}
