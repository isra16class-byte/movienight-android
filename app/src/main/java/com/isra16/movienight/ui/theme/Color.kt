package com.isra16.movienight.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * TODOS los colores de la app viven en este archivo (Fase 8 del plan). Cambiar la paleta es editar solo esto.
 *
 * >>> PALETA PROVISIONAL <<<
 * Estos valores NO son la identidad final: son verdes y azul cielo neutros, puestos para que los dos modos se vean
 * bien y legibles mientras se rediseñan las pantallas. La temática, la paleta de cada modo, las tipografías y el
 * logo se eligen en el paso 6 (ver "Fase 8" en docs/PLAN-PRODUCCION.md). Contraste (WCAG) comprobado por el
 * asistente: texto sobre su fondo >= 4,5:1 y bordes >= 3:1 en los dos modos.
 *
 * Reglas:
 *  - Ningún otro archivo de la app (salvo la pantalla completa de la sala, que usa blanco/negro/`Scrim` a
 *    propósito sobre el video) pone colores a mano: todo sale de MaterialTheme.colorScheme.
 *  - La app no usa el color dinámico de Android 12+: así todas las personas ven la misma paleta.
 *  - El fondo oscuro de abajo (`DarkBackground`) debe coincidir con `window_background` en res/values/colors.xml:
 *    ese es el color de la ventana mientras la app arranca y XML no puede leer este archivo.
 */

// --- Modo claro (provisional) -----------------------------------------------------------------------------------
private val LightPrimary = Color(0xFF1B6B3F)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFB4F1C3)
private val LightOnPrimaryContainer = Color(0xFF00210F)
private val LightInversePrimary = Color(0xFF79DA9C)
private val LightSecondary = Color(0xFF4F6354)
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFD1E8D5)
private val LightOnSecondaryContainer = Color(0xFF0C1F13)
private val LightTertiary = Color(0xFF2D6580)
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightTertiaryContainer = Color(0xFFC5E7FF)
private val LightOnTertiaryContainer = Color(0xFF001E2D)
private val LightBackground = Color(0xFFF5FBF5)
private val LightOnBackground = Color(0xFF171D19)
private val LightSurface = Color(0xFFF5FBF5)
private val LightOnSurface = Color(0xFF171D19)
private val LightSurfaceVariant = Color(0xFFDBE5DC)
private val LightOnSurfaceVariant = Color(0xFF404943)
private val LightInverseSurface = Color(0xFF2C322E)
private val LightInverseOnSurface = Color(0xFFECF2EC)
private val LightError = Color(0xFFBA1A1A)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFFFDAD6)
private val LightOnErrorContainer = Color(0xFF410002)
private val LightOutline = Color(0xFF707972)
private val LightOutlineVariant = Color(0xFFBFC9C1)
private val LightScrim = Color(0xFF000000)
private val LightSurfaceDim = Color(0xFFD5DBD5)
private val LightSurfaceBright = Color(0xFFF5FBF5)
private val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
private val LightSurfaceContainerLow = Color(0xFFEFF5EF)
private val LightSurfaceContainer = Color(0xFFE9EFE9)
private val LightSurfaceContainerHigh = Color(0xFFE3EAE4)
private val LightSurfaceContainerHighest = Color(0xFFDEE4DE)

