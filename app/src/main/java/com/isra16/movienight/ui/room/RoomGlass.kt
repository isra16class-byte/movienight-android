package com.isra16.movienight.ui.room

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.isra16.movienight.ui.auth.GLASS_CARD_TINT_ALPHA
import com.isra16.movienight.ui.auth.GlassBarContent
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
 * Hoja de vidrio de la sala (Fase 8, paso 5e): UN solo `GlassPanel` a pantalla completa, sin forma ni borde ni margen, que se dibuja
 * detrás de toda la columna de la sala (ver `RoomScreen`). Las piezas de arriba (cabecera, fila de la cinta, avisos y chat) son
 * [RoomGlassBar] con `flat = true`: contenido transparente encima de esta hoja, así la copia borrosa y el tinte se dibujan una vez, sin
 * costuras entre piezas. La caja del video es opaca y tapa la hoja por el medio. Debe ir dentro de `HomeBackdrop`.
 */
@Composable
internal fun RoomGlassSheet(modifier: Modifier = Modifier) {
    GlassPanel(
        modifier = modifier,
        contentPadding = 0.dp,
        spacing = 0.dp,
        panelShape = RectangleShape,
        showBorder = false,
        tint = LocalGlassColors.current.panelTint.copy(alpha = GLASS_CARD_TINT_ALPHA),
    ) {}
}

/**
 * Barra de la sala (Fase 8, paso 5a): cabecera, fila de la cinta y avisos. Con `flat = false` es un `GlassPanel` suelto (los estados
 * que no son la sala), con el tinte de las tarjetas (`GLASS_CARD_TINT_ALPHA`) y la copia borrosa del fondo por dentro. Con `flat = true`
 * (dentro de la sala) no dibuja fondo: solo conserva los colores y el estilo de texto de `GlassContent` y `GlassBarContent`, y el
 * vidrio lo pone [RoomGlassSheet] detrás. Solo va FUERA del video: la caja del video y sus controles no llevan vidrio.
 */
@Composable
internal fun RoomGlassBar(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 8.dp,
    flat: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassContent {
        if (flat) {
            Column(modifier.fillMaxWidth().padding(contentPadding), horizontalAlignment = Alignment.Start) {
                val scope = this
                GlassBarContent { scope.content() }
            }
        } else {
            GlassPanel(
                modifier = modifier,
                horizontalAlignment = Alignment.Start,
                contentPadding = contentPadding,
                panelShape = RoundedCornerShape(ROOM_GLASS_CORNER),
                spacing = 0.dp,
                tint = LocalGlassColors.current.panelTint.copy(alpha = GLASS_CARD_TINT_ALPHA),
                content = content,
            )
        }
    }
}

/**
 * Tarjeta de vidrio centrada de los estados de la sala que no son el chat (comprobando, entrando, contraseña, expulsado y fallo;
 * Fase 8, paso 5h): un `GlassPanel` suelto sobre el fondo de Home, con el tinte de las tarjetas ([GLASS_CARD_TINT_ALPHA]) y la copia
 * borrosa por dentro, y los textos y botones con los colores del vidrio. Reemplaza al fondo liso interino (`RoomSolidZone`).
 */
@Composable
internal fun RoomStateCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    GlassContent {
        Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            GlassPanel(
                modifier = Modifier.widthIn(max = 480.dp),
                contentPadding = 24.dp,
                cornerRadius = 28.dp,
                spacing = 12.dp,
                tint = LocalGlassColors.current.panelTint.copy(alpha = GLASS_CARD_TINT_ALPHA),
                content = content,
            )
        }
    }
}

/** Colores de las burbujas y avisos del chat del modo actual (provisionales; ver `RoomChatColors`). */
@Composable
internal fun roomChatColors(): RoomChatColors = if (LocalAppDarkTheme.current) MovieNightDarkChat else MovieNightLightChat
