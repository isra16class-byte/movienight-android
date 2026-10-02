package com.isra16.movienight.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.isra16.movienight.home.HomeViewModel
import com.isra16.movienight.home.LibraryState
import com.isra16.movienight.net.LibraryItem
import com.isra16.movienight.net.formatFileSize
import com.isra16.movienight.ui.auth.ErrorText
import com.isra16.movienight.ui.auth.PasswordField
import java.text.DateFormat
import java.util.Date

/**
 * Pantalla principal: unirse a una sala por código o link, y la biblioteca de videos para crear una
 * sala nueva. Subir videos todavía no se puede desde la app (se hace desde la web).
 */
@Composable
fun HomeScreen(
    email: String,
    isLoggingOut: Boolean,
    onLogout: () -> Unit,
    onOpenRoom: (roomId: String) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    var selected by remember { mutableStateOf<LibraryItem?>(null) }

    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(email, isLoggingOut, onLogout) }
            item { JoinCard(vm, onOpenRoom) }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Biblioteca", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = vm::refresh, enabled = vm.library !is LibraryState.Loading) {
                        Text("Actualizar")
                    }
                }
            }
            when (val state = vm.library) {
                LibraryState.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is LibraryState.Error -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ErrorText(state.message)
                        OutlinedButton(onClick = vm::refresh) { Text("Reintentar") }
                    }
                }
                is LibraryState.Loaded -> if (state.items.isEmpty()) {
                    item {
                        Text(
                            "La biblioteca está vacía. Subí un video desde la web para poder crear una sala.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.items, key = { it.filename }) { item ->
                        VideoCard(item) {
                            vm.clearCreateRoomError()
                            selected = item
                        }
                    }
                }
            }
        }
    }

    selected?.let { item ->
        CreateRoomDialog(
            item = item,
            busy = vm.isCreatingRoom,
            error = vm.createRoomError,
            onDismiss = {
                if (!vm.isCreatingRoom) {
                    vm.clearCreateRoomError()
                    selected = null
                }
            },
            onCreate = { password ->
                vm.createRoom(item, password) { roomId ->
                    selected = null
                    onOpenRoom(roomId)
                }
            },
        )
    }
}

@Composable
private fun Header(email: String, isLoggingOut: Boolean, onLogout: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("MovieNight", style = MaterialTheme.typography.headlineMedium)
            if (email.isNotBlank()) {
                Text(
                    email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        OutlinedButton(onClick = onLogout, enabled = !isLoggingOut) {
            Text(if (isLoggingOut) "Saliendo…" else "Cerrar sesión")
        }
    }
}

@Composable
private fun JoinCard(vm: HomeViewModel, onOpenRoom: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    val joinError = vm.joinError
    fun join() = vm.joinByCode(code, onOpenRoom)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Unirse a una sala", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = code,
                onValueChange = {
                    code = it
                    vm.clearJoinError()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Código o link de la sala") },
                singleLine = true,
                isError = joinError != null,
                supportingText = if (joinError != null) {
                    { Text(joinError) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { join() }),
            )
            Button(onClick = ::join, enabled = code.isNotBlank()) { Text("Unirme") }
        }
    }
}

@Composable
private fun VideoCard(item: LibraryItem, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            val date = if (item.modifiedMillis > 0) {
                " · " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.modifiedMillis))
            } else {
                ""
            }
            Text(
                formatFileSize(item.sizeBytes) + date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreateRoomDialog(
    item: LibraryItem,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onCreate: (password: String) -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear sala") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(item.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 3)
                PasswordField(
                    label = "Contraseña de la sala (opcional)",
                    value = password,
                    onValueChange = { password = it },
                    error = null,
                    enabled = !busy,
                    showPassword = showPassword,
                    onToggleShow = { showPassword = !showPassword },
                    hint = "Dejala vacía para una sala abierta a quien tenga el link.",
                    imeAction = ImeAction.Done,
                    onDone = { onCreate(password) },
                )
                ErrorText(error)
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(password) }, enabled = !busy) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Crear sala")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
}