// --- Modo oscuro (provisional) ----------------------------------------------------------------------------------
private val DarkPrimary = Color(0xFF79DA9C)
private val DarkOnPrimary = Color(0xFF00391C)
private val DarkPrimaryContainer = Color(0xFF00522B)
private val DarkOnPrimaryContainer = Color(0xFF95F7B5)
private val DarkInversePrimary = Color(0xFF1B6B3F)
private val DarkSecondary = Color(0xFFB5CCB9)
private val DarkOnSecondary = Color(0xFF213527)
private val DarkSecondaryContainer = Color(0xFF374B3D)
private val DarkOnSecondaryContainer = Color(0xFFD1E8D5)
private val DarkTertiary = Color(0xFF94CDEA)
private val DarkOnTertiary = Color(0xFF00344A)
private val DarkTertiaryContainer = Color(0xFF0B4B66)
private val DarkOnTertiaryContainer = Color(0xFFC5E7FF)
private val DarkBackground = Color(0xFF0E1511) // = window_background en res/values/colors.xml
private val DarkOnBackground = Color(0xFFDEE4DE)
private val DarkSurface = Color(0xFF0E1511)
private val DarkOnSurface = Color(0xFFDEE4DE)
private val DarkSurfaceVariant = Color(0xFF404943)
private val DarkOnSurfaceVariant = Color(0xFFBFC9C1)
private val DarkInverseSurface = Color(0xFFDEE4DE)
private val DarkInverseOnSurface = Color(0xFF2C322E)
private val DarkError = Color(0xFFFFB4AB)
private val DarkOnError = Color(0xFF690005)
private val DarkErrorContainer = Color(0xFF93000A)
private val DarkOnErrorContainer = Color(0xFFFFDAD6)
private val DarkOutline = Color(0xFF8A938B)
private val DarkOutlineVariant = Color(0xFF404943)
private val DarkScrim = Color(0xFF000000)
private val DarkSurfaceDim = Color(0xFF0E1511)
private val DarkSurfaceBright = Color(0xFF343B36)
private val DarkSurfaceContainerLowest = Color(0xFF090F0C)
private val DarkSurfaceContainerLow = Color(0xFF161D19)
private val DarkSurfaceContainer = Color(0xFF1A211D)
private val DarkSurfaceContainerHigh = Color(0xFF252C27)
private val DarkSurfaceContainerHighest = Color(0xFF303632)

/*
 * Se dan TODOS los roles (incluidos los de contenedores de superficie): los que no se den quedan con los valores
 * por defecto de Material 3, que son violeta, y se colarían en tarjetas y diálogos.
 */
internal val MovieNightLightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    inversePrimary = LightInversePrimary,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = LightPrimary,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = LightScrim,
    surfaceBright = LightSurfaceBright,
    surfaceDim = LightSurfaceDim,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainerLowest = LightSurfaceContainerLowest,
)

internal val MovieNightDarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = DarkInversePrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkPrimary,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = DarkScrim,
    surfaceBright = DarkSurfaceBright,
    surfaceDim = DarkSurfaceDim,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
)

// --- Vidrio de las pantallas de entrada (Fase 8, paso 3; PROVISIONAL, igual que el resto de la paleta) ---------------

/**
 * Colores del efecto de vidrio de login, registro y recuperar contraseña. Fuera del `ColorScheme` de Material porque
 * no son roles de Material; se leen con `LocalGlassColors` (ver `Theme.kt`). Capas, de abajo arriba:
 *  1. la imagen de fondo (nítida fuera del panel; su copia borrosa dentro);
 *  2. [scrim]: velo del color de fondo del modo, sobre la imagen (también sobre la copia borrosa dentro del panel);
 *  3. [panelTint]: tinte del panel, encima del velo;
 *  4. [panelBorder]: borde fino del panel (decorativo: los campos llevan su propio contorno).
 *
 * Los alfas salen de MEDIR la imagen real detrás del panel (ver `net/GlassLogic.kt`): el peor caso entre varios
 * tamaños de ventana da texto >= 4,5:1 con onSurface, onSurfaceVariant, primary y error, y >= 3:1 para el contorno de
 * los campos. Si se cambia la imagen o la paleta (paso 6), hay que volver a medir.
 */
@Immutable
class GlassColors(
    val scrim: Color,
    val panelTint: Color,
    val panelBorder: Color,
)

// Medido (peor caso, ventana 9:16): onSurface 12,9 / onSurfaceVariant 7,0 / primary 4,9 / error 4,9 / contorno 3,4.
// Es el modo que más tinte necesita: el texto es oscuro y la foto de día tiene zonas oscuras (troncos).
internal val MovieNightLightGlass = GlassColors(
    scrim = LightBackground.copy(alpha = 0.35f),
    panelTint = LightSurface.copy(alpha = 0.80f),
    panelBorder = LightOutline.copy(alpha = 0.40f),
)

// Medido (peor caso, ventana 9:16): onSurface 8,4 / onSurfaceVariant 6,4 / primary 6,4 / error 6,4 / contorno 3,4.
internal val MovieNightDarkGlass = GlassColors(
    scrim = DarkBackground.copy(alpha = 0.15f),
    panelTint = DarkSurface.copy(alpha = 0.50f),
    panelBorder = Color.White.copy(alpha = 0.25f),
)
