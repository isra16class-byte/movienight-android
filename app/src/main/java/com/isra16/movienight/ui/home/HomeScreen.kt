package com.isra16.movienight.ui.home

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.isra16.movienight.net.DEFAULT_HOME_TAB
import com.isra16.movienight.net.HomeTab
import com.isra16.movienight.net.LibraryItem
import com.isra16.movienight.net.MAX_SIMPLE_PUT_LABEL
import com.isra16.movienight.net.UPLOAD_VIDEO_EXTENSIONS
import com.isra16.movienight.net.UploadState
import com.isra16.movienight.net.formatFileSize
import com.isra16.movienight.net.homeTabAt
import com.isra16.movienight.net.isBusy
import com.isra16.movienight.net.shouldShowAdminAccess
import com.isra16.movienight.net.showHomeBottomBar
import com.isra16.movienight.ui.auth.ErrorText
import com.isra16.movienight.ui.auth.GLASS_CARD_TINT_ALPHA
import com.isra16.movienight.ui.auth.GlassButton
import com.isra16.movienight.ui.auth.GlassContent
import com.isra16.movienight.ui.auth.GlassDialog
import com.isra16.movienight.ui.auth.GlassFieldGroup
import com.isra16.movienight.ui.auth.GlassLinkButton
import com.isra16.movienight.ui.auth.GlassPasswordField
import com.isra16.movienight.ui.auth.GlassPanel
import com.isra16.movienight.ui.auth.GlassTextField
import com.isra16.movienight.ui.theme.AuthTitleStyle
import com.isra16.movienight.ui.theme.LocalGlassColors
import com.isra16.movienight.ui.upload.KeepScreenOn
import com.isra16.movienight.ui.upload.rememberNotificationGate
import com.isra16.movienight.ui.upload.UploadStatusBody
import java.text.DateFormat
import java.util.Date

/**
 * Pantalla principal, con una barra inferior de cuatro pestañas (Fase 8, paso 4): Salas (unirse por código o link),
 * Biblioteca (subir un video del teléfono y crear una sala con uno de la biblioteca), Subidas (estado de la subida en
 * curso) y Perfil (cuenta, modo oscuro, administración y cerrar sesión). Arriba de cada pestaña va la tarjeta de
 * encabezado. El estado (subida, biblioteca, ventanas) vive aquí y no cambia con la pestaña.
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

    // Pestaña elegida: se guarda como número para sobrevivir al giro (ver `homeTabAt`).
    var tabIndex by rememberSaveable { mutableStateOf(DEFAULT_HOME_TAB.ordinal) }
    val tab = homeTabAt(tabIndex)
    val headerImage = rememberHeaderImage()

    // Todo lo de Home va dentro de GlassContent: fija los colores del modo y, dentro de cada panel, GlassBarContent los cambia.
    GlassContent {
        // Arriba y a los lados lo respeta esta columna; abajo, la barra de pestañas se extiende bajo la barra del sistema
        // (sus propios insets) y, con el teclado abierto, el contenido sube con `imePadding` y la barra se esconde.
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            ),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth().imePadding(), contentAlignment = Alignment.TopCenter) {
                // `key(tab)`: cada pestaña tiene su propio desplazamiento, que empieza arriba.
                key(tab) {
                    LazyColumn(
                        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { HeaderCard(image = headerImage, onOpenProfile = { tabIndex = HomeTab.PROFILE.ordinal }) }
                        when (tab) {
                            HomeTab.ROOMS -> item { JoinCard(vm, onOpenRoom) }
                            HomeTab.LIBRARY -> item {
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
                            HomeTab.UPLOADS -> item {
                                UploadsCard(
                                    upload = uploadState,
                                    onPick = { askNotifications { pickVideo.launch(arrayOf("video/*")) } },
                                    onCancel = vm::cancelUpload,
                                    onRetry = vm::retryUpload,
                                    onDismiss = vm::dismissUpload,
                                    onOpenRoom = onOpenRoom,
                                )
                            }
                            HomeTab.PROFILE -> {
                                if (adminError != null) item { ErrorText(adminError) }
                                item {
                                    ProfileCard(
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
                            }
                        }
                    }
                }
            }
            HomeBottomBar(selected = tab, onSelect = { tabIndex = it.ordinal })
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

/** Nombre (sin extensión) del recurso `res/drawable-nodpi/home_header.*` con la imagen del encabezado. Si no existe, el encabezado usa un degradado. */
private const val HOME_HEADER_IMAGE_NAME = "home_header"

