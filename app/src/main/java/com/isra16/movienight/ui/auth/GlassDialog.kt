package com.isra16.movienight.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.isra16.movienight.ui.theme.AuthTitleStyle
import com.isra16.movienight.ui.theme.LocalGlassColors

/**
 * Ventana flotante de vidrio (Fase 8, paso 4e): el mismo panel de las tarjetas de Home (esquinas de 28 dp, borde fino,
 * texto oscuro en el modo claro y blanco en el oscuro, sin sombra), con el título en la tipografía de la app y los
 * botones de cristal. Sustituye a `AlertDialog`: [confirmButton] y [dismissButton] se pasan ya armados (normalmente un
 * `GlassButton` y un `GlassLinkButton`) y van en una fila abajo, el de cancelar a la izquierda.
 *
 * Sin la copia borrosa del fondo: una ventana es otra superficie y la posición de la copia se calcula respecto a la
 * ventana de la app, no a la del diálogo. Detrás se ve la propia pantalla, oscurecida por el sistema, y el tinte es más
 * asentado que el de las tarjetas porque lo que hay detrás puede ser texto de la lista.
 */
@Composable
internal fun GlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        GlassPanel(
            horizontalAlignment = Alignment.Start,
            contentPadding = 24.dp,
            cornerRadius = 28.dp,
            spacing = 12.dp,
            blurredCopy = false,
            tint = LocalGlassColors.current.panelTint.copy(alpha = GLASS_DIALOG_TINT_ALPHA),
        ) {
            Text(title, style = AuthTitleStyle.copy(fontSize = 24.sp, lineHeight = 30.sp))
            content()
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}

/** Opacidad del tinte de las ventanas flotantes. Provisional: se ajusta en el teléfono. */
private const val GLASS_DIALOG_TINT_ALPHA = 0.88f
