package com.isra16.movienight.ui.room

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import com.isra16.movienight.net.ChatMessage
import com.isra16.movienight.net.PauseOnStopTracker
import com.isra16.movienight.net.PipConditions
import com.isra16.movienight.net.PipContent
import com.isra16.movienight.net.PipRequest
import com.isra16.movienight.net.connectionBanner
import com.isra16.movienight.net.formatPlaybackTime
import com.isra16.movienight.net.isBusy
import com.isra16.movienight.net.pipAspectFor
import com.isra16.movienight.net.pipContentFor
import com.isra16.movienight.net.progressFraction
import com.isra16.movienight.net.seekTargetMs
import com.isra16.movienight.room.RoomPhase
import com.isra16.movienight.room.RoomPlayer
import com.isra16.movienight.room.RoomViewModel
import com.isra16.movienight.ui.auth.ErrorText
import com.isra16.movienight.ui.auth.PasswordField
import com.isra16.movienight.ui.auth.PrimaryButton
import com.isra16.movienight.ui.upload.KeepScreenOn

/** Una sala: según la fase muestra la comprobación, el pedido de contraseña, un error o el chat. */
@Composable
fun RoomScreen(onLeave: () -> Unit, vm: RoomViewModel = viewModel()) {
    // Al pasar la app a segundo plano el video se pausa (si no, el sonido sigue con la pantalla apagada), salvo que
    // pase a la ventana flotante (PiP): ahí sigue, y se pausa cuando la ventana se cierra o deja de verse.
    PauseWhenAppStops(onStop = vm::onAppStopped)
    // Le dice a la actividad cuándo tiene sentido una ventana flotante (y la desactiva al salir de la sala).
    PictureInPictureRequests(vm)
    // Mientras el host sube un video la pantalla no se apaga: apagada, Android puede congelar la app y cortar la subida.
    KeepScreenOn(vm.upload.isBusy())
    // En la ventana flotante solo va el video: sin chat, cabecera ni controles ni cuadros.
    if (rememberIsInPictureInPicture(LocalContext.current.findActivity())) {
        PipVideo(vm)
        return
    }
    // El cuadro "Cambiar video" es solo del host: si pierde el rol, el ViewModel lo cierra.
    if (vm.showChangeVideo && vm.isHost) ChangeVideoDialog(vm)
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        RoomHeader(vm = vm, onLeave = onLeave)
        HorizontalDivider()
        when (val phase = vm.phase) {
            RoomPhase.Checking -> CenteredStatus("Comprobando la sala…", vm.notice)
            RoomPhase.Connecting -> CenteredStatus("Entrando a la sala…", vm.notice)
            is RoomPhase.AskPassword -> PasswordPrompt(message = phase.message, onSubmit = vm::submitPassword)
            RoomPhase.Kicked -> KickedState(onLeave = onLeave)
            is RoomPhase.Failed -> FailedState(phase.message, canRetry = phase.canRetry, onRetry = vm::retry, onLeave = onLeave)
            RoomPhase.InRoom -> RoomContent(vm)
        }
    }
}

@Composable
private fun RoomHeader(vm: RoomViewModel, onLeave: () -> Unit) {
    var showViewers by rememberSaveable { mutableStateOf(false) }
    val inRoom = vm.phase == RoomPhase.InRoom

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onLeave) { Text("Salir") }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text("Sala ${vm.roomId}", style = MaterialTheme.typography.titleMedium, maxLines = 1)
            if (inRoom && vm.isHost) {
                Text("Sos el host", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (inRoom) {
            TextButton(onClick = { showViewers = true }) {
                Text(if (vm.viewerCount == 1) "1 conectado" else "${vm.viewerCount} conectados")
            }
        }
    }

    if (showViewers) ViewersDialog(vm = vm, onDismiss = { showViewers = false })
}