private const val HOME_TAGLINE = "Una película, buena compañía."
private val HOME_HEADER_MIN_HEIGHT = 168.dp

/** Fondo del encabezado mientras no hay imagen (provisional): verdes y azules oscuros, para que el texto blanco se lea. */
private val HomeHeaderFallback = Brush.linearGradient(listOf(Color(0xFF1E5B52), Color(0xFF0B2A3A)))

/** Oscurece la parte de abajo del encabezado (con o sin imagen) para que el texto blanco se lea sobre cualquier foto. */
private val HomeHeaderScrim = Brush.verticalGradient(listOf(Color.Transparent, Color(0xA6000000)))

/**
 * Imagen del encabezado, o `null` si el archivo `home_header` no está en `res/drawable-nodpi`. Se busca por nombre a propósito:
 * así basta con copiar el archivo (por ejemplo `home_header.webp`) para que aparezca, sin tocar el código. Con R8 y
 * `shrinkResources` activados (hoy no lo están) habría que protegerlo con un `res/raw/keep.xml`.
 */
@SuppressLint("DiscouragedApi")
@Composable
private fun rememberHeaderImage(): Painter? {
    val context = LocalContext.current
    val id = remember(context) { context.resources.getIdentifier(HOME_HEADER_IMAGE_NAME, "drawable", context.packageName) }
    return if (id != 0) painterResource(id) else null
}

/**
 * Tarjeta de encabezado: imagen (o degradado) con el nombre y el lema abajo a la izquierda, y el avatar arriba a la
 * derecha, que lleva a la pestaña Perfil. El texto es siempre blanco: va sobre la imagen oscurecida, no sobre el vidrio.
 */
@Composable
private fun HeaderCard(image: Painter?, onOpenProfile: () -> Unit) {
    val shape = RoundedCornerShape(HOME_CARD_CORNER)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = HOME_HEADER_MIN_HEIGHT)
            .clip(shape)
            .background(HomeHeaderFallback)
            .border(1.dp, LocalGlassColors.current.panelBorder, shape),
    ) {
        if (image != null) {
            Image(image, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.matchParentSize().background(HomeHeaderScrim))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            // El título de las pantallas de entrada (Playfair Display, provisional hasta el paso 6).
            Text("MovieNight", style = AuthTitleStyle.copy(fontSize = 30.sp, lineHeight = 36.sp), color = Color.White)
            Text(HOME_TAGLINE, style = MaterialTheme.typography.bodyMedium, color = Color.White)
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0x66FFFFFF))
                .border(2.dp, Color.White, CircleShape)
                .clickable(role = Role.Button, onClick = onOpenProfile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_person), contentDescription = "Perfil", tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}

private fun HomeTab.iconRes(): Int = when (this) {
    HomeTab.ROOMS -> R.drawable.ic_nav_home
    HomeTab.LIBRARY -> R.drawable.ic_nav_library
    HomeTab.UPLOADS -> R.drawable.ic_upload
    HomeTab.PROFILE -> R.drawable.ic_person
}

/**
 * Barra de pestañas de vidrio: un [GlassPanel] sin esquinas ni márgenes, del ancho de la ventana, con el mismo tinte que las
 * tarjetas de Home (`HOME_TINT_ALPHA`) y la copia borrosa del fondo por dentro (Fase 8, paso 4j; antes era un tinte liso).
 * El panel incluye la franja bajo la barra del sistema, porque `NavigationBar` pone ahí su propio relleno. Se esconde con
 * el teclado abierto (`showHomeBottomBar`); el alto del teclado se lee solo aquí, para que al animarse no se recomponga
 * toda la pantalla. El texto va con los colores de dentro del panel (oscuros en el modo claro, blancos en el oscuro).
 */
@Composable
private fun HomeBottomBar(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    if (!showHomeBottomBar(WindowInsets.ime.getBottom(LocalDensity.current))) return
    val glass = LocalGlassColors.current
    GlassPanel(
        cornerRadius = 0.dp,
        contentPadding = 0.dp,
        spacing = 0.dp,
        tint = glass.panelTint.copy(alpha = HOME_TINT_ALPHA),
    ) {
        val ink = MaterialTheme.colorScheme.onSurface
        // Transparente: el vidrio lo dibuja el panel de afuera.
        NavigationBar(containerColor = Color.Transparent, contentColor = ink) {
            HomeTab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    icon = { Icon(painterResource(tab.iconRes()), contentDescription = null) },
                    label = { Text(tab.label, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ink,
                        selectedTextColor = ink,
                        indicatorColor = ink.copy(alpha = 0.14f),
                        unselectedIconColor = ink.copy(alpha = 0.7f),
                        unselectedTextColor = ink.copy(alpha = 0.7f),
                    ),
                )
            }
        }
    }
}

