package com.isra16.movienight.ui.room

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.isra16.movienight.home.LibraryState
import com.isra16.movienight.net.LibraryItem
import com.isra16.movienight.net.MAX_SIMPLE_PUT_LABEL
import com.isra16.movienight.net.UploadState
import com.isra16.movienight.net.canCancel
import com.isra16.movienight.net.finishingLabel
import com.isra16.movienight.net.formatFileSize
import com.isra16.movienight.net.isBusy
import com.isra16.movienight.net.progressLabel
import com.isra16.movienight.room.RoomViewModel
import com.isra16.movienight.ui.auth.ErrorText
import com.isra16.movienight.ui.upload.UploadStatusBody
import com.isra16.movienight.ui.upload.rememberNotificationGate

/**
 * "Cambiar video" (solo el host, Fase 4B): elegir uno de la biblioteca (pide confirmar, porque lo ven todos y
 * arranca desde el principio) o subir uno nuevo del teléfono, que al terminar pasa a ser el video de la sala.
 *
 * Una subida en curso se puede ocultar con "Ocultar": sigue, y [RoomUploadBanner] muestra cómo va.
 */
@Composable
fun ChangeVideoDialog(vm: RoomViewModel) {
    var confirming by remember { mutableStateOf<LibraryItem?>(null) }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        vm.onVideoPicked(uri)
    }
    val askNotifications = rememberNotificationGate()
    val upload = vm.upload
    val busy = upload.isBusy() || vm.isChangingVideo

    AlertDialog(
        onDismissRequest = vm::hideChangeVideo,
        title = { Text("Cambiar el video de la sala") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    UploadStatusBody(
                        state = upload,
                        idle = {
                            Text(
                                "Elegí un video del teléfono (hasta $MAX_SIMPLE_PUT_LABEL). Se sube a la biblioteca " +
                                    "y, al terminar, pasa a ser el video de la sala.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = { askNotifications { pickVideo.launch(arrayOf("video/*")) } },
                                enabled = !vm.isChangingVideo,
                            ) { Text("Subir uno nuevo") }
                        },
                        onPick = { askNotifications { pickVideo.launch(arrayOf("video/*")) } },
                        onCancel = vm::cancelUpload,
                        onRetry = vm::retryUpload,
                        onDismiss = vm::dismissUpload,
                        uploadingHint = "Podés usar otras apps, pero no salgas de la sala mientras sube: si salís, la subida se corta.",
                    )
                }
                if (vm.isChangingVideo) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Cambiando el video de la sala…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                vm.changeVideoError?.let { message -> item { ErrorText(message) } }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        HorizontalDivider()
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("O elegí uno de la biblioteca", style = MaterialTheme.typography.titleSmall)
                            TextButton(
                                onClick = vm::loadChangeLibrary,
                                enabled = vm.changeLibrary !is LibraryState.Loading,
                            ) { Text("Actualizar") }
                        }
                    }
                }
                when (val library = vm.changeLibrary) {
                    LibraryState.Loading -> item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is LibraryState.Error -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ErrorText(library.message)
                            OutlinedButton(onClick = vm::loadChangeLibrary) { Text("Reintentar") }
                        }
                    }
                    is LibraryState.Loaded -> if (library.items.isEmpty()) {
                        item {
                            Text(
                                "La biblioteca está vacía.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(library.items, key = { it.filename }) { item ->
                            LibraryRow(item, enabled = !busy) { confirming = item }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = vm::hideChangeVideo) { Text(if (upload.isBusy()) "Ocultar" else "Cerrar") }
        },
    )

    confirming?.let { item ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("¿Cambiar el video?") },
            text = {
                Text(
                    "La sala pasa a «${item.displayName}». Todos lo ven cambiar y arranca desde el principio, en pausa.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirming = null
                    vm.changeVideoTo(item)
                }) { Text("Cambiar") }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun LibraryRow(item: LibraryItem, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            item.displayName,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            formatFileSize(item.sizeBytes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Franja de arriba de la sala con el avance de una subida (o su fallo) mientras el cuadro "Cambiar video" está
 * oculto. No muestra nada si no hay subida, ni cuando terminó bien (eso ya lo dice el chat de la sala).
 */
@Composable
fun RoomUploadBanner(vm: RoomViewModel) {
    if (vm.showChangeVideo) return
    val state = vm.upload
    val text = when (state) {
        is UploadState.Preparing -> "Preparando la subida…"
        is UploadState.Uploading -> "Subiendo ${state.fileName} · " + progressLabel(state.sentBytes, state.totalBytes)
        is UploadState.Finishing -> finishingLabel(state.goal)
        is UploadState.Failed -> "No se pudo cambiar el video: ${state.message}"
        else -> return
    }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 3,
        )
        when {
            // Quien ya no es host no puede abrir el cuadro: solo cortar o cerrar el aviso.
            vm.isHost -> TextButton(onClick = vm::openChangeVideo) { Text("Ver") }
            state.canCancel() -> TextButton(onClick = vm::cancelUpload) { Text("Cancelar") }
            !state.isBusy() -> TextButton(onClick = vm::dismissUpload) { Text("Cerrar") }
        }
    }
}