@Composable
private fun CenteredStatus(text: String, notice: String?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (notice != null) {
                Text(notice, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PasswordPrompt(message: String?, onSubmit: (String) -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Esta sala tiene contraseña", style = MaterialTheme.typography.titleLarge)
            PasswordField(
                label = "Contraseña de la sala",
                value = password,
                onValueChange = { password = it },
                error = null,
                enabled = true,
                showPassword = showPassword,
                onToggleShow = { showPassword = !showPassword },
                imeAction = ImeAction.Done,
                onDone = { if (password.isNotEmpty()) onSubmit(password) },
            )
            ErrorText(message)
            PrimaryButton("Entrar", busy = false) { if (password.isNotEmpty()) onSubmit(password) }
        }
    }
}

/** El host te sacó (`kicked`): no es un error de conexión ni hay nada que reintentar, solo volver. */
@Composable
private fun KickedState(onLeave: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Te sacaron de la sala", style = MaterialTheme.typography.titleLarge)
            Text("El host te expulsó de esta sala.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            OutlinedButton(onClick = onLeave) { Text("Volver") }
        }
    }
}

@Composable
private fun FailedState(message: String, canRetry: Boolean, onRetry: () -> Unit, onLeave: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No se pudo entrar a la sala", style = MaterialTheme.typography.titleLarge)
            Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            if (canRetry) Button(onClick = onRetry) { Text("Reintentar") }
            OutlinedButton(onClick = onLeave) { Text("Volver") }
        }
    }
}

@Composable
private fun RoomContent(vm: RoomViewModel) {
    var draft by rememberSaveable { mutableStateOf("") }
    val canSend = draft.isNotBlank() && vm.isConnected && !vm.isMuted

    fun send() {
        if (vm.sendChat(draft)) draft = ""
    }

    Column(Modifier.fillMaxSize()) {
        // Con el teclado abierto se esconde el recuadro del video: en un teléfono chico dejaría el chat aplastado.
        val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        if (!keyboardOpen) RoomVideo(
                vm.player,
                videoName = vm.videoName,
                isHost = vm.isHost,
                onTogglePlay = vm::hostTogglePlay,
                onSeek = vm::hostSeekTo,
                onChangeVideo = vm::openChangeVideo,
            )

        // Avance de una subida cuando el cuadro "Cambiar video" está oculto.
        RoomUploadBanner(vm)

        val banner = connectionBanner(vm.isConnected, vm.restart, vm.notice)
        if (banner != null) {
            Text(
                banner,
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
            )
        }

        // reverseLayout: lo más nuevo abajo, y si no estás leyendo mensajes viejos el chat queda pegado al final.
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(vm.messages.asReversed()) { message ->
                MessageRow(message, mine = !message.system && message.userId == vm.myUserId)
            }
        }

        val typing = vm.typingUser
        if (typing != null) {
            Text(
                "$typing está escribiendo…",
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (vm.isMuted) {
            Text(
                "El host te silenció: no podés escribir en el chat.",
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        } else {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = it.take(500)
                        if (it.isNotEmpty()) vm.onTyping()
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Escribí un mensaje") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                )
                Button(onClick = ::send, enabled = canSend) { Text("Enviar") }
            }
        }
    }
}

@Composable
private fun MessageRow(message: ChatMessage, mine: Boolean) {
    if (message.system) {
        Text(
            message.text,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        return
    }

    val bubbleColor = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(12.dp),
            color = bubbleColor,
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    message.user + if (message.isHost) " · host" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (message.isHost) MaterialTheme.colorScheme.primary else textColor,
                )
                message.replyTo?.let { reply ->
                    Text(
                        "↪ ${reply.user}: ${reply.text}",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        maxLines = 2,
                    )
                }
                Text(message.text, style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
        }
    }
}

/**
 * El video de la sala. Los controles van debajo del video y no encima: así no hay nada dibujado sobre
 * la `SurfaceView` del `PlayerView` (que además no usa los controles nativos de Media3).
 *
 * Quien NO es host solo ve una barra de progreso de solo lectura (sin play/pause ni salto: la sala la
 * maneja el host, igual que en `room.html` con `video.controls = false`). Quien es host (confirmado por el
 * server) tiene play/pausa y una barra con la que puede saltar; todo eso llega a la sala (Fase 3C).
 */
