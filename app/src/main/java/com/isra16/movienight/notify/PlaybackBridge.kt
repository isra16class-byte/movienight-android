package com.isra16.movienight.notify

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.isra16.movienight.net.PlaybackServiceAction
import com.isra16.movienight.net.PlaybackSnapshot
import com.isra16.movienight.net.playbackNotificationText
import com.isra16.movienight.net.playbackNotificationTitle
import com.isra16.movienight.net.playbackServiceAction

private const val TAG = "MovieNightPlayback"

/**
 * Lo que hace la sala cuando la notificación (o un botón de auriculares) pide reproducir o pausar. Lo implementa el
 * `RoomViewModel`: así el host emite el `sync` igual que desde la sala y el invitado solo mueve su reproductor.
 */
interface PlaybackControls {
    fun onRemotePlay()
    fun onRemotePause()
}

/**
 * Puente a nivel de app (Fase 7B, mismo patrón que [UploadKeepAlive]) entre la sala y el servicio de reproducción.
 * NO es dueño del reproductor: el `RoomPlayer` y su ExoPlayer siguen en el `RoomViewModel`. El puente solo guarda,
 * mientras la sala lo pide, una referencia al ExoPlayer (envuelto en [SessionPlayer]) para que [PlaybackService] lo
 * ponga en una `MediaSession`, y arranca/detiene ese servicio según `playbackServiceAction` (lógica pura con tests).
 *
 * Todo se llama desde el hilo principal. El servicio se arranca conectando un `MediaController` a su sesión (así
 * se enlaza sin pasar por `startForegroundService`, que exige llamar a `startForeground` enseguida); es Media3 quien
 * lo promueve a primer plano cuando el video reproduce. Si Android no lo deja (segundo plano en Android 12+), se avisa
 * por [onStartRefused] y la sala sigue como antes de la 7B: se pausa al dejar de verse.
 */
@OptIn(UnstableApi::class)
class PlaybackBridge(context: Context) {
    private val app = context.applicationContext

    private var owner: Any? = null
    private var controls: PlaybackControls? = null
    private var exo: Player? = null
    private var title: String = ""
    private var future: ListenableFuture<MediaController>? = null
    private var service: PlaybackService? = null
    private var startRefused = false

    /** El reproductor que envuelve el servicio, o `null` si no hay sesión pedida. */
    val sessionPlayer: Player?
        get() = exo?.let { SessionPlayer(it) }

    /** ¿Hay servicio de audio pedido por [who]? Con eso el audio sigue al apagar la pantalla o ir a otra app. */
    fun isActive(who: Any): Boolean = owner === who && future != null && !startRefused

    /**
     * La sala [who] avisa su estado. [exoPlayer] es el ExoPlayer de su `RoomPlayer` (o `null` si todavía no hay
     * cinta). Arranca o detiene el servicio según corresponda; si hay una sala distinta ya dueña del servicio, se ignora.
     */
    fun update(who: Any, exoPlayer: Player?, controls: PlaybackControls, videoName: String, snapshot: PlaybackSnapshot) {
        if (future != null && owner !== who) return
        title = playbackNotificationTitle(videoName)
        if (!snapshot.playing) startRefused = false
        when (playbackServiceAction(active = future != null, snapshot, startRefused)) {
            PlaybackServiceAction.START -> if (exoPlayer != null) start(who, exoPlayer, controls)
            PlaybackServiceAction.STOP -> stop(who)
            PlaybackServiceAction.NONE -> Unit
        }
    }

    private fun start(who: Any, exoPlayer: Player, controls: PlaybackControls) {
        owner = who
        exo = exoPlayer
        this.controls = controls
        try {
            val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
            val f = MediaController.Builder(app, token).buildAsync()
            future = f
            f.addListener({
                try {
                    f.get()
                } catch (e: Exception) {
                    Log.d(TAG, "no se pudo enlazar el servicio: $e")
                    if (future === f) onStartRefused()
                }
            }, ContextCompat.getMainExecutor(app))
            Log.d(TAG, "pido el servicio de audio")
        } catch (e: RuntimeException) {
            Log.d(TAG, "no se pudo pedir el servicio: $e")
            onStartRefused()
        }
    }

    /** La sala [who] termina (salir, expulsión, error, sin cinta...): si es la dueña, se detiene el servicio. */
    fun stop(who: Any) {
        if (owner !== who) return
        release()
    }

    /** Detiene todo, sea de quien sea (cerrar sesión). */
    fun stopAll() = release()

    /** Android no dejó arrancar o promover el servicio (segundo plano en Android 12+): vale como "sin servicio". */
    fun onStartRefused() {
        Log.d(TAG, "Android no dejó arrancar el servicio de audio")
        startRefused = true
        release()
    }

    /** Quita la conexión de la sala (la X de recientes): los pedidos de la notificación ya no llegan a ninguna sala. */
    fun detachControls() {
        controls = null
    }

    internal fun attach(s: PlaybackService) {
        service = s
    }

    internal fun detach(s: PlaybackService) {
        if (service === s) service = null
    }

    private fun release() {
        val f = future
        future = null
        owner = null
        controls = null
        exo = null
        f?.let { MediaController.releaseFuture(it) }
        service?.stopFromBridge()
        service = null
    }

    /**
     * El ExoPlayer visto por la `MediaSession`. Reproducir/pausar (notificación, auriculares) NO tocan el ExoPlayer
     * directamente: pasan por [PlaybackControls] para que el host emita el `sync`; sin sala conectada (por ejemplo al
     * quitar la app de recientes) se aplican al reproductor. Solo se ofrecen reproducir y pausar.
     */
    private inner class SessionPlayer(wrapped: Player) : ForwardingPlayer(wrapped) {
        override fun play() = request(true)
        override fun pause() = request(false)
        override fun setPlayWhenReady(playWhenReady: Boolean) = request(playWhenReady)

        private fun request(play: Boolean) {
            val c = controls
            when {
                c == null -> if (play) super.play() else super.pause()
                play -> c.onRemotePlay()
                else -> c.onRemotePause()
            }
        }

        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon().removeAll(*DISABLED_COMMANDS).build()

        override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

        override fun getMediaMetadata(): MediaMetadata =
            MediaMetadata.Builder()
                .setTitle(title.ifEmpty { playbackNotificationTitle("") })
                .setArtist(playbackNotificationText(playWhenReady))
                .build()
    }

    private companion object {
        /** Todo lo que no sea reproducir/pausar: la sala no admite adelantar, atrasar ni cambiar de cinta desde afuera. */
        val DISABLED_COMMANDS = intArrayOf(
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD,
            Player.COMMAND_STOP,
            Player.COMMAND_SET_SPEED_AND_PITCH,
            Player.COMMAND_SET_SHUFFLE_MODE,
            Player.COMMAND_SET_REPEAT_MODE,
            Player.COMMAND_SET_MEDIA_ITEM,
            Player.COMMAND_CHANGE_MEDIA_ITEMS,
            Player.COMMAND_PREPARE,
        )
    }
}