/** Fila de la pestaña Perfil: texto a la izquierda y, si hace falta, algo a la derecha; toda la fila es el botón (48 dp de alto como mínimo). */
@Composable
private fun ProfileRow(label: String, enabled: Boolean = true, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        trailing()
    }
}

/** Pestaña Perfil: lo que antes era el menú "Cuenta" (modo oscuro, administración si corresponde y cerrar sesión), ahora a la vista. */
@Composable
private fun ProfileCard(
    email: String,
    isLoggingOut: Boolean,
    onLogout: () -> Unit,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    showAdmin: Boolean,
    onOpenAdmin: () -> Unit,
) {
    HomeGlassCard {
        Text("Perfil", style = MaterialTheme.typography.titleLarge)
        if (email.isNotBlank()) Text(email, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ProfileRow("Modo oscuro", onClick = { onDarkThemeChange(!darkTheme) }) {
            Switch(checked = darkTheme, onCheckedChange = null)
        }
        if (showAdmin) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ProfileRow("Administración (abre el navegador)", onClick = onOpenAdmin)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ProfileRow(if (isLoggingOut) "Saliendo…" else "Cerrar sesión", enabled = !isLoggingOut, onClick = onLogout)
    }
}

/** Pestaña Subidas (provisional): el estado de la subida en curso; sin ninguna, un aviso. Las subidas se inician desde Biblioteca. */
@Composable
private fun UploadsCard(
    upload: UploadState,
    onPick: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onOpenRoom: (roomId: String) -> Unit,
) {
    HomeGlassCard {
        Text("Subidas", style = MaterialTheme.typography.titleLarge)
        UploadStatusBody(
            state = upload,
            idle = {
                Text(
                    "No hay subidas en curso. Para subir un video, andá a Biblioteca.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onPick = onPick,
            onCancel = onCancel,
            onRetry = onRetry,
            onDismiss = onDismiss,
            onOpenRoom = onOpenRoom,
        )
    }
}

/**
 * Tarjeta de vidrio de Home: el [GlassPanel] de las pantallas de entrada, con el contenido alineado a la izquierda y más
 * compacto. [blurredCopy] en false deja solo tinte y borde (sin la copia borrosa trasladada); hoy ninguna tarjeta de Home lo
 * usa: todas llevan el vidrio completo (Fase 8, paso 4i). La barra de pestañas también lleva vidrio (paso 4j), pero con su propio panel.
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
        // Mismo color que el de la paleta (blanco en claro, oscuro en oscuro) pero más asentado: se ve menos de la foto detrás del texto.
        tint = LocalGlassColors.current.panelTint.copy(alpha = HOME_TINT_ALPHA),
        content = content,
    )
}

private val HOME_CARD_CORNER = 24.dp

/** Opacidad del tinte de las tarjetas de Home (antes 0,45 en claro y 0,55 en oscuro, los de la paleta). Provisional: se ajusta en el teléfono. */
private const val HOME_TINT_ALPHA = GLASS_CARD_TINT_ALPHA

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
    HomeGlassCard {
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
                    GlassLinkButton("Solo subir (sin crear sala)", enabled = true, onClick = onPick, icon = painterResource(R.drawable.ic_cloud_upload))
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
            painterResource(R.drawable.ic_video_clip),
            contentDescription = "Crear sala con este video",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(32.dp),
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

    GlassDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmButton = {
            // Mismo botón de cristal de la pantalla; mientras trabaja muestra el progreso y no se puede tocar.
            GlassButton(confirmLabel, busy = busy, onClick = { onCreate(password) }, modifier = Modifier.width(150.dp))
        },
        dismissButton = { GlassLinkButton("Cancelar", enabled = !busy, onClick = onDismiss) },
    ) {
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, maxLines = 4)
        GlassFieldGroup {
            GlassPasswordField(
                label = "Contraseña (opcional)",
                value = password,
                onValueChange = { password = it },
                error = null,
                enabled = !busy,
                showPassword = showPassword,
                onToggleShow = { showPassword = !showPassword },
                imeAction = ImeAction.Done,
                onDone = { onCreate(password) },
            )
        }
        ErrorText(error)
    }
}
