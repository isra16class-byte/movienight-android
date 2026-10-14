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
 * Colores del texto sobre la hoja de vidrio cuando el modo necesita los suyos en vez de los de la paleta normal (hoy
 * solo el claro: texto crema, como en la maqueta). `GlassContent` los aplica solo a las pantallas de entrada; el
 * resto de la app sigue con el `ColorScheme` del modo.
 */
@Immutable
class GlassContentColors(
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val primary: Color,
    val error: Color,
    val outline: Color,
)

/**
 * Colores del vidrio de login, registro y recuperar contraseña: UNA hoja que cubre toda la pantalla (sin panel ni
 * esquinas), transparente: la imagen borrosa se ve casi tal cual. Fuera del `ColorScheme` de Material porque no son
 * roles de Material; se leen con `LocalGlassColors` (ver `Theme.kt`). Capas, de abajo arriba:
 *  1. la copia borrosa de la imagen de fondo, a pantalla completa;
 *  2. el velo: [veil] (color OSCURO liso) con un alfa que puede cambiar de arriba a abajo ([veilAlphas], paradas
 *     igualmente espaciadas; ver `veilAlphaAt`). Hoy es 10 % parejo, a pedido de la persona (antes 3 %);
 *  3. solo en la barra de campos y el botón: [barTint] y [buttonTint] (con alfa; en el modo claro son color crema, que
 *     aclara el vidrio, y en el oscuro la barra sigue con tinte oscuro y el botón sin relleno) y [barBorder], el borde fino.
 *     En el modo claro el texto de la barra y del botón es oscuro ([barContent]) para leerse sobre ese tinte crema.
 *
 * ATENCIÓN, decisión de diseño: con velo tan leve el contraste del texto NO llega a 4,5:1 en toda la imagen (ver
 * `tools/glass/backdrop_tools.py measure`, que da el porcentaje de píxeles que sí). La legibilidad la completa la
 * sombra del texto (`GlassTextShadow` en `AuthBackdrop.kt`), que esa cuenta no mide. La versión anterior (velo
 * claro de 30 a 55 %) cumplía 4,5:1 pero se veía lechosa. Si se cambia la imagen o la paleta (paso 6), hay que volver
 * a mirar.
 */
@Immutable
class GlassColors(
    val veil: Color,
    val veilAlphas: List<Float>,
    val barTint: Color,
    val barBorder: Color,
    val buttonTint: Color = Color.Transparent,
    val content: GlassContentColors? = null,
    // Colores del texto DENTRO del panel (modo claro: oscuros, sobre el panel blanco); null = los mismos que fuera.
    val barContent: GlassContentColors? = null,
    // Panel de vidrio que lleva el formulario: tinte (sobre la copia borrosa) y borde fino.
    val panelTint: Color,
    val panelBorder: Color,
)

private val GlassVeilAlphas = List(21) { 0.10f }
private val GlassCream = Color(0xFFFFF8E7)

/**
 * Texto oscuro para dentro de la barra de campos y del botón del modo claro, sobre el tinte crema (el texto crema se
 * perdía ahí). Contraste medido con la copia borrosa real y los tintes de hoy (barra 25 %, botón 12 %), sin sombra:
 * `onSurface` >= 4,5:1 en ~94 % de los píxeles de la barra y ~88 % de los del botón; `onSurfaceVariant` (etiquetas)
 * ~89 %; `error` (mensaje de error dentro de la barra) ~87 %. Provisionales, igual que el resto de la paleta.
 */
private val GlassBarInk = GlassContentColors(
    onSurface = Color(0xFF0E1511),
    onSurfaceVariant = Color(0xFF1F2923),
    primary = Color(0xFF0E1511),
    error = Color(0xFF5C0005),
    outline = Color(0xFF1F2923),
)

internal val MovieNightLightGlass = GlassColors(
    veil = DarkBackground,
    veilAlphas = GlassVeilAlphas,
    barTint = GlassCream.copy(alpha = 0.25f),
    barBorder = Color(0xFF1F2923).copy(alpha = 0.5f), // oscuro: el crema no se vería sobre el panel blanco
    buttonTint = GlassCream.copy(alpha = 0.12f),
    content = GlassContentColors(
        onSurface = GlassCream,
        onSurfaceVariant = GlassCream,
        primary = GlassCream,
        error = Color(0xFFFFB4AB),
        outline = GlassCream,
    ),
    barContent = GlassBarInk,
    // 45 % (antes 62 %): más transparente a pedido de la persona. Texto oscuro sobre la copia borrosa real y este tinte
    // (sin la barra crema ni la sombra): peor píxel 4,63:1 y 100 % >= 4,5:1; con 40 % ya baja a 3,93:1 en el peor píxel.
    panelTint = Color.White.copy(alpha = 0.45f),
    panelBorder = Color.White.copy(alpha = 0.70f),
)

internal val MovieNightDarkGlass = GlassColors(
    veil = DarkBackground,
    veilAlphas = GlassVeilAlphas,
    barTint = DarkBackground.copy(alpha = 0.25f),
    barBorder = Color(0xFFD8E0DA),
    panelTint = DarkBackground.copy(alpha = 0.55f),
    panelBorder = Color(0xFFD8E0DA).copy(alpha = 0.25f),
)
