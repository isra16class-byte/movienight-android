package com.isra16.movienight.ui.room

import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.view.OrientationEventListener
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.isra16.movienight.R
import com.isra16.movienight.net.FullscreenState
import com.isra16.movienight.net.OrientationRequest
import com.isra16.movienight.net.OverlayState
import com.isra16.movienight.net.RoomLayout
import com.isra16.movienight.net.VideoFit
import com.isra16.movienight.net.connectionBanner
import com.isra16.movienight.net.videoFitButtonLabel
import com.isra16.movienight.net.videoFitName
import com.isra16.movienight.room.RoomPlayer
import com.isra16.movienight.room.RoomViewModel
import kotlinx.coroutines.delay

/*
 * Pantalla completa de la sala (Fase 7C): solo el video, con controles que aparecen al tocar y se esconden solos.
 * Qué vista se muestra, qué orientación se pide y los relojes de los controles viven en `net/FullscreenLogic.kt`
 * (lógica pura con tests); acá solo se conectan con Android. La sincronización sigue en RoomPlayer y RoomViewModel.
 */

/** Guarda el [FullscreenState] (dos booleanos) para `rememberSaveable`. */
internal val FullscreenStateSaver = listSaver<FullscreenState, Boolean>(
    save = { listOf(it.requested, it.portraitHold) },
    restore = { FullscreenState(requested = it[0], portraitHold = it[1]) },
)

/** Fondo semitransparente de los controles sobre el video (el mismo tono oscuro que usa la web). */
internal val Scrim = Color(0xCC0C0616)

private fun OrientationRequest.toActivityOrientation(): Int = when (this) {
    OrientationRequest.FREE -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    OrientationRequest.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    OrientationRequest.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
}

/**
 * Lo que la pantalla completa le pide a la ventana: la orientación, esconder o mostrar las barras del sistema y,
 * mientras se retiene la vertical, escuchar el ángulo del teléfono para soltarla cuando quede derecho.
 *
 * Se llama ANTES del `return` de la ventana flotante (PiP) de `RoomScreen`, así entrar y salir de PiP no suelta ni
 * vuelve a fijar la orientación. Cambiar la orientación llega a la actividad como `onConfigurationChanged` (no la
 * recrea: `configChanges` ya incluye `orientation`) y nunca pasa por `ON_STOP`, así que no pausa el video.
 */
