package com.isra16.movienight.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.isra16.movienight.R
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
import com.isra16.movienight.ui.auth.GlassBarContent
import com.isra16.movienight.ui.auth.GlassButton
import com.isra16.movienight.ui.auth.GlassContent
import com.isra16.movienight.ui.auth.GlassFieldGroup
import com.isra16.movienight.ui.auth.GlassLinkButton
import com.isra16.movienight.ui.auth.GlassPanel
import com.isra16.movienight.ui.auth.GlassTextField
import com.isra16.movienight.ui.auth.PasswordField
import com.isra16.movienight.ui.theme.AuthTitleStyle
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
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
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

    // Todo lo de Home va dentro de GlassContent: fija los colores del modo y, dentro de cada panel, GlassBarContent los cambia.
    GlassContent {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Header(
                        email = email,
                        isLoggingOut = isLoggingOut,
                        onLogout = onLogout,
                        darkTheme = darkTheme,
                        onDarkThemeChange = onDarkThemeChange,
                        showAdmin = shouldShowAdminAccess(vm.adminAccess),
                        onOpenAdmin = {
                            adminError = if (openInBrowser(context, vm.adminPanelLink)) null else ADMIN_NO_BROWSER_MESSAGE
                        },
                    )
                }
                if (adminError != null) item { ErrorText(adminError) }
                item { JoinCard(vm, onOpenRoom) }
                item {
                    LibraryCard(
                        library = vm.library,
                        upload = uploadState,
                        onRefresh = vm::refresh,
                        onPick = { askNotifications { pickVideo.launch(arrayOf("video/*")) } },
                        onPickAndCreateRoom = { showUploadAndCreate = true },
                        onCancel = vm::cancelUpload,
                        onRetry = vm::retryUpload,
                        onDismiss = vm::dismissUpload,
                        onOpenRoom = onOpenRoom,
                        onSelect = { item ->
                            vm.clearCreateRoomError()
                            selected = item
                        },
                    )
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
}

/**
 * Encabezado sin panel: el nombre y el email sobre el fondo, y un botón "Cuenta" con un menú (modo oscuro, administración
 * si corresponde y cerrar sesión). Antes eran dos tarjetas (encabezado y administración) arriba de todo.
 */
