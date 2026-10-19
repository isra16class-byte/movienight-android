package com.isra16.movienight.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.isra16.movienight.MovieNightApp
import com.isra16.movienight.net.Avatar
import com.isra16.movienight.net.AvatarListStatus
import com.isra16.movienight.ui.auth.ErrorText
import com.isra16.movienight.ui.auth.GlassButton
import com.isra16.movienight.ui.auth.GlassDialog
import com.isra16.movienight.ui.auth.GlassLinkButton
import com.isra16.movienight.ui.common.AvatarImage

/** Columnas de la cuadrícula del selector. */
private const val AVATAR_GRID_COLUMNS = 3

/** Tamaño de cada avatar del selector; el círculo tocable mide 80 dp con el borde del elegido (más que los 48 dp mínimos). */
private val AVATAR_PICKER_SIZE = 72.dp
private val AVATAR_PICKER_CELL = 80.dp

/**
 * Ventana «Elige tu avatar» (Fase 8, «Avatares de cuenta»): cuadrícula de 3 columnas con los avatares del servidor (`container.avatars`).
 * Al tocar uno se guarda al instante ([onChoose]) y la pantalla que la abre la cierra cuando sale bien; mientras se guarda ([busy]) se ve
 * un indicador y no se aceptan más toques. [currentId] queda marcado con un borde; «Quitar avatar» solo sale si hay uno elegido. Los errores
 * de la lista (con «Reintentar») y del cambio ([error]) se ven aquí dentro.
 */
@Composable
internal fun AvatarPickerDialog(
    currentId: String?,
    busy: Boolean,
    error: String?,
    onChoose: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val container = (LocalContext.current.applicationContext as MovieNightApp).container
    val directory by container.avatars.state.collectAsState()
    LaunchedEffect(Unit) { container.avatars.ensureLoaded() }
    // «Quitar avatar» solo si hay uno elegido.
    val removeButton: (@Composable () -> Unit)? =
        if (currentId != null) ({ GlassLinkButton("Quitar avatar", enabled = !busy, onClick = onRemove) }) else null

    GlassDialog(
        onDismissRequest = onDismiss,
        title = "Elige tu avatar",
        confirmButton = { GlassLinkButton("Cerrar", enabled = true, onClick = onDismiss) },
        dismissButton = removeButton,
    ) {
        when {
            directory.avatars.isNotEmpty() -> AvatarGrid(directory.avatars, currentId, enabled = !busy, onChoose = onChoose)
            directory.status == AvatarListStatus.READY -> Text("No hay avatares disponibles.", style = MaterialTheme.typography.bodyMedium)
            directory.status == AvatarListStatus.ERROR -> {
                ErrorText(directory.errorMessage ?: "No se pudieron cargar los avatares. Inténtalo de nuevo.")
                GlassButton("Reintentar", busy = false, onClick = container.avatars::reload, modifier = Modifier.width(150.dp))
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Text("Cargando avatares…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Guardando…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (error != null) ErrorText(error)
        Text("Los demás lo verán la próxima vez que entres a una sala.", style = MaterialTheme.typography.bodyMedium)
    }
}

/** Los avatares en filas de [AVATAR_GRID_COLUMNS]; con muchos, la cuadrícula se desplaza dentro de la ventana. */
@Composable
private fun AvatarGrid(avatars: List<Avatar>, currentId: String?, enabled: Boolean, onChoose: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (row in avatars.chunked(AVATAR_GRID_COLUMNS)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (avatar in row) AvatarCell(avatar, selected = avatar.id == currentId, enabled = enabled, onClick = { onChoose(avatar.id) })
                // Una última fila incompleta deja huecos del mismo tamaño para que las columnas queden alineadas.
                repeat(AVATAR_GRID_COLUMNS - row.size) { Box(Modifier.size(AVATAR_PICKER_CELL)) }
            }
        }
    }
}

@Composable
private fun AvatarCell(avatar: Avatar, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val marker = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(AVATAR_PICKER_CELL)
            .clip(CircleShape)
            .then(if (selected) Modifier.border(3.dp, marker, CircleShape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AvatarImage(avatar.id, avatar.name, AVATAR_PICKER_SIZE)
    }
}