@Composable
internal fun FullscreenWindowEffects(
    layout: RoomLayout,
    orientation: OrientationRequest,
    holdActive: Boolean,
    onPhysicalAngle: (Int) -> Unit,
) {
    val activity = LocalContext.current.findActivity() ?: return
    val view = LocalView.current
    val currentOnAngle by rememberUpdatedState(onPhysicalAngle)

    DisposableEffect(activity, orientation) {
        activity.requestedOrientation = orientation.toActivityOrientation()
        onDispose { }
    }

    // Barras del sistema: ocultas en pantalla completa (al deslizar desde el borde aparecen un momento). Se vuelven
    // a ocultar al reanudar, porque el sistema puede mostrarlas al volver de la ventana flotante.
    DisposableEffect(activity, view, layout) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        val fullscreen = layout == RoomLayout.FULLSCREEN
        fun applyBars() {
            if (fullscreen) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        applyBars()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) applyBars() }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    // Al salir de la sala todo vuelve a como estaba.
    DisposableEffect(activity, view) {
        onDispose {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowCompat.getInsetsController(activity.window, view).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(activity, holdActive) {
        if (!holdActive) return@DisposableEffect onDispose { }
        val listener = object : OrientationEventListener(activity) {
            override fun onOrientationChanged(degrees: Int) = currentOnAngle(degrees)
        }
        // Sin sensor no hay forma de saber cuándo está derecho: se suelta de una vez para no dejarlo fijo en vertical.
        if (listener.canDetectOrientation()) listener.enable() else currentOnAngle(0)
        onDispose { listener.disable() }
    }
}

/**
 * La vista de pantalla completa: el video a toda la ventana (negro alrededor) y encima los controles, en dos grupos
 * con reloj propio. Tocar el video los muestra o los esconde; los avisos de conexión y de silencio no tienen reloj.
 *
 * [viewersDialogOpen]: la lista de conectados (que vive en `RoomScreen`) está abierta; mientras tanto no corre ningún reloj.
 * [videoFit] / [onNextFit]: nivel de ajuste del video (Entero, Medio, Llenar, Cine); cada toque pasa al siguiente. El estado
 * vive en `RoomScreen`.
 */
@Composable
internal fun RoomFullscreen(
    vm: RoomViewModel,
    viewersDialogOpen: Boolean,
    videoFit: VideoFit,
    onNextFit: () -> Unit,
    onExit: () -> Unit,
    onShowViewers: () -> Unit,
) {
    val rp = vm.player
    // ✕ y Atrás vuelven a la vista vertical; para salir de la sala hay que volver primero a la vista normal.
    BackHandler(onBack = onExit)
    VideoProgressTicker(rp)

    val overlay = rememberOverlayController()
    // Panel de reacciones (parte 2B): abierto, fija el grupo lateral como la lista de conectados y no se cierra solo
    // (la web tampoco). Vive aquí: al salir de pantalla completa se descarta y la próxima vez arranca cerrado.
    var reactionPanelOpen by remember { mutableStateOf(false) }
    val asidePinned = viewersDialogOpen || reactionPanelOpen
    LaunchedEffect(asidePinned) {
        overlay.update { s, t -> if (asidePinned) s.pinAside() else s.unpinAside(t) }
    }

    val controlsVisible = overlay.controlsVisible
    val asideVisible = overlay.asideVisible

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // El toque va en el contenedor del video: el botón "Reintentar" (hijo) lo consume antes y no alterna los controles.
        VideoSurface(
            rp,
            Modifier.fillMaxSize().pointerInput(reactionPanelOpen) {
                // Con el panel de reacciones abierto, tocar el video no esconde nada (en la web el panel abierto tampoco se esconde).
                detectTapGestures { if (!reactionPanelOpen) overlay.update { s, t -> s.onTapVideo(t) } }
            },
            fit = videoFit,
        )
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
            Column(
                Modifier.align(Alignment.TopCenter).widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                connectionBanner(vm.isConnected, vm.restart, vm.notice)?.let { FullscreenNotice(it, warning = true) }
                if (vm.isMuted) FullscreenNotice(MUTED_CHAT_NOTICE, warning = false)
            }

            if (rp.hasVideo && rp.error == null && rp.isBuffering) {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(40.dp), color = Color.White)
            }

            AnimatedVisibility(
                visible = vm.isHost && controlsVisible,
                modifier = Modifier.align(Alignment.TopStart),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Text(
                    "Sos el host",
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }

            AnimatedVisibility(
                visible = asideVisible,
                modifier = Modifier.align(Alignment.TopEnd),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Scrim)
                            .clickable {
                                // Con el panel abierto el grupo ya está fijo: refrescar el reloj lo des-fijaría.
                                if (!reactionPanelOpen) overlay.update { s, t -> s.touchAside(t) }
                                onShowViewers()
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_group), contentDescription = "Conectados", tint = Color.White, modifier = Modifier.size(20.dp))
                        Text("${vm.viewerCount}", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    }
                    // Reacciones (parte 2B). Elegir un emoji no toca el reloj: el panel abierto mantiene el grupo fijo.
                    ReactionPicker(
                        open = reactionPanelOpen,
                        onToggle = { reactionPanelOpen = !reactionPanelOpen },
                        onPick = { vm.sendReaction(it) },
                    )
                }
            }

            AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                val onTouch = { overlay.update { s, t -> s.touchControls(t) } }
                PlayerControlsBar(
                    rp = rp,
                    isHost = vm.isHost,
                    onTogglePlay = vm::hostTogglePlay,
                    onSeek = vm::hostSeekTo,
                    onTouch = onTouch,
                ) {
                    // El volumen es solo del invitado (como en la web); la ✕ vuelve a la vista vertical.
                    if (!vm.isHost) VolumeControl(rp, onTouch)
                    // Icono + nombre del nivel actual, para ver en cuál estás sin adivinar; cada toque pasa al siguiente.
                    TextButton(
                        onClick = { onTouch(); onNextFit() },
                        modifier = Modifier.semantics { contentDescription = videoFitButtonLabel(videoFit) },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Icon(
                            painterResource(if (videoFit == VideoFit.FIT) R.drawable.ic_video_fit else R.drawable.ic_video_fill),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        // El botón ya lo describe entero (nivel actual y siguiente): el texto no se lee dos veces.
                        Text(videoFitName(videoFit), modifier = Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.labelLarge)
                    }
                    IconButton(onClick = onExit) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Salir de pantalla completa", tint = Color.White)
                    }
                }
            }
        }
        // Al final, para dibujarse por encima de los controles. Emojis (parte 2B) y comentarios flotantes del chat
        // (parte 2A): solo existen mientras se está en pantalla completa; al salir se descartan junto con esta función.
        ReactionLayer(vm.liveReactions)
        DanmakuLayer(vm.liveChat)
    }
}

