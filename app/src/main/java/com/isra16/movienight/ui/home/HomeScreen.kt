package com.isra16.movienight.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.isra16.movienight.home.HomeViewModel
import com.isra16.movienight.home.LibraryState
import com.isra16.movienight.net.ADMIN_NO_BROWSER_MESSAGE
import com.isra16.movienight.net.LibraryItem
import com.isra16.movienight.net.MAX_SIMPLE_PUT_LABEL
import com.isra16.movienight.net.UPLOAD_VIDEO_EXTENSIONS
import com.isra16.movienight.net.UploadState
import com.isra16.movienight.net.formatFileSize
import com.isra16.movienight.net.isBusy
import com.isra16.movienight.net.shouldShowAdminAccess
import com.isra16.movienight.ui.auth.ErrorText
import com.isra16.movienight.ui.auth.PasswordField
import com.isra16.movienight.ui.upload.KeepScreenOn
import com.isra16.movienight.ui.upload.rememberNotificationGate
import com.isra16.movienight.ui.upload.UploadStatusBody
import java.text.DateFormat
import java.util.Date

/**
 * Pantalla principal: unirse a una sala por código o link, subir un video del teléfono a la biblioteca
 * y la biblioteca de videos para crear una sala nueva.
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
    var showUploadAndCreate by rememberSaveable { mutableStateOf(false) }

    // Selector de documentos del sistema (SAF): no pide permisos de almacenamiento, la persona le da
    // acceso a ESE archivo al elegirlo. "video/*" muestra solo videos; el tipo exacto se valida después.
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        vm.onVideoPicked(uri)
    }

    // Antes de abrir el selector, si hace falta, explica y pide el permiso de notificaciones (Android 13+, una sola vez).
    val askNotifications = rememberNotificationGate()

    // Acceso al panel de administración (Fase 6B): se pregunta al server al abrir la pantalla y cada vez
    // que se vuelve a ella (por ejemplo al salir de una sala), así un cambio de rol se nota.
    LaunchedEffect(Unit) { vm.refreshAdminAccess() }
    val context = LocalContext.current
    var adminError by remember { mutableStateOf<String?>(null) }

    // Mientras sube, la pantalla no se apaga: apagada, Android puede congelar la app y cortar la subida.
    val uploadState = vm.upload
    KeepScreenOn(uploadState.isBusy())

    // "Subir y crear sala": al terminar entra solo a la sala, pero únicamente si esta pantalla estaba a la
    // vista mientras se creaba. Si la persona se había ido a otra sala, no se la arrastra a esta: queda el
    // botón "Entrar a la sala" en la tarjeta.
    var previousUpload by remember { mutableStateOf(uploadState) }
    LaunchedEffect(uploadState) {
        val before = previousUpload
        previousUpload = uploadState
        val roomId = (uploadState as? UploadState.Done)?.roomId
        if (roomId != null && before is UploadState.Finishing) {
            vm.dismissUpload()
            onOpenRoom(roomId)
        }
    }

    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(email, isLoggingOut, onLogout) }
            if (shouldShowAdminAccess(vm.adminAccess)) {
                item {
                    AdminCard(
                        error = adminError,
                        onOpen = {
                            adminError = if (openInBrowser(context, vm.adminPanelLink)) null else ADMIN_NO_BROWSER_MESSAGE
                        },
                    )
                }
            }
            item { JoinCard(vm, onOpenRoom) }
            item {
                UploadCard(
                    state = uploadState,
                    onPick = { askNotifications { pickVideo.launch(arrayOf("video/*")) } },
                    onPickAndCreateRoom = { showUploadAndCreate = true },
                    onCancel = vm::cancelUpload,
                    onRetry = vm::retryUpload,
                    onDismiss = vm::dismissUpload,
                    onOpenRoom = onOpenRoom,
                )
            }
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
                            "La biblioteca está vacía. Subí un video desde acá o desde la web para poder crear una sala.",
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

    // "Subir y crear sala": pide la contraseña (opcional) y recién después abre el selector de videos.
    if (showUploadAndCreate) {
        CreateRoomDialog(
            title = "Subir y crear sala",
            subtitle = "Elegí un video del teléfono: se sube a la biblioteca y, al terminar, se crea la sala con él.",
            confirmLabel = "Elegir video",
            busy = false,
            error = null,
            onDismiss = { showUploadAndCreate = false },
            onCreate = { password ->
                showUploadAndCreate = false
                // La contraseña se guarda recién cuando se abre el selector: si el cuadro de permiso se pierde
                // (por ejemplo al girar el teléfono), no queda una contraseña pendiente para otra subida.
                askNotifications {
                    vm.prepareUploadAndCreateRoom(password)
                    pickVideo.launch(arrayOf("video/*"))
                }
            },
        )
    }

    selected?.let { item ->
        CreateRoomDialog(
            title = "Crear sala",
            subtitle = item.displayName,
            confirmLabel = "Crear sala",
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

/** Solo se muestra a una cuenta admin confirmada por el server (ver `shouldShowAdminAccess`). */
@Composable
private fun AdminCard(error: String?, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Administración", style = MaterialTheme.typography.titleMedium)
            Text(
                "El panel se abre en el navegador del teléfono. Ahí te va a pedir iniciar sesión " +
                    "con tu email y contraseña: es una sesión aparte de la de la app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ErrorText(error)
            OutlinedButton(onClick = onOpen) { Text("Abrir panel de administración") }
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
private fun UploadCard(
    state: UploadState,
    onPick: () -> Unit,
    onPickAndCreateRoom: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onOpenRoom: (roomId: String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Subir un video", style = MaterialTheme.typography.titleMedium)
            UploadStatusBody(
                state = state,
                idle = {
                    val formats = UPLOAD_VIDEO_EXTENSIONS.joinToString(", ") { it.removePrefix(".").uppercase() }
                    Text(
                        "Elegí un video del teléfono ($formats; hasta $MAX_SIMPLE_PUT_LABEL). " +
                            "Se sube directo a la nube y queda en la biblioteca; si querés, se crea la sala con él al terminar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onPickAndCreateRoom) { Text("Subir y crear sala") }
                        OutlinedButton(onClick = onPick) { Text("Solo subir") }
                    }
                },
                onPick = onPick,
                onCancel = onCancel,
                onRetry = onRetry,
                onDismiss = onDismiss,
                onOpenRoom = onOpenRoom,
            )
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

/**
 * Diálogo de la contraseña (opcional) de una sala nueva. Sirve para "Crear sala" con un video de la biblioteca
 * y para "Subir y crear sala" (donde el botón de confirmar abre el selector de videos).
 */
@Composable
private fun CreateRoomDialog(
    title: String,
    subtitle: String,
    confirmLabel: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onCreate: (password: String) -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, maxLines = 4)
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
                    Text(confirmLabel)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
}
