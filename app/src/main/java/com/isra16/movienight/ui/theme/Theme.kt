package com.isra16.movienight.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Modo de la APP (no del teléfono): `true` si la persona tiene puesto el modo oscuro con el interruptor de Home.
 * Dentro de las pantallas se lee de aquí y NO con `isSystemInDarkTheme()`, que devolvería el modo del teléfono y
 * no el de la app (la app no sigue al teléfono). Lo provee [MovieNightTheme] con el mismo valor que recibe.
 * El valor por defecto (oscuro, como el primer arranque) solo cuenta fuera de [MovieNightTheme], p. ej. en una vista previa.
 */
val LocalAppDarkTheme = staticCompositionLocalOf { true }

/** Colores del vidrio de las pantallas de entrada (ver `GlassColors` en `Color.kt`); los provee [MovieNightTheme]. */
internal val LocalGlassColors = staticCompositionLocalOf { MovieNightDarkGlass }

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
    CompositionLocalProvider(
        LocalAppDarkTheme provides darkTheme,
        LocalGlassColors provides (if (darkTheme) MovieNightDarkGlass else MovieNightLightGlass),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) MovieNightDarkColors else MovieNightLightColors,
            typography = Typography,
            content = content
        )
    }
}
