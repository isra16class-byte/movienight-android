package com.isra16.movienight.ui.upload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.isra16.movienight.net.ALREADY_UPLOADED_NOTE
import com.isra16.movienight.net.UploadGoal
import com.isra16.movienight.net.UploadState
import com.isra16.movienight.net.doneMessage
import com.isra16.movienight.net.finishingLabel
import com.isra16.movienight.net.progressLabel
import com.isra16.movienight.ui.auth.ErrorText

/**
 * Lo que se ve de una subida de video según su [UploadState]: lo comparten la tarjeta de la pantalla
 * principal y el cuadro "Cambiar video" de la sala. Qué se muestra en reposo ([UploadState.Idle]) lo pone
 * cada pantalla en [idle].
 *
 * Los botones "Subir otro" / "Elegir otro video" solo aparecen si la subida era a la biblioteca: si era para
 * crear una sala o cambiar el video de una, volver a elegir sin pasar por el botón de esa acción haría una
 * subida distinta de la que la persona pidió.
 */
@Composable
fun UploadStatusBody(
    state: UploadState,
    idle: @Composable () -> Unit,
    onPick: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Si no es `null`, un "listo" de crear sala ofrece este botón (por si la pantalla no navegó sola). */
    onOpenRoom: ((roomId: String) -> Unit)? = null,
    uploadingHint: String = "Dejá la app abierta mientras sube.",
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state) {
            UploadState.Idle -> idle()
            is UploadState.Preparing -> {
                FileNameLine(state.fileName)
                BusyRow("Preparando la subida…")
                OutlinedButton(onClick = onCancel) { Text("Cancelar") }
            }
            is UploadState.Uploading -> {
                FileNameLine(state.fileName)
                LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
                Text(
                    if (state.sentBytes >= state.totalBytes) {
                        "Terminando… esperando que la nube confirme."
                    } else {
                        progressLabel(state.sentBytes, state.totalBytes)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    uploadingHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onCancel) { Text("Cancelar") }
            }
            // Sin "Cancelar": es un POST de un instante y cortarlo dejaría en duda si la sala se creó.
            is UploadState.Finishing -> {
                FileNameLine(state.fileName)
                BusyRow(finishingLabel(state.goal))
            }
            is UploadState.Done -> {
                Text(doneMessage(state), style = MaterialTheme.typography.bodyMedium)
                val roomId = state.roomId
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (roomId != null && onOpenRoom != null) {
                        Button(onClick = { onOpenRoom(roomId) }) { Text("Entrar a la sala") }
                    } else if (state.goal == UploadGoal.LIBRARY) {
                        Button(onClick = onPick) { Text("Subir otro") }
                    }
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
                }
            }
            is UploadState.Failed -> {
                FileNameLine(state.fileName)
                ErrorText(state.message)
                if (state.alreadyUploaded) {
                    Text(
                        ALREADY_UPLOADED_NOTE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.canRetry) {
                        Button(onClick = onRetry) { Text("Reintentar") }
                        OutlinedButton(onClick = onDismiss) { Text("Descartar") }
                    } else {
                        if (!state.alreadyUploaded && state.goal == UploadGoal.LIBRARY) {
                            Button(onClick = onPick) { Text("Elegir otro video") }
                        }
                        TextButton(onClick = onDismiss) { Text("Cerrar") }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileNameLine(fileName: String) {
    if (fileName.isNotBlank()) Text(fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
}

@Composable
private fun BusyRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Mantiene la pantalla encendida mientras [enabled]. Mientras sube un video la pantalla no se apaga: apagada,
 * Android puede congelar la app y cortar la subida.
 */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
