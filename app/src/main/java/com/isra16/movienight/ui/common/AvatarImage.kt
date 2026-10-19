package com.isra16.movienight.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.isra16.movienight.MovieNightApp
import com.isra16.movienight.net.avatarById
import com.isra16.movienight.net.avatarInitial
import com.isra16.movienight.net.chatNameColorIndex
import com.isra16.movienight.ui.room.roomChatColors
import com.isra16.movienight.ui.theme.LocalAppDarkTheme

/** Tinta de la inicial sobre el color del nombre en modo oscuro (los colores de nombre del modo oscuro son claros). Igual a `DarkBackground`. */
private val InitialInkDark = Color(0xFF0E1511)

/** Tinta de la inicial en modo claro (los colores de nombre del modo claro son oscuros). */
private val InitialInkLight = Color.White

/**
 * El avatar de una persona, en círculo (Fase 8, «Avatares de cuenta»). Si [avatarId] está en la lista del servidor (`container.avatars`) y su
 * icono se pudo cargar (`container.avatarImages`), lo dibuja recortado en círculo; si no (sin avatar, id desconocido, lista o imagen
 * cargando, error), dibuja un círculo con la inicial de [name] (`avatarInitial`) sobre el color de su nombre (`chatNameColorIndex` y la
 * paleta del chat: el mismo color que el nombre en la sala). El tamaño es fijo desde el primer cuadro: no hay saltos al cargar. [name]
 * es también la descripción para accesibilidad.
 */
@Composable
fun AvatarImage(avatarId: String?, name: String, size: Dp, modifier: Modifier = Modifier) {
    val container = (LocalContext.current.applicationContext as MovieNightApp).container
    val directory by container.avatars.state.collectAsState()
    val avatarUrl = avatarById(directory.avatars, avatarId)?.url

    // Lo que ya está en memoria se dibuja en el primer cuadro; lo demás se pide una vez por URL (un `?v=` nuevo es otra URL).
    var bitmap by remember(avatarUrl) { mutableStateOf(avatarUrl?.let { container.avatarImages.peek(it) }) }
    LaunchedEffect(avatarUrl) {
        if (avatarUrl != null && bitmap == null) bitmap = container.avatarImages.load(avatarUrl)
    }

    val chat = roomChatColors()
    val background = chat.names[chatNameColorIndex(name, chat.names.size)]
    val ink = if (LocalAppDarkTheme.current) InitialInkDark else InitialInkLight
    val initial = remember(name) { avatarInitial(name) }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    val fontSize = with(LocalDensity.current) { (size * 0.45f).toSp() }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(bitmap = image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize().background(background), contentAlignment = Alignment.Center) {
                Text(initial, color = ink, fontSize = fontSize, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
            }
        }
    }
}
