package com.isra16.movienight.room

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.isra16.movienight.net.HostReference
import com.isra16.movienight.net.RoomPosition
import com.isra16.movienight.net.SyncMessage
import com.isra16.movienight.net.SyncType
import com.isra16.movienight.net.hostReferenceFrom
import com.isra16.movienight.net.planReadyResync
import com.isra16.movienight.net.planRejoin
import com.isra16.movienight.net.planSync
import com.isra16.movienight.net.shouldReportBuffering
import com.isra16.movienight.net.updateHostReference
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
 * Fase 3: la app SIGUE a la sala si no es host (recibe `sync`, ver [applySync]) y la MANEJA si es host
 * (3C). Para que emitir no cause bucles hay dos caminos separados que nunca se mezclan:
 *  - lo que viene del server entra solo por [applySync], [load] (con la posición de `room-data`) y
 *    [setSubtitle]: ninguno devuelve ni emite nada;
 *  - lo que hace la persona entra solo por [togglePlay], [seekTo] y [pause], que DEVUELVEN el `sync`
 *    que corresponde emitir (o `null`); el que emite es el [RoomViewModel], solo si es host.
 * Ningún listener del ExoPlayer emite `sync` hacia el server: así lo que se aplica por orden del server
 * nunca se confunde con una acción de la persona (la web lo resuelve con una bandera `ignoreSync` y un
 * `setTimeout` de 300 ms; acá no hace falta ninguna de las dos). El único listener que avisa algo al
 * server es el del buffering ([onBufferingChange]), que no mueve el video de nadie.
 */
class RoomPlayer(private val context: Context) {

    private var exo: ExoPlayer? = null
    private var loadedUrl: String? = null

    /** Subtítulo de la sala (URL absoluta del `.vtt`), o `null`. Sobrevive a `video-changed`, igual que en el server. */
    private var subtitleUrl: String? = null

    /** El server confirmó que la persona es host (`host-status`): no sigue a nadie. Ver [setHostRole]. */
    private var isHostRole = false

    /** Último valor de buffering avisado (o que se habría avisado sin conexión), para avisar solo los cambios. */
    private var reportedBuffering = false

    /**
     * Se llama (hilo principal) cuando cambia "el video quiere reproducir pero se quedó sin datos" (ver
     * `shouldReportBuffering`); el [RoomViewModel] lo manda como `buffering-status`.
     */
    var onBufferingChange: ((Boolean) -> Unit)? = null

    /** Momento (reloj del sistema) del último salto por heartbeat; 0 = ninguno. Ver `planSync`. */
    private var lastHardSeekAt = 0L

    /** Lo último que se supo del host, para estimar dónde está ahora (ver `planReadyResync`). */
    private var hostRef: HostReference? = null