@Composable
private fun RoomVideo(
    rp: RoomPlayer,
    videoName: String,
    isHost: Boolean,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onChangeVideo: () -> Unit,
) {
    // La posición no es un evento del reproductor: se consulta unas veces por segundo mientras la vista existe.
    LaunchedEffect(rp, rp.hasVideo) {
        while (true) {
            rp.refreshProgress()
            delay(PROGRESS_REFRESH_MS)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val exo = rp.player
            val error = rp.error
            when {
                !rp.hasVideo -> Text("Sin cinta", style = MaterialTheme.typography.titleMedium, color = Color.White)
                error != null -> Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error, style = MaterialTheme.typography.bodyMedium, color = Color.White, textAlign = TextAlign.Center)
                    OutlinedButton(onClick = rp::retry) { Text("Reintentar") }
                }
                exo != null -> {
                    // El reproductor vive más que esta vista (teclado que la esconde, girar el teléfono): hay que
                    // soltarlo del PlayerView al salir, o cada PlayerView viejo seguiría escuchándolo.
                    val viewHolder = remember { arrayOfNulls<PlayerView>(1) }
                    DisposableEffect(exo) { onDispose { viewHolder[0]?.player = null } }
                    AndroidView(
                        factory = { context ->
                            PlayerView(context).apply { useController = false }.also { viewHolder[0] = it }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            view.player = exo
                            view.keepScreenOn = rp.showsPause // que la pantalla no se apague a mitad de la peli
                        },
                    )
                }
                else -> Unit
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isHost) {
                OutlinedButton(onClick = onTogglePlay, enabled = rp.hasVideo && rp.error == null) {
                    Text(if (rp.showsPause) "Pausar" else "Reproducir")
                }
            }
            if (rp.isBuffering) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(
                if (videoName.isNotEmpty()) videoName else "Sin cinta",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (isHost) TextButton(onClick = onChangeVideo) { Text("Cambiar video") }
        }
        if (rp.hasVideo && rp.error == null) {
            if (isHost) {
                HostSeekBar(positionMs = rp.positionMs, durationMs = rp.durationMs, onSeek = onSeek)
            } else {
                GuestProgressBar(positionMs = rp.positionMs, durationMs = rp.durationMs)
            }
        }
    }
}

/**
 * Barra con la que el host salta en el video. Mientras se arrastra solo se mueve la barra; al soltar se
 * salta (un solo `seek` hacia la sala, no uno por cada pixel del arrastre, como el `seeked` de la web).
 */
@Composable
private fun HostSeekBar(positionMs: Long, durationMs: Long, onSeek: (Float) -> Unit) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val shown = dragFraction ?: progressFraction(positionMs, durationMs)
    val shownMs = if (dragFraction != null) seekTargetMs(shown, durationMs) else positionMs
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Slider(
            value = shown,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let(onSeek)
                dragFraction = null
            },
            modifier = Modifier.weight(1f),
            enabled = durationMs > 0L,
        )
        Text(
            "${formatPlaybackTime(shownMs)} / ${if (durationMs > 0L) formatPlaybackTime(durationMs) else "--:--"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Barra de progreso de solo lectura para quien no es host: no se puede tocar ni arrastrar. */
@Composable
private fun GuestProgressBar(positionMs: Long, durationMs: Long) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LinearProgressIndicator(
            progress = { progressFraction(positionMs, durationMs) },
            modifier = Modifier.weight(1f),
        )
        Text(
            "${formatPlaybackTime(positionMs)} / ${if (durationMs > 0L) formatPlaybackTime(durationMs) else "--:--"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val PROGRESS_REFRESH_MS = 500L

/**
 * Llama a [onStop] cuando la app deja de verse (Home, apagar la pantalla, cerrar la ventana flotante...), pero no
 * cuando la actividad se recrea por un cambio de configuración (tema oscuro, idioma...: ahí el video tiene que
 * seguir; girar el teléfono ya no la recrea, ver `configChanges` en el manifiesto) ni cuando pasa a la ventana
 * flotante (PiP, ahí tampoco se pausa). La decisión vive en [PauseOnStopTracker] (lógica pura con tests);
 * acá solo se le pasan los eventos de Android. Los logs `MovieNightPip` dejan ver el orden real de los eventos.
 */
@Composable
private fun PauseWhenAppStops(onStop: () -> Unit) {
    val activity = LocalContext.current.findActivity()
    val currentOnStop by rememberUpdatedState(onStop)
    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose { }
        val tracker = PauseOnStopTracker(startInPip = activity.isInPictureInPictureMode)

        fun pauseIf(pause: Boolean, why: String) {
            Log.d(PIP_TAG, "$why -> ${if (pause) "PAUSO" else "no pauso"}")
            if (pause) currentOnStop()
        }

        fun state() = "pip=${activity.isInPictureInPictureMode} cambioConfig=${activity.isChangingConfigurations} " +
            "pantalla=${if (activity.isScreenInteractive()) "on" else "off"}"

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    Log.d(PIP_TAG, "ON_START ${state()}")
                    tracker.onStarted()
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_RESUME -> Log.d(PIP_TAG, "$event ${state()}")
                Lifecycle.Event.ON_STOP -> pauseIf(
                    tracker.onStopped(
                        changingConfigurations = activity.isChangingConfigurations,
                        inPipNow = activity.isInPictureInPictureMode,
                        screenOn = activity.isScreenInteractive(),
                    ),
                    "ON_STOP ${state()}",
                )
                else -> Unit
            }
        }
        val pipListener = Consumer<PictureInPictureModeChangedInfo> { info ->
            val stopped = !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            pauseIf(
                tracker.onPipModeChanged(info.isInPictureInPictureMode, stopped, activity.isChangingConfigurations),
                "MODO_PIP=${info.isInPictureInPictureMode} lifecycle=${activity.lifecycle.currentState} ${state()}",
            )
        }
        activity.lifecycle.addObserver(observer)
        activity.addOnPictureInPictureModeChangedListener(pipListener)
        onDispose {
            activity.lifecycle.removeObserver(observer)
            activity.removeOnPictureInPictureModeChangedListener(pipListener)
        }
    }
}