/**
 * Estado de los dos grupos de controles ([OverlayState]) con su reloj. Lo usan la vista normal (solo el grupo de
 * controles) y la pantalla completa (los dos), así hay una sola implementación de los tiempos.
 */
@Stable
internal class OverlayController {
    var overlay by mutableStateOf(OverlayState.shown(SystemClock.elapsedRealtime()))
        private set
    var now by mutableLongStateOf(SystemClock.elapsedRealtime())
        internal set

    val controlsVisible: Boolean get() = overlay.controlsVisible(now)
    val asideVisible: Boolean get() = overlay.asideVisible(now)

    /** Aplica un cambio de [OverlayState] con la hora actual (tocar el video, tocar un control...). */
    fun update(change: (OverlayState, Long) -> OverlayState) {
        overlay = change(overlay, SystemClock.elapsedRealtime())
    }
}

/** Crea el [OverlayController] y lo despierta justo cuando un grupo tiene que esconderse. */
@Composable
internal fun rememberOverlayController(): OverlayController {
    val controller = remember { OverlayController() }
    LaunchedEffect(controller.overlay) {
        while (true) {
            val t = SystemClock.elapsedRealtime()
            controller.now = t
            val next = controller.overlay.nextExpiry(t) ?: break
            delay(next - t)
        }
    }
    return controller
}

/**
 * Barra de controles sobre el video, la misma en la vista normal y en pantalla completa. El host tiene play/pausa y
 * una barra para saltar; el invitado, una barra de solo lectura. [trailing] son los botones del extremo (pantalla
 * completa en la vista normal; volumen y ✕ en pantalla completa). [onTouch] refresca el reloj de los controles.
 */
@Composable
internal fun PlayerControlsBar(
    rp: RoomPlayer,
    isHost: Boolean,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onTouch: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit,
) {
    val healthy = rp.hasVideo && rp.error == null
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Scrim).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isHost) {
            IconButton(onClick = { onTouch(); onTogglePlay() }, enabled = healthy) {
                Icon(
                    painterResource(if (rp.showsPause) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = if (rp.showsPause) "Pausar" else "Reproducir",
                    tint = Color.White,
                )
            }
        }
        if (healthy) {
            if (isHost) {
                HostSeekBar(
                    positionMs = rp.positionMs,
                    durationMs = rp.durationMs,
                    onSeek = onSeek,
                    modifier = Modifier.weight(1f),
                    labelColor = Color.White,
                    onInteract = onTouch,
                )
            } else {
                GuestProgressBar(rp.positionMs, rp.durationMs, Modifier.weight(1f), labelColor = Color.White)
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        trailing()
    }
}

/**
 * Volumen del invitado, igual que en la web: el parlante abre y cierra la barra. Es el volumen local del reproductor
 * (no se sincroniza con la sala); no toca `RoomPlayer`.
 */
@Composable
private fun VolumeControl(rp: RoomPlayer, onTouch: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(rp.player?.volume ?: 1f) }
    if (open) {
        Slider(
            value = volume,
            onValueChange = {
                volume = it
                rp.player?.volume = it
                onTouch()
            },
            modifier = Modifier.width(120.dp),
        )
    }
    IconButton(onClick = { open = !open; onTouch() }) {
        Icon(painterResource(R.drawable.ic_volume_up), contentDescription = "Volumen", tint = Color.White)
    }
}

/** Aviso fino arriba al centro: conexión (ámbar, como el de la web) o silencio. Sin reloj: se ve mientras exista. */
@Composable
private fun FullscreenNotice(text: String, warning: Boolean) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (warning) Color(0xD9785000) else Scrim)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.bodySmall,
        color = if (warning) Color(0xFFFFE9B3) else Color.White,
        textAlign = TextAlign.Center,
    )
}