    /** Hay un salto o una carga reciente: al quedar lista, corregir una vez con `planReadyResync`. */
    private var resyncPending = false

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
            if (playbackState == Player.STATE_READY && resyncPending) {
                resyncPending = false
                resyncToHost()
            }
            updateBufferingReport()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            this@RoomPlayer.playWhenReady = playWhenReady
            updateBufferingReport()
        }

        override fun onPlayerError(error: PlaybackException) {
            this@RoomPlayer.error = playbackErrorMessage(error.errorCode)
            updateBufferingReport()
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
        if (!shouldLoadVideo(loadedUrl, url, force)) {
            // Reconexión con el mismo video: ver [alignToRoom].
            if (url != null && start != null) alignToRoom(start)
            return
        }
        loadedUrl = url
        error = null
        lastHardSeekAt = 0L
        hostRef = start?.let { hostReferenceFrom(it, SystemClock.elapsedRealtime()) }
        resyncPending = start != null && !isHostRole
        positionMs = 0L
        durationMs = 0L
        if (url == null) {
            exo?.let {
                it.stop()
                it.clearMediaItems()
            }
            hasVideo = false
            updateBufferingReport()
            return
        }
        val p = exo ?: createPlayer()
        p.playWhenReady = start?.paused == false
        p.setPlaybackSpeed(1f)
        p.setMediaItem(buildMediaItem(url), start?.timeMs ?: 0L)
        p.prepare()
        hasVideo = true
    }

    /**
     * `room-data` con el video ya cargado (reconexión): se pone el reproductor donde está la sala. Vale para
     * invitados y para el host (ver `planRejoin`); el host no guarda referencia porque él es la referencia.
     */
    private fun alignToRoom(start: RoomPosition) {
        val p = exo ?: return
        if (!hasVideo || error != null) return
        val plan = planRejoin(start, p.currentPosition)
        plan.seekToMs?.let {
            p.seekTo(it)
            positionMs = it
        }
        p.playWhenReady = plan.playWhenReady
        hostRef = if (isHostRole) null else hostReferenceFrom(start, SystemClock.elapsedRealtime())
        Log.d(TAG, "reconecto: sala en ${start.timeMs}ms paused=${start.paused}, salto a ${plan.seekToMs}")
    }

    private fun buildMediaItem(url: String): MediaItem {
        val builder = MediaItem.Builder().setUri(url)
        subtitleUrl?.let { sub ->
            // El server siempre convierte a WebVTT. Mismos datos que la web: idioma "es" y activo por defecto.
            builder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage("es")
                        .setLabel("Subtítulos")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build(),
                ),
            )
        }
        return builder.build()
    }

    /**
     * Subtítulo de la sala (`room-data.subtitleFile` o `subtitle-changed`), URL ya resuelta; `null` = sin
     * subtítulo. Llamarlo ANTES de [load] al entrar, para que el video cargue ya con él. Si ya hay un video
     * cargado y el subtítulo cambió, se vuelve a preparar el mismo video en la posición actual y con la
     * reproducción como estaba (Media3 no deja agregar un subtítulo a un `MediaItem` ya cargado): puede haber
     * un instante de carga. Quien sigue al host se realinea al quedar lista; el host no (él es la referencia).
     */
    fun setSubtitle(url: String?) {
        if (url == subtitleUrl) return
        subtitleUrl = url
        val p = exo ?: return
        val video = loadedUrl ?: return
        if (!hasVideo) return
        p.setMediaItem(buildMediaItem(video), p.currentPosition)
        p.prepare()
        if (!isHostRole) resyncPending = true
    }

    /**
     * Rol confirmado por el server (`host-status`). Quien es host no sigue a nadie: se le quita la
     * velocidad del seguimiento y cualquier corrección pendiente ([applySync] y el realineo al quedar
     * listo dejan de aplicarse), para que una referencia vieja nunca mueva el video del host.
     */
    fun setHostRole(host: Boolean) {
        isHostRole = host
        if (host) {
            exo?.setPlaybackSpeed(1f)
            resyncPending = false
            hostRef = null
        }
    }

    /**
     * Sigue al host: aplica un `sync` (play, pause, seek o heartbeat) con la corrección de desfase de
     * `planSync` (umbrales documentados en `SyncLogic.kt`). Solo llamar si la persona NO es host. No emite
     * nada. Se ignora si no hay video o falló (al llegar el próximo heartbeat ya habrá con qué alinearse).
     */
    fun applySync(msg: SyncMessage) {
        if (isHostRole) return
        val p = exo ?: return
        if (!hasVideo || error != null) return
        hostRef = updateHostReference(hostRef, msg, SystemClock.elapsedRealtime())
        val sinceHardSeek = if (lastHardSeekAt == 0L) Long.MAX_VALUE else SystemClock.elapsedRealtime() - lastHardSeekAt
        val plan = planSync(
            msg,
            localPositionMs = p.currentPosition,
            playerReady = p.playbackState == Player.STATE_READY,
            msSinceLastHardSeek = sinceHardSeek,
        )
        plan.seekToMs?.let {
            if (msg.type == SyncType.HEARTBEAT) lastHardSeekAt = SystemClock.elapsedRealtime()
            resyncPending = true
            p.seekTo(it)
        }
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

    /**
     * Una sola corrección al quedar lista tras un salto o la carga: salta a donde estima que está el host
     * ahora. No arma otra corrección (`resyncPending` ya está en false), así que no puede encadenarse.
     */
    private fun resyncToHost() {
        if (isHostRole) return
        val p = exo ?: return
        val target = planReadyResync(hostRef, p.currentPosition, SystemClock.elapsedRealtime()) ?: return
        p.seekTo(target)
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

    /**
     * Play/pause de la persona (solo la usa quien es host). Devuelve el `sync` que hay que emitir (`pause` o
     * `play` con la posición resultante), o `null` si no se hizo nada.
     */
    fun togglePlay(): SyncMessage? {
        val p = exo ?: return null
        if (!hasVideo || error != null) return null
        if (showsPause) {
            p.pause()
            return SyncMessage(SyncType.PAUSE, p.currentPosition.coerceAtLeast(0L), null)
        }
        // play() solo pone playWhenReady = true: si el video terminó, o falló, hay que reubicarlo antes.
        when (p.playbackState) {
            Player.STATE_ENDED -> p.seekToDefaultPosition()
            Player.STATE_IDLE -> p.prepare()
        }
        p.play()
        return SyncMessage(SyncType.PLAY, p.currentPosition.coerceAtLeast(0L), null)
    }

    /**
     * Salto de la persona (solo quien es host, desde la barra). Devuelve el `seek` a emitir, o `null` si no
     * hay video. [positionMs] se actualiza ya, para que la barra no vuelva atrás hasta el próximo refresco.
     */
    fun seekTo(targetMs: Long): SyncMessage? {
        val p = exo ?: return null
        if (!hasVideo || error != null) return null
        val target = targetMs.coerceAtLeast(0L)
        p.seekTo(target)
        positionMs = target
        return SyncMessage(SyncType.SEEK, target, null)
    }

    /** Pausa (la app pasó a segundo plano). Devuelve el `pause` a emitir si estaba reproduciendo; si no, `null`. */
    fun pause(): SyncMessage? {
        val p = exo ?: return null
        val wasPlaying = showsPause && hasVideo && error == null
        p.pause()
        return if (wasPlaying) SyncMessage(SyncType.PAUSE, p.currentPosition.coerceAtLeast(0L), null) else null
    }

    /**
     * El `heartbeat` del host (posición + si está en pausa), o `null` si no hay un video sano que reportar:
     * con la cinta caída no se manda, o el host le pondría la sala en el segundo 0 y en pausa a todos.
     * "En pausa" = lo contrario de [showsPause]: un video que terminó cuenta como pausado.
     */
    fun heartbeat(): SyncMessage? {
        val p = exo ?: return null
        if (!hasVideo || error != null) return null
        return SyncMessage(SyncType.HEARTBEAT, p.currentPosition.coerceAtLeast(0L), paused = !showsPause)
    }

    /** Avisa el estado de buffering actual si es verdadero; se usa tras (re)unirse a la sala, donde el server empieza de cero. */
    fun resendBufferingState() {
        if (reportedBuffering) onBufferingChange?.invoke(true)
    }

    private fun updateBufferingReport() {
        val now = shouldReportBuffering(hasVideo, error != null, playWhenReady, playbackState == Player.STATE_BUFFERING)
        if (now == reportedBuffering) return
        reportedBuffering = now
        exo?.let {
            // Para distinguir red lenta (R2, esperable) de un fallo de la app: sin datos por delante => se agotó el buffer.
            Log.d(TAG, "buffering=$now pos=${it.currentPosition}ms bufferedAhead=${it.bufferedPosition - it.currentPosition}ms")
        }
        onBufferingChange?.invoke(now)
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
        reportedBuffering = false
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

    private companion object {
        const val TAG = "MovieNightSync"
    }
}
