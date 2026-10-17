package com.isra16.movienight.ui.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.isra16.movienight.net.ModerationAction
import com.isra16.movienight.net.Viewer
import com.isra16.movienight.net.actionLabel
import com.isra16.movienight.net.availableActions
import com.isra16.movienight.net.confirmationFor
import com.isra16.movienight.net.viewerLabel
import com.isra16.movienight.room.RoomViewModel
import com.isra16.movienight.ui.auth.GlassButton
import com.isra16.movienight.ui.auth.GlassDialog
import com.isra16.movienight.ui.auth.GlassLinkButton

/** Una acción que espera confirmación: se guarda el id (no la fila) porque la lista puede cambiar mientras se decide. */
private data class AwaitingConfirm(val action: ModerationAction, val viewerId: String, val name: String)

/**
 * "En la sala": la lista de conectados con el estado de cada uno (host, silenciado). Si la persona es host
 * (confirmado por el server), cada fila de otra persona trae un menú con hacer host, silenciar / quitar silencio
 * y expulsar; quien no es host no ve acciones. Hacer host y expulsar piden confirmación.
 *
 * Arriba de la lista va la cinta de la sala ("Viendo: nombre", o "Sin cinta") para todos; el host ve debajo "Cambiar video"
 * ([onChangeVideo]; quien llama cierra esta lista y abre el cuadro de cambiar). Fase 8, paso 5g: antes eso estaba bajo el video.
 */
@Composable
fun ViewersDialog(vm: RoomViewModel, onDismiss: () -> Unit, onChangeVideo: () -> Unit) {
    var awaiting by remember { mutableStateOf<AwaitingConfirm?>(null) }

    // Un problema de una vez anterior ya no dice nada al volver a abrir la lista.
    LaunchedEffect(Unit) { vm.clearModerationMessage() }

    // Ventana de vidrio (Fase 8, paso 5f). La lista conserva su desplazamiento con un alto máximo, como antes dentro del AlertDialog.
    GlassDialog(
        onDismissRequest = onDismiss,
        title = "En la sala",
        confirmButton = { GlassButton("Cerrar", busy = false, onClick = onDismiss, modifier = Modifier.width(120.dp)) },
    ) {
        Text(
            if (vm.videoName.isNotEmpty()) "Viendo: ${vm.videoName}" else "Sin cinta",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
        if (vm.isHost) GlassLinkButton("Cambiar video", enabled = true, onClick = onChangeVideo)
        Column(
            Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val message = vm.moderationMessage
            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            vm.viewers.forEach { viewer ->
                ViewerRow(
                    viewer = viewer,
                    isMe = vm.mySocketId != null && viewer.id == vm.mySocketId,
                    actions = availableActions(viewer, vm.mySocketId, vm.isHost, vm.isConnected),
                    busy = viewer.id in vm.busyViewerIds,
                    onAction = { action ->
                        if (confirmationFor(action, viewer.username) == null) {
                            vm.moderate(action, viewer.id)
                        } else {
                            awaiting = AwaitingConfirm(action, viewer.id, viewer.username)
                        }
                    },
                )
            }
        }
    }

    val pending = awaiting
    if (pending != null) {
        val confirmation = confirmationFor(pending.action, pending.name)
        if (confirmation != null) {
            GlassDialog(
                onDismissRequest = { awaiting = null },
                title = confirmation.title,
                confirmButton = {
                    GlassButton(
                        confirmation.confirmLabel,
                        busy = false,
                        onClick = {
                            vm.moderate(pending.action, pending.viewerId)
                            awaiting = null
                        },
                        modifier = Modifier.width(150.dp),
                    )
                },
                dismissButton = { GlassLinkButton("Cancelar", enabled = true, onClick = { awaiting = null }) },
            ) {
                Text(confirmation.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ViewerRow(
    viewer: Viewer,
    isMe: Boolean,
    actions: List<ModerationAction>,
    busy: Boolean,
    onAction: (ModerationAction) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            viewerLabel(viewer, isMe),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (actions.isNotEmpty()) {
            Box {
                GlassLinkButton(if (busy) "…" else "Acciones", enabled = !busy, onClick = { menuOpen = true })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    actions.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(actionLabel(action, viewer)) },
                            onClick = {
                                menuOpen = false
                                onAction(action)
                            },
                        )
                    }
                }
            }
        }
    }
}
