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
 * Con la copia borrosa del fondo, como las tarjetas: la copia se dibuja opaca y tapa lo que hay detrás del panel (sin ella
 * se transparentaba el texto de la pantalla). Una ventana es otra superficie, así que la zona del fondo que se ve no es
 * exactamente la de la posición del diálogo (la copia es muy borrosa y casi no se nota). Fuera de [HomeBackdrop] (por
 * ejemplo en la sala) no hay copia: solo queda el tinte y por eso es más asentado.
 */
@Composable
internal fun GlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hasBackdrop = LocalGlassBackdrop.current != null
    Dialog(onDismissRequest = onDismissRequest) {
        GlassPanel(
            horizontalAlignment = Alignment.Start,
            contentPadding = 24.dp,
            cornerRadius = 28.dp,
            spacing = 12.dp,
            blurredCopy = hasBackdrop,
            tint = LocalGlassColors.current.panelTint.copy(alpha = if (hasBackdrop) GLASS_DIALOG_TINT_ALPHA else GLASS_DIALOG_TINT_ALPHA_NO_BACKDROP),
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

/** Opacidad del tinte con la copia borrosa debajo: la misma que las tarjetas de Home. Provisional: se ajusta en el teléfono. */
private const val GLASS_DIALOG_TINT_ALPHA = 0.70f

/** Sin copia borrosa (fuera de Home) se transparentaría la pantalla: el tinte es mucho más asentado. */
private const val GLASS_DIALOG_TINT_ALPHA_NO_BACKDROP = 0.92f
