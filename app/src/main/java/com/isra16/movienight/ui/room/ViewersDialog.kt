package com.isra16.movienight.ui.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/** Una acción que espera confirmación: se guarda el id (no la fila) porque la lista puede cambiar mientras se decide. */
private data class AwaitingConfirm(val action: ModerationAction, val viewerId: String, val name: String)

/**
 * "En la sala": la lista de conectados con el estado de cada uno (host, silenciado). Si la persona es host
 * (confirmado por el server), cada fila de otra persona trae un menú con hacer host, silenciar / quitar silencio
 * y expulsar; quien no es host no ve acciones. Hacer host y expulsar piden confirmación.
 */
@Composable
fun ViewersDialog(vm: RoomViewModel, onDismiss: () -> Unit) {
    var awaiting by remember { mutableStateOf<AwaitingConfirm?>(null) }

    // Un problema de una vez anterior ya no dice nada al volver a abrir la lista.
    LaunchedEffect(Unit) { vm.clearModerationMessage() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("En la sala") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
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
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )

    val pending = awaiting
    if (pending != null) {
        val confirmation = confirmationFor(pending.action, pending.name)
        if (confirmation != null) {
            AlertDialog(
                onDismissRequest = { awaiting = null },
                title = { Text(confirmation.title) },
                text = { Text(confirmation.text) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            vm.moderate(pending.action, pending.viewerId)
                            awaiting = null
                        },
                    ) { Text(confirmation.confirmLabel) }
                },
                dismissButton = { TextButton(onClick = { awaiting = null }) { Text("Cancelar") } },
            )
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
                TextButton(onClick = { menuOpen = true }, enabled = !busy) { Text(if (busy) "…" else "Acciones") }
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