@Composable
private fun Header(
    email: String,
    isLoggingOut: Boolean,
    onLogout: () -> Unit,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    showAdmin: Boolean,
    onOpenAdmin: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            // El título de las pantallas de entrada (Playfair Display, provisional hasta el paso 6) en un tamaño menor.
            Text("MovieNight", style = AuthTitleStyle.copy(fontSize = 26.sp, lineHeight = 32.sp))
            if (email.isNotBlank()) {
                Text(
                    email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Box {
            GlassButton("Cuenta", busy = false, onClick = { menuOpen = true }, modifier = Modifier.width(112.dp))
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                // El menú es una ventana aparte, con el fondo claro u oscuro normal del tema: el texto va con los colores de
                // dentro del panel (oscuros en el modo claro, blancos en el oscuro), no con los de sobre el fondo.
                GlassBarContent {
                    // El interruptor no cierra el menú: así se ve cómo cambia.
                    DropdownMenuItem(
                        text = {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Modo oscuro")
                                Switch(checked = darkTheme, onCheckedChange = null)
                            }
                        },
                        onClick = { onDarkThemeChange(!darkTheme) },
                    )
                    if (showAdmin) {
                        DropdownMenuItem(
                            text = { Text("Administración (abre el navegador)") },
                            onClick = {
                                menuOpen = false
                                onOpenAdmin()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (isLoggingOut) "Saliendo…" else "Cerrar sesión") },
                        enabled = !isLoggingOut,
                        onClick = {
                            menuOpen = false
                            onLogout()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Tarjeta de vidrio de Home: el [GlassPanel] de las pantallas de entrada, con el contenido alineado a la izquierda y más
 * compacto. [blurredCopy] en false deja solo tinte y borde (sin la copia borrosa trasladada): se usa en las tarjetas de la
 * biblioteca, que pueden ser muchas en una lista.
 */
@Composable
private fun HomeGlassCard(
    modifier: Modifier = Modifier,
    blurredCopy: Boolean = true,
    padding: Dp = 16.dp,
    spacing: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassPanel(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        contentPadding = padding,
        cornerRadius = HOME_CARD_CORNER,
        spacing = spacing,
        blurredCopy = blurredCopy,
        content = content,
    )
}

private val HOME_CARD_CORNER = 24.dp

@Composable
private fun JoinCard(vm: HomeViewModel, onOpenRoom: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    val joinError = vm.joinError
    fun join() = vm.joinByCode(code, onOpenRoom)

    // La acción principal de la pantalla: el campo y el botón en una sola fila.
    HomeGlassCard {
        Text("Unirse a una sala", style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.weight(1f)) {
                GlassFieldGroup {
                    GlassTextField(
                        value = code,
                        onValueChange = {
                            code = it
                            vm.clearJoinError()
                        },
                        label = "Código o link",
                        error = joinError,
                        imeAction = ImeAction.Go,
                        onAction = { join() },
                    )
                }
            }
            GlassButton("Unirme", busy = false, onClick = ::join, modifier = Modifier.width(104.dp), enabled = code.isNotBlank())
        }
    }
}

/**
 * Biblioteca y subida en un solo panel: título y "Actualizar", la subida (botones, o el avance si hay una en curso) y,
 * debajo, los videos como filas separadas por una línea fina (no una tarjeta por video). Tocar una fila abre "Crear sala".
 * Las filas no son perezosas: van todas dentro del panel (una biblioteca de decenas de videos no pesa; de cientos habría que revisarlo).
 */
@Composable
private fun LibraryCard(
    library: LibraryState,
    upload: UploadState,
    onRefresh: () -> Unit,
    onPick: () -> Unit,
    onPickAndCreateRoom: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onOpenRoom: (roomId: String) -> Unit,
    onSelect: (LibraryItem) -> Unit,
) {
    HomeGlassCard(blurredCopy = false) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Biblioteca", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onRefresh, enabled = library !is LibraryState.Loading) {
                Icon(painterResource(R.drawable.ic_refresh), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Actualizar")
            }
        }
        UploadStatusBody(
            state = upload,
            idle = {
                val formats = UPLOAD_VIDEO_EXTENSIONS.joinToString(", ") { it.removePrefix(".").uppercase() }
                Text(
                    "$formats · hasta $MAX_SIMPLE_PUT_LABEL",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Una sola acción principal; la otra, más discreta (en dos columnas "Subir y crear sala" no cabe en un teléfono angosto).
                GlassButton("Subir y crear sala", busy = false, onClick = onPickAndCreateRoom)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    GlassLinkButton("Solo subir (sin crear sala)", enabled = true, onClick = onPick, icon = painterResource(R.drawable.ic_upload))
                }
            },
            onPick = onPick,
            onCancel = onCancel,
            onRetry = onRetry,
            onDismiss = onDismiss,
            onOpenRoom = onOpenRoom,
        )
        when (library) {
            LibraryState.Loading -> Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is LibraryState.Error -> {
                ErrorText(library.message)
                GlassButton("Reintentar", busy = false, onClick = onRefresh)
            }
            is LibraryState.Loaded -> if (library.items.isEmpty()) {
                Text(
                    "La biblioteca está vacía. Subí un video desde acá o desde la web para poder crear una sala.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(Modifier.fillMaxWidth()) {
                    library.items.forEach { item ->
                        key(item.filename) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            LibraryRow(item) { onSelect(item) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(item: LibraryItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
        // La fila entera es el botón; el icono (con descripción para lectores de pantalla) deja todo el ancho al nombre.
        Icon(
            painterResource(R.drawable.ic_play),
            contentDescription = "Crear sala con este video",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
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

    // GlassBarContent: texto oscuro en el modo claro y blanco en el oscuro, sin el verde de antes; el fondo, algo translúcido.
    GlassBarContent {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
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
}
