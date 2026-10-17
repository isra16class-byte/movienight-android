package com.isra16.movienight.ui.room

import android.content.res.Configuration
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import com.isra16.movienight.R
import com.isra16.movienight.net.ChatMessage
import com.isra16.movienight.net.FullscreenState
import com.isra16.movienight.net.PauseOnStopTracker
import com.isra16.movienight.net.DanmakuProfile
import com.isra16.movienight.net.PipConditions
import com.isra16.movienight.net.PipContent
import com.isra16.movienight.net.PipRequest
import com.isra16.movienight.net.ReactionSurface
import com.isra16.movienight.net.RoomLayout
import com.isra16.movienight.net.VideoFit
import com.isra16.movienight.net.connectionBanner
import com.isra16.movienight.net.formatPlaybackTime
import com.isra16.movienight.net.isBusy
import com.isra16.movienight.net.orientationRequestFor
import com.isra16.movienight.net.pipAspectFor
import com.isra16.movienight.net.pipContentFor
import com.isra16.movienight.net.RoomHeaderMode
import com.isra16.movienight.net.portraitVideoHeightDp
import com.isra16.movienight.net.progressFraction
import com.isra16.movienight.net.roomLayoutFor
import com.isra16.movienight.net.roomHeaderModeFor
import com.isra16.movienight.net.videoScale
import com.isra16.movienight.net.seekTargetMs
import com.isra16.movienight.room.RoomPhase
import com.isra16.movienight.room.RoomPlayer
import com.isra16.movienight.room.RoomViewModel
import com.isra16.movienight.ui.auth.ErrorText
import com.isra16.movienight.ui.auth.GlassButton
import com.isra16.movienight.ui.auth.GlassCompactField
import com.isra16.movienight.ui.auth.GlassFieldGroup
import com.isra16.movienight.ui.auth.GlassLinkButton
import com.isra16.movienight.ui.auth.GlassPasswordField
import com.isra16.movienight.ui.upload.KeepScreenOn

