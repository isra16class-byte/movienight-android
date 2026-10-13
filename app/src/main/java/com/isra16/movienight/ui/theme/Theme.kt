package com.isra16.movienight.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Tema de la app. El modo (claro u oscuro) lo decide la persona con el interruptor de la pantalla principal; no se
 * sigue el ajuste del teléfono, y el color dinámico de Android 12+ está apagado a propósito: todas las personas ven
 * la misma paleta. Los colores están en `Color.kt` (aquí no hay ninguno).
 */
@Composable
fun MovieNightTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) MovieNightDarkColors else MovieNightLightColors,
        typography = Typography,
        content = content
    )
}
