package com.isra16.movienight.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.isra16.movienight.ui.auth.GLASS_CARD_TINT_ALPHA
import com.isra16.movienight.ui.auth.GlassContent
import com.isra16.movienight.ui.auth.GlassPanel
import com.isra16.movienight.ui.theme.LocalAppDarkTheme
import com.isra16.movienight.ui.theme.LocalGlassColors
import com.isra16.movienight.ui.theme.MovieNightDarkChat
import com.isra16.movienight.ui.theme.MovieNightLightChat
import com.isra16.movienight.ui.theme.RoomChatColors

/** Radio de las esquinas de una barra de vidrio suelta (los estados que no son la sala). */
internal val ROOM_GLASS_CORNER = 20.dp

/**
 * Piezas del bloque de vidrio de la sala en vertical: van de borde a borde de la pantalla, sin margen ni esquinas, y la de arriba
 * y la de abajo se estiran bajo la barra de estado y la de navegación (ver `RoomHeader` y `RoomContentBody`). Las tres son rectas.
 */
internal val RoomGlassTopShape: Shape = RectangleShape
internal val RoomGlassMiddleShape: Shape = RectangleShape
internal val RoomGlassBottomShape: Shape = RectangleShape

/**
 * Barra de vidrio de la sala (Fase 8, paso 5a): cabecera, fila de la cinta y avisos. Es el `GlassPanel` de Home con el mismo
 * tinte de las tarjetas (`GLASS_CARD_TINT_ALPHA`) y la copia borrosa del fondo por dentro. Solo va FUERA del video: la caja del
 * video y los controles encima de ella no llevan vidrio. Debe ir dentro de `HomeBackdrop` (lo pone `AppRoot`); `GlassContent`
 * se pone aquí, y no alrededor de toda la sala, para que no cambie los colores de los controles del video.
 */
@Composable
internal fun RoomGlassBar(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 8.dp,
    shape: Shape = RoundedCornerShape(ROOM_GLASS_CORNER),
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassContent {
        GlassPanel(
            modifier = modifier,
            horizontalAlignment = Alignment.Start,
            contentPadding = contentPadding,
            panelShape = shape,
            spacing = 0.dp,
            tint = LocalGlassColors.current.panelTint.copy(alpha = GLASS_CARD_TINT_ALPHA),
            content = content,
        )
    }
}

/**
 * Fondo liso de lo que todavía no lleva vidrio (el chat y los estados de la sala). INTERINO de 5a: 5b (chat) y 5c (estados)
 * lo reemplazan; se pone para que ese texto no quede suelto sobre la imagen sin contraste medido.
 */
@Composable
internal fun RoomSolidZone(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.background(MaterialTheme.colorScheme.background)) { content() }
}

/** Colores de las burbujas y avisos del chat del modo actual (provisionales; ver `RoomChatColors`). */
@Composable
internal fun roomChatColors(): RoomChatColors = if (LocalAppDarkTheme.current) MovieNightDarkChat else MovieNightLightChat
