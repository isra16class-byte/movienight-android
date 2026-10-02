package com.isra16.movienight.ui.room

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.isra16.movienight.net.ChatMessage
import com.isra16.movienight.room.RoomPhase
import com.isra16.movienight.room.RoomViewModel
import com.isra16.movienight.ui.auth.ErrorText
import com.isra16.movienight.ui.auth.PasswordField
import com.isra16.movienight.ui.auth.PrimaryButton

/** Una sala: según la fase muestra la comprobación, el pedido de contraseña, un error o el chat. */
@Composable
fun RoomScreen(onLeave: () -> Unit, vm: RoomViewModel = viewModel()) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        RoomHeader(vm = vm, onLeave = onLeave)
        HorizontalDivider()
        when (val phase = vm.phase) {
            RoomPhase.Checking -> CenteredStatus("Comprobando la sala…", vm.notice)
            RoomPhase.Connecting -> CenteredStatus("Entrando a la sala…", vm.notice)
            is RoomPhase.AskPassword -> PasswordPrompt(message = phase.message, onSubmit = vm::submitPassword)
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

    if (showViewers) {
        AlertDialog(
            onDismissRequest = { showViewers = false },
            title = { Text("En la sala") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    vm.viewers.forEach { viewer ->
                        Text(
                            viewer.username + if (viewer.isHost) " (host)" else "",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showViewers = false }) { Text("Cerrar") } },
        )
    }
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
        if (!keyboardOpen) {
            // El reproductor llega en la Fase 3: por ahora solo se ve qué cinta tiene la sala.
            Surface(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                        Text(
                            if (vm.videoName.isNotEmpty()) vm.videoName else "Sin cinta",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                        Text(
                            "El reproductor llega en la próxima etapa.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        val banner = when {
            !vm.isConnected -> vm.notice ?: "Reconectando…"
            else -> vm.notice
        }
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