/** Una sala: según la fase muestra la comprobación, el pedido de contraseña, un error o el chat. */
@Composable
fun RoomScreen(onLeave: () -> Unit, vm: RoomViewModel = viewModel()) {
    // Al pasar la app a segundo plano el video se pausa (si no, el sonido sigue con la pantalla apagada), salvo que
    // pase a la ventana flotante (PiP): ahí sigue, y se pausa cuando la ventana se cierra o deja de verse.
    PauseWhenAppStops(onStop = vm::onAppStopped, backgroundAudioActive = vm::hasBackgroundAudio)
    // Le dice a la actividad cuándo tiene sentido una ventana flotante (y la desactiva al salir de la sala).
    PictureInPictureRequests(vm)
    // Mientras el host sube un video la pantalla no se apaga: apagada, Android puede congelar la app y cortar la subida.
    KeepScreenOn(vm.upload.isBusy())
    // Este estado vive acá, antes del `return` de la ventana flotante y fuera de la vista que se muestre: si viviera
    // dentro de RoomContent o RoomHeader se perdería al cambiar entre la vista normal y la pantalla completa.
    var fullscreen by rememberSaveable(stateSaver = FullscreenStateSaver) { mutableStateOf(FullscreenState()) }
    var draft by rememberSaveable { mutableStateOf("") }
    // Nivel de ajuste del video en pantalla completa (Entero, Medio, Llenar, Cine): se recuerda mientras la sala siga abierta (también al girar o pasar a PiP) y se
    // pierde al salir de ella. Solo visual y local: no toca la sincronización.
    var videoFit by rememberSaveable { mutableStateOf(VideoFit.FIT) }
    var showViewers by rememberSaveable { mutableStateOf(false) }
    val inRoom = vm.phase == RoomPhase.InRoom
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Horizontal = pantalla completa (la vista normal no sirve acostado); también si se pidió con el botón.
    val layout = roomLayoutFor(inRoom = inRoom, fullscreenRequested = fullscreen.requested, landscape = landscape)
    FullscreenWindowEffects(
        layout = layout,
        orientation = orientationRequestFor(inRoom, fullscreen.requested, fullscreen.portraitHold),
        holdActive = inRoom && fullscreen.portraitHold,
        onPhysicalAngle = { fullscreen = fullscreen.onPhysicalAngle(it) },
    )
    // En la ventana flotante solo va el video: sin chat, cabecera ni controles ni cuadros.
    if (rememberIsInPictureInPicture(LocalContext.current.findActivity())) {
        PipVideo(vm)
        return
    }
    // El cuadro "Cambiar video" es solo del host: si pierde el rol, el ViewModel lo cierra.
    if (vm.showChangeVideo && vm.isHost) ChangeVideoDialog(vm)
    // La lista de conectados (con la moderación) se abre desde la cabecera o desde la pantalla completa.
    if (showViewers) {
        ViewersDialog(
            vm = vm,
            onDismiss = { showViewers = false },
            // "Cambiar video" (solo host): se cierra la lista y se abre el cuadro de cambiar, para que no queden dos ventanas apiladas.
            onChangeVideo = {
                showViewers = false
                vm.openChangeVideo()
            },
        )
    }
    if (layout == RoomLayout.FULLSCREEN) {
        RoomFullscreen(
            vm = vm,
            viewersDialogOpen = showViewers,
            videoFit = videoFit,
            onNextFit = { videoFit = videoFit.next() },
            onExit = { fullscreen = fullscreen.exit() },
            onShowViewers = { showViewers = true },
        )
        return
    }
    // En la sala el bloque de vidrio llega hasta los bordes de la pantalla: solo se deja el teclado y las barras del sistema las cubren las
    // piezas de arriba y de abajo por dentro (Fase 8, paso 5d). Los demás estados siguen dentro de las zonas seguras, como antes.
    Box(Modifier.fillMaxSize()) {
        // Una sola hoja de vidrio a pantalla completa detrás de toda la sala (Fase 8, paso 5e); las piezas de abajo son transparentes.
        if (inRoom) RoomGlassSheet(Modifier.fillMaxSize())
        Column(if (inRoom) Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime) else Modifier.fillMaxSize().safeDrawingPadding()) {
            // Con el teclado abierto, dentro de la sala, la cabecera pasa a una fila compacta (Salir y conectados, sin título ni línea de host)
            // para dejarle su alto al video (`roomHeaderModeFor`, Fase 8, paso 5i). En los demás estados la cabecera siempre es completa.
            val headerMode = if (inRoom) roomHeaderModeFor(WindowInsets.ime.getBottom(LocalDensity.current)) else RoomHeaderMode.FULL
            RoomHeader(vm = vm, onLeave = onLeave, onShowViewers = { showViewers = true }, compact = headerMode == RoomHeaderMode.COMPACT)
            when (val phase = vm.phase) {
                RoomPhase.Checking -> RoomStateCard(Modifier.fillMaxSize()) { CenteredStatus("Comprobando la sala…", vm.notice) }
                RoomPhase.Connecting -> RoomStateCard(Modifier.fillMaxSize()) { CenteredStatus("Entrando a la sala…", vm.notice) }
                is RoomPhase.AskPassword -> RoomStateCard(Modifier.fillMaxSize()) { PasswordPrompt(message = phase.message, onSubmit = vm::submitPassword) }
                RoomPhase.Kicked -> RoomStateCard(Modifier.fillMaxSize()) { KickedState(onLeave = onLeave) }
                is RoomPhase.Failed -> RoomStateCard(Modifier.fillMaxSize()) { FailedState(phase.message, canRetry = phase.canRetry, onRetry = vm::retry, onLeave = onLeave) }
                RoomPhase.InRoom -> RoomContent(
                    vm = vm,
                    draft = draft,
                    onDraftChange = { draft = it },
                    onEnterFullscreen = { fullscreen = fullscreen.enter() },
                )
            }
        }
    }
}

