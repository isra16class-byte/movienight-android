package com.isra16.movienight.ui.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.isra16.movienight.ui.auth.GlassFieldDivider
import com.isra16.movienight.ui.auth.GlassLinkButton
import com.isra16.movienight.ui.common.AvatarImage

/** Una acción que espera confirmación: se guarda el id (no la fila) porque la lista puede cambiar mientras se decide. */
private data class AwaitingConfirm(val action: ModerationAction, val viewerId: String, val name: String)

/**
 * "En la sala": la lista de conectados con el estado de cada uno (host, silenciado). Si la persona es host
 * (confirmado por el server), cada fila de otra persona trae un menú con hacer host, silenciar / quitar silencio
 * y expulsar; quien no es host no ve acciones. Hacer host y expulsar piden confirmación.
 *
 * Arriba de la lista va la cinta de la sala (una línea "Viendo" y debajo el nombre, o solo "Sin cinta") para todos; el host ve
 * debajo el botón "Cambiar video" ([onChangeVideo]; quien llama cierra esta lista y abre el cuadro de cambiar). Fase 8, paso 5g:
 * antes eso estaba bajo el video. Después, un separador y "Conectados · N" con la lista (solo ella se desplaza). Solo orden y
 * alineación visual (paso 5m): textos, acciones y llamadas a [vm] no cambian.
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
        // Bloque de la cinta: etiqueta pequeña y el nombre debajo; sin cinta, solo "Sin cinta".
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (vm.videoName.isNotEmpty()) {
                Text("Viendo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(vm.videoName, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                Text("Sin cinta", style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            }
        }
        if (vm.isHost) GlassButton("Cambiar video", busy = false, onClick = onChangeVideo, compact = true)
        GlassFieldDivider()
        // El encabezado queda fuera del desplazamiento: solo la lista (y el aviso de moderación) hace scroll.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Conectados · ${vm.viewers.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

private val VIEWER_ROW_MIN_HEIGHT = 44.dp

/** Igual al `padding(horizontal = 12.dp)` de `GlassLinkButton` (AuthComponents.kt); si cambia allí, cambia aquí. */
private val GLASS_LINK_HORIZONTAL_PADDING = 12.dp

@Composable
private fun ViewerRow(
    viewer: Viewer,
    isMe: Boolean,
    actions: List<ModerationAction>,
    busy: Boolean,
    onAction: (ModerationAction) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // Alto mínimo igual con o sin "Acciones" (el enlace mide 32 dp y sin él la fila era más baja).
    Row(Modifier.fillMaxWidth().heightIn(min = VIEWER_ROW_MIN_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        // El nombre se recorta con elipsis y el sufijo (" · tú, host") queda siempre visible: viewerLabel empieza por el nombre.
        val label = viewerLabel(viewer, isMe)
        val suffix = label.removePrefix(viewer.username)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            // Avatar de 32 dp antes del nombre (Fase 8, «Avatares de cuenta»); sin avatar, la inicial en círculo. La fila conserva su alto mínimo de 44 dp.
            AvatarImage(viewer.avatarId, viewer.username, 32.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                viewer.username,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (suffix.isNotEmpty()) {
                Text(suffix, style = MaterialTheme.typography.bodyLarge, maxLines = 1, softWrap = false)
            }
        }
        if (actions.isNotEmpty()) {
            // El enlace lleva 12 dp de aire a cada lado: el offset (no cambia el tamaño) hace que el texto termine en el borde derecho de la lista.
            Box(Modifier.offset(x = GLASS_LINK_HORIZONTAL_PADDING)) {
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