/**
 * Cada vez que cambia lo que decide si una ventana flotante tiene sentido (en la sala, video cargado, sano y
 * reproduciéndose; y la relación de aspecto del video), se lo avisa a la actividad. Si la sala deja de estar sana
 * (error, "te sacaron") las condiciones dejan de cumplirse solas; al salir de esta pantalla (salir de la sala, cerrar
 * sesión...) se desactiva del todo, para que desde la pantalla principal o el login nunca aparezca la ventana flotante.
 */
@Composable
private fun PictureInPictureRequests(vm: RoomViewModel) {
    val host = LocalContext.current.findActivity() as? PipHost
    val rp = vm.player
    val request = PipRequest(
        PipConditions(
            inRoom = vm.phase == RoomPhase.InRoom,
            videoLoaded = rp.hasVideo,
            videoFailed = rp.error != null,
            playing = rp.showsPause,
        ),
        pipAspectFor(rp.videoWidth, rp.videoHeight),
    )
    SideEffect { host?.updatePip(request) }
    DisposableEffect(host) { onDispose { host?.updatePip(PipRequest.OFF) } }
}

/** Lo único que se dibuja dentro de la ventana flotante: el video, o un mensaje corto si la sala dejó de estar sana. */
@Composable
private fun PipVideo(vm: RoomViewModel) {
    val rp = vm.player
    val exo = rp.player
    val content = pipContentFor(
        inRoom = vm.phase == RoomPhase.InRoom,
        kicked = vm.phase == RoomPhase.Kicked,
        videoLoaded = rp.hasVideo,
        videoFailed = rp.error != null,
    )
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (content == PipContent.VIDEO && exo != null) {
            // Igual que en RoomVideo: soltar el reproductor del PlayerView al salir de la composición.
            val viewHolder = remember { arrayOfNulls<PlayerView>(1) }
            DisposableEffect(exo) { onDispose { viewHolder[0]?.player = null } }
            AndroidView(
                factory = { context -> PlayerView(context).apply { useController = false }.also { viewHolder[0] = it } },
                modifier = Modifier.fillMaxSize(),
                update = { view -> view.player = exo },
            )
        } else {
            Text(
                when (content) {
                    PipContent.KICKED -> "Te sacaron de la sala"
                    PipContent.ROOM_UNAVAILABLE -> "Sala no disponible"
                    PipContent.VIDEO_ERROR -> "Error de video"
                    else -> "Sin cinta"
                },
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}