@Composable
private fun RoomHeader(vm: RoomViewModel, onLeave: () -> Unit, onShowViewers: () -> Unit, compact: Boolean) {
    val inRoom = vm.phase == RoomPhase.InRoom

    // Barra de vidrio (Fase 8, paso 5a). Se queda quieta sobre el fondo; fuera de la sala la `safeDrawingPadding` deja libre la barra de estado.
    // Dentro de la sala la cabecera es la pieza de arriba del bloque de vidrio (sin hueco con el video); en los demás estados, suelta.
    RoomGlassBar(
        if (inRoom) Modifier else Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        contentPadding = if (compact) 0.dp else 4.dp,
        flat = inRoom,
    ) {
        // En la sala la pieza llega hasta arriba del todo: este espacio es la barra de estado (el vidrio queda detrás de ella).
        if (inRoom) Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        // Compacta (teclado abierto, solo en la sala): fila de 40 dp con Salir a la izquierda y conectados a la derecha; sin título ni línea de host.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = if (compact) 8.dp else 4.dp).then(if (compact) Modifier.height(40.dp) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onLeave) { Text("Salir") }
            if (compact) {
                Spacer(Modifier.weight(1f))
            } else Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text("Sala ${vm.roomId}", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                if (inRoom && vm.isHost) {
                    Text("Sos el host", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (inRoom) {
                TextButton(onClick = onShowViewers) {
                    Text(if (vm.viewerCount == 1) "1 conectado" else "${vm.viewerCount} conectados")
                }
            }
        }
    }
}

/** Los cuatro estados siguientes van dentro de [RoomStateCard] (Fase 8, paso 5h): la tarjeta pone el centrado, el margen y el fondo. */
@Composable
private fun ColumnScope.CenteredStatus(text: String, notice: String?) {
    CircularProgressIndicator()
    Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    if (notice != null) {
        Text(notice, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ColumnScope.PasswordPrompt(message: String?, onSubmit: (String) -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Text("Esta sala tiene contraseña", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    GlassFieldGroup {
        GlassPasswordField(
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
    }
    ErrorText(message)
    GlassButton("Entrar", busy = false, onClick = { if (password.isNotEmpty()) onSubmit(password) })
}

/** El host te sacó (`kicked`): no es un error de conexión ni hay nada que reintentar, solo volver. */
@Composable
private fun ColumnScope.KickedState(onLeave: () -> Unit) {
    Text("Te sacaron de la sala", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    Text("El host te expulsó de esta sala.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    GlassButton("Volver", busy = false, onClick = onLeave)
}

@Composable
private fun ColumnScope.FailedState(message: String, canRetry: Boolean, onRetry: () -> Unit, onLeave: () -> Unit) {
    Text("No se pudo entrar a la sala", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    if (canRetry) {
        GlassButton("Reintentar", busy = false, onClick = onRetry)
        GlassLinkButton("Volver", enabled = true, onClick = onLeave)
    } else {
        GlassButton("Volver", busy = false, onClick = onLeave)
    }
}

@Composable
private fun RoomContent(vm: RoomViewModel, draft: String, onDraftChange: (String) -> Unit, onEnterFullscreen: () -> Unit) {
    // Con el teclado abierto el video NO se esconde (como en la web): se achica, ver `portraitVideoHeightDp`. El alto que
    // queda sobre el teclado se mide aquí, en la misma pasada de layout, para que el video no parpadee al abrirlo.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val videoHeight = portraitVideoHeightDp(maxWidth.value, maxHeight.value, keyboardOpen).dp
        RoomContentBody(vm, draft, onDraftChange, onEnterFullscreen, videoHeight, keyboardOpen)
    }
}

@Composable
private fun RoomContentBody(
    vm: RoomViewModel,
    draft: String,
    onDraftChange: (String) -> Unit,
    onEnterFullscreen: () -> Unit,
    videoHeight: Dp,
    keyboardOpen: Boolean,
) {
    val canSend = draft.isNotBlank() && vm.isConnected && !vm.isMuted

    fun send() {
        if (vm.sendChat(draft)) onDraftChange("")
    }

    Column(Modifier.fillMaxSize()) {
        RoomVideo(
            vm.player,
            videoHeight = videoHeight,
            isHost = vm.isHost,
            onTogglePlay = vm::hostTogglePlay,
            onSeek = vm::hostSeekTo,
            onEnterFullscreen = onEnterFullscreen,
            liveReactions = vm.liveReactions,
        )

        // Avance de una subida cuando el cuadro "Cambiar video" está oculto.
        RoomUploadBanner(vm)

        val banner = connectionBanner(vm.isConnected, vm.restart, vm.notice)
        if (banner != null) {
            RoomGlassBar(contentPadding = 8.dp, flat = true) {
                Text(
                    banner,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Una sola hoja de vidrio detrás del chat, las reacciones y el campo (Fase 8, paso 5b): la copia borrosa se dibuja UNA vez
        // para toda la hoja y las burbujas son tintes lisos, sin copia borrosa por mensaje.
        val chat = roomChatColors()
        RoomGlassBar(Modifier.weight(1f), contentPadding = 4.dp, flat = true) {
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

            // Reacciones (parte 2D): la fila de la web móvil, encima del campo de chat; se esconde con el teclado abierto,
            // como la web. Silenciado se puede reaccionar (el server solo bloquea el chat), así que sigue visible.
            if (!keyboardOpen) ReactionBar(onPick = { vm.sendReaction(it) }, chipColor = chat.other)

            if (vm.isMuted) {
                Surface(Modifier.fillMaxWidth().padding(8.dp), shape = RoundedCornerShape(12.dp), color = chat.notice) {
                    Text(
                        MUTED_CHAT_NOTICE,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        // Campo fino y sin etiqueta (Fase 8, paso 5k); el lector de pantalla lee "Escribe un mensaje" mientras está vacío.
                        GlassCompactField(
                            value = draft,
                            onValueChange = {
                                onDraftChange(it.take(500))
                                if (it.isNotEmpty()) vm.onTyping()
                            },
                            description = "Escribe un mensaje",
                            imeAction = ImeAction.Send,
                            onAction = { send() },
                        )
                    }
                    GlassButton("Enviar", busy = false, onClick = ::send, modifier = Modifier.width(104.dp), enabled = canSend, compact = true)
                }
            }
            // La hoja llega hasta abajo del todo: este espacio es la barra de navegación. Con el teclado abierto vale 0, porque la
            // `windowInsetsPadding(ime)` de arriba ya consumió esa parte del inset (la barra de navegación queda tapada por el teclado).
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
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

    // Dentro de la hoja de vidrio (5b): tinte liso de `RoomChatColors` y la tinta del vidrio (0E1511 en claro, blanco en oscuro).
    val chat = roomChatColors()
    val bubbleColor = if (mine) chat.mine else chat.other
    val textColor = MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(16.dp),
            color = bubbleColor,
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    message.user + if (message.isHost) " · host" else "",
                    // En el vidrio `primary` es la misma tinta que el texto: el host se distingue en negrita, no por color.
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (message.isHost) FontWeight.Bold else FontWeight.Normal),
                    color = textColor,
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
 * El video de la sala. Los controles van encima del video, como en un reproductor normal (Fase 7C; antes iban
 * debajo): aparecen al tocar el video y se esconden solos a los 3 s. Dibujar Compose encima de la `SurfaceView` del
 * `PlayerView` (que no usa los controles nativos de Media3) ya se comprobó en la pantalla completa. Debajo del
 * video no queda nada: el nombre de la cinta y "Cambiar video" se mudaron a la lista de conectados (Fase 8, paso 5g).
 *
 * Quien NO es host solo ve una barra de progreso de solo lectura (sin play/pause ni salto: la sala la
 * maneja el host, igual que en `room.html` con `video.controls = false`). Quien es host (confirmado por el
 * server) tiene play/pausa y una barra con la que puede saltar; todo eso llega a la sala (Fase 3C).
 *
 * [videoHeight] es el alto del recuadro: el 16:9 de siempre o, con el teclado abierto, uno más bajo (el video no se
 * esconde, queda con bandas a los lados).
 */
@Composable
private fun RoomVideo(
    rp: RoomPlayer,
    videoHeight: Dp,
    isHost: Boolean,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onEnterFullscreen: () -> Unit,
    liveReactions: SharedFlow<String>,
) {
    VideoProgressTicker(rp)
    val overlay = rememberOverlayController()

    Column(Modifier.fillMaxWidth()) {
        RoomVideoBox(
            rp = rp,
            overlay = overlay,
            videoHeight = videoHeight,
            isHost = isHost,
            onTogglePlay = onTogglePlay,
            onSeek = onSeek,
            onEnterFullscreen = onEnterFullscreen,
            liveReactions = liveReactions,
        )
    }
}

/**
 * El video con sus controles encima. Va en una función aparte a propósito: dentro del `Column` de [RoomVideo] el
 * compilador resolvía `AnimatedVisibility` contra el `ColumnScope` de afuera (que Compose bloquea desde el `Box`) y
 * fallaba con "cannot be called in this context with an implicit receiver". Aquí solo hay `BoxScope`.
 */
@Composable
private fun RoomVideoBox(
    rp: RoomPlayer,
    overlay: OverlayController,
    videoHeight: Dp,
    isHost: Boolean,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onEnterFullscreen: () -> Unit,
    liveReactions: SharedFlow<String>,
) {
    // El video ocupa el ancho completo, negro y recto, entre las piezas de vidrio de arriba y de abajo.
    Box(Modifier.fillMaxWidth().height(videoHeight)) {
        // El toque va en el contenedor del video: el botón "Reintentar" (hijo) lo consume antes y no alterna los controles.
        VideoSurface(
            rp,
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { overlay.update { s, t -> s.onTapVideo(t) } }
            },
        )
        if (rp.hasVideo && rp.error == null && rp.isBuffering) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(32.dp), color = Color.White, strokeWidth = 3.dp)
        }
        AnimatedVisibility(
            visible = overlay.controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            PlayerControlsBar(
                rp = rp,
                isHost = isHost,
                onTogglePlay = onTogglePlay,
                onSeek = onSeek,
                onTouch = { overlay.update { s, t -> s.touchControls(t) } },
            ) {
                IconButton(onClick = onEnterFullscreen) {
                    Icon(painterResource(R.drawable.ic_fullscreen), contentDescription = "Pantalla completa", tint = Color.White)
                }
            }
        }
        // Al final, para dibujarse por encima de los controles. Emojis que mandan los demás (parte 2D), como los
        // `.float-emoji` de la web móvil: solo sobre el video, recortados a su caja. Los comentarios no: ya se ven en el chat.
        ReactionLayer(liveReactions, surface = ReactionSurface.PORTRAIT)
    }
}

/**
 * La caja del video: el `PlayerView`, "Sin cinta" o el error con "Reintentar". La usan la vista normal (16:9) y la
 * pantalla completa (toda la ventana). Según [fit]:
 * - [VideoFit.FIT] (el de siempre): el video entero con franjas negras, sin recortar.
 * - [VideoFit.FILL]: lo agranda hasta cubrir todo, sin deformarlo, y recorta lo que sobre (`RESIZE_MODE_ZOOM`).
 * - [VideoFit.MEDIUM] y [VideoFit.CINEMA]: `PlayerView` no los trae. Se deja el `PlayerView` en modo FIT pero con un
 *   tamaño propio (mayor que la ventana, centrado) calculado por `videoScale` con el tamaño real de la ventana y del
 *   video; lo que sobresale queda fuera de la ventana. Mientras el video no informe su tamaño se ve como FIT.
 */
@androidx.annotation.OptIn(UnstableApi::class) // `UnstableApi` usa el RequiresOptIn de AndroidX: el `@OptIn` de Kotlin no sirve
@Composable
internal fun VideoSurface(rp: RoomPlayer, modifier: Modifier, fit: VideoFit = VideoFit.FIT) {
    BoxWithConstraints(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
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
                // El reproductor vive más que esta vista (girar el teléfono, pasar a pantalla
                // completa): hay que soltarlo del PlayerView al salir, o cada PlayerView viejo seguiría escuchándolo.
                val viewHolder = remember { arrayOfNulls<PlayerView>(1) }
                DisposableEffect(exo) { onDispose { viewHolder[0]?.player = null } }
                // Tamaño propio solo para MEDIUM y CINEMA y solo si ya se conoce el tamaño del video.
                val videoSize = rememberVideoSize(exo)
                val shownW = videoSize.width * videoSize.pixelWidthHeightRatio
                val scale = if (fit.usesCustomScale) {
                    videoScale(fit, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), shownW, videoSize.height.toFloat())
                } else {
                    null
                }
                val density = LocalDensity.current
                val videoModifier = if (scale != null) {
                    with(density) { Modifier.requiredSize((shownW * scale).toDp(), (videoSize.height * scale).toDp()) }
                } else {
                    Modifier.fillMaxSize()
                }
                AndroidView(
                    factory = { context ->
                        PlayerView(context).apply { useController = false }.also { viewHolder[0] = it }
                    },
                    modifier = videoModifier,
                    update = { view ->
                        view.player = exo
                        view.resizeMode = if (fit == VideoFit.FILL) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        view.keepScreenOn = rp.showsPause // que la pantalla no se apague a mitad de la peli
                    },
                )
            }
            else -> Unit
        }
    }
}

/**
 * El tamaño del video que se está reproduciendo, siempre al día. Solo se lee del reproductor y se escucha su aviso de
 * cambio (`onVideoSizeChanged`); no toca la sincronización. `VideoSize.UNKNOWN` (0 x 0) mientras no se conoce.
 */
@Composable
private fun rememberVideoSize(player: Player): VideoSize {
    var size by remember(player) { mutableStateOf(player.videoSize) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                size = videoSize
            }
        }
        player.addListener(listener)
        size = player.videoSize // por si cambió entre crear el estado y registrar el aviso
        onDispose { player.removeListener(listener) }
    }
    return size
}

/** La posición no es un evento del reproductor: se consulta unas veces por segundo mientras la vista existe. */
@Composable
internal fun VideoProgressTicker(rp: RoomPlayer) {
    LaunchedEffect(rp, rp.hasVideo) {
        while (true) {
            rp.refreshProgress()
            delay(PROGRESS_REFRESH_MS)
        }
    }
}

/**
 * Barra con la que el host salta en el video. Mientras se arrastra solo se mueve la barra; al soltar se
 * salta (un solo `seek` hacia la sala, no uno por cada pixel del arrastre, como el `seeked` de la web).
 * [onInteract] avisa de cada toque o movimiento (en pantalla completa refresca el reloj de los controles).
 */
@Composable
internal fun HostSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onInteract: () -> Unit = {},
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val shown = dragFraction ?: progressFraction(positionMs, durationMs)
    val shownMs = if (dragFraction != null) seekTargetMs(shown, durationMs) else positionMs
    Row(
        modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Slider(
            value = shown,
            onValueChange = {
                dragFraction = it
                onInteract()
            },
            onValueChangeFinished = {
                dragFraction?.let(onSeek)
                dragFraction = null
                onInteract()
            },
            modifier = Modifier.weight(1f),
            enabled = durationMs > 0L,
        )
        Text(
            "${formatPlaybackTime(shownMs)} / ${if (durationMs > 0L) formatPlaybackTime(durationMs) else "--:--"}",
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
        )
    }
}

/** Barra de progreso de solo lectura para quien no es host: no se puede tocar ni arrastrar. */
@Composable
internal fun GuestProgressBar(
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier.fillMaxWidth(),
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier.padding(horizontal = 12.dp, vertical = 4.dp),
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
            color = labelColor,
        )
    }
}

private const val PROGRESS_REFRESH_MS = 500L

/** Aviso de que el host silenció al usuario (en el campo de texto de la vista normal y arriba en pantalla completa). */
internal const val MUTED_CHAT_NOTICE = "El host te silenció: no podés escribir en el chat."

/**
 * Llama a [onStop] cuando la app deja de verse (Home, apagar la pantalla, cerrar la ventana flotante...), pero no
 * cuando la actividad se recrea por un cambio de configuración (tema oscuro, idioma...: ahí el video tiene que
 * seguir; girar el teléfono ya no la recrea, ver `configChanges` en el manifiesto) ni cuando pasa a la ventana
 * flotante (PiP, ahí tampoco se pausa). La decisión vive en [PauseOnStopTracker] (lógica pura con tests);
 * acá solo se le pasan los eventos de Android. Los logs `MovieNightPip` dejan ver el orden real de los eventos.
 */
@Composable
private fun PauseWhenAppStops(onStop: () -> Unit, backgroundAudioActive: () -> Boolean) {
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
                        backgroundAudioActive = backgroundAudioActive(),
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
        // Emojis y comentarios de los demás también en la ventana flotante (parte 2D), en chico: solo se ven, desde
        // aquí no se puede mandar nada. Son capas de dibujo sin toques; no tocan el reproductor ni la sincronización.
        if (content == PipContent.VIDEO) {
            ReactionLayer(vm.liveReactions, surface = ReactionSurface.PIP)
            DanmakuLayer(vm.liveChat, profile = DanmakuProfile.PIP)
        }
    }
}
