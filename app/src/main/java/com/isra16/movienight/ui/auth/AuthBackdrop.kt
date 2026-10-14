package com.isra16.movienight.ui.auth

import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.isra16.movienight.R
import com.isra16.movienight.net.GlassRect
import com.isra16.movienight.net.blurRectInPanel
import com.isra16.movienight.net.coverCropRect
import com.isra16.movienight.ui.theme.LocalAppDarkTheme
import com.isra16.movienight.ui.theme.LocalGlassColors
import com.isra16.movienight.ui.theme.Typography as AppTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/*
 * Hoja de vidrio de pantalla completa de login, registro y recuperar contraseña (Fase 8, paso 3).
 *
 * Cómo funciona:
 *  - [AuthBackdrop] envuelve el NavHost de las tres pantallas, así el fondo es UNO solo y no parpadea al pasar de una
 *    a otra. Dibuja, a pantalla completa y detrás de las barras del sistema: el color de fondo del tema, la COPIA
 *    BORROSA de la imagen del modo (recorte centrado, `coverCropRect`) y encima el velo en degradado vertical
 *    (`GlassColors.veil` con `veilAlphas`). Esa copia borrosa más el velo ES el vidrio: no hay panel ni bordes. Queda
 *    FUERA del `safeDrawingPadding` de las pantallas: no se mueve con el teclado ni con el desplazamiento.
 *  - La imagen nítida (`auth_bg_*.webp`) ya no se dibuja en estas pantallas; sigue en `res` para lo que venga después.
 *  - Sin `Modifier.blur` ni librerías: el desenfoque está horneado en `drawable-nodpi/auth_bg_*_blur.webp`.
 *  - La imagen se decodifica fuera del hilo principal; mientras no está, se ve el color de fondo del tema (el velo, del
 *    mismo color, no cambia nada).
 *  - [GlassContent] cambia los colores del texto de TODO lo que lleva dentro (pantallas de entrada) cuando el modo lo
 *    pide (hoy el claro, con texto más oscuro; ver `GlassColors.content`).
 */

/**
 * Sombra de TODO el texto de las pantallas de entrada. Con el velo leve (10 %) es lo que da legibilidad sobre las zonas
 * claras de la imagen (cielo, flores blancas); el contraste de píxeles no la mide. Valores provisionales (px, no dp).
 */
internal val GlassTextShadow = Shadow(color = Color.Black.copy(alpha = 0.65f), offset = Offset(0f, 2f), blurRadius = 6f)

/** Sombra y letra más gruesa: lo que pesa menos de SemiBold (600) sube a SemiBold; lo que ya es más grueso se queda. */
private fun TextStyle.glass(shadow: Shadow?): TextStyle =
    copy(shadow = shadow, fontWeight = if ((fontWeight?.weight ?: 400) < 600) FontWeight.SemiBold else fontWeight)

/** La misma tipografía con la sombra y el peso de [glass] en cada estilo; así llega a todo `Text` que use `MaterialTheme.typography`. */
internal fun Typography.withShadow(shadow: Shadow?) = Typography(
    displayLarge = displayLarge.glass(shadow),
    displayMedium = displayMedium.glass(shadow),
    displaySmall = displaySmall.glass(shadow),
    headlineLarge = headlineLarge.glass(shadow),
    headlineMedium = headlineMedium.glass(shadow),
    headlineSmall = headlineSmall.glass(shadow),
    titleLarge = titleLarge.glass(shadow),
    titleMedium = titleMedium.glass(shadow),
    titleSmall = titleSmall.glass(shadow),
    bodyLarge = bodyLarge.glass(shadow),
    bodyMedium = bodyMedium.glass(shadow),
    bodySmall = bodySmall.glass(shadow),
    labelLarge = labelLarge.glass(shadow),
    labelMedium = labelMedium.glass(shadow),
    labelSmall = labelSmall.glass(shadow),
)

/** La misma tipografía sin sombra y con los pesos tal como son (a diferencia de [withShadow], no sube nada a SemiBold). */
private fun Typography.withoutShadow() = Typography(
    displayLarge = displayLarge.copy(shadow = null),
    displayMedium = displayMedium.copy(shadow = null),
    displaySmall = displaySmall.copy(shadow = null),
    headlineLarge = headlineLarge.copy(shadow = null),
    headlineMedium = headlineMedium.copy(shadow = null),
    headlineSmall = headlineSmall.copy(shadow = null),
    titleLarge = titleLarge.copy(shadow = null),
    titleMedium = titleMedium.copy(shadow = null),
    titleSmall = titleSmall.copy(shadow = null),
    bodyLarge = bodyLarge.copy(shadow = null),
    bodyMedium = bodyMedium.copy(shadow = null),
    bodySmall = bodySmall.copy(shadow = null),
    labelLarge = labelLarge.copy(shadow = null),
    labelMedium = labelMedium.copy(shadow = null),
    labelSmall = labelSmall.copy(shadow = null),
)

/** Dibuja [image] ocupando [rect]. */
private fun DrawScope.drawCover(image: ImageBitmap, rect: GlassRect) {
    if (rect.width <= 0f || rect.height <= 0f) return
    drawImage(
        image = image,
        dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
        dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
    )
}

/**
 * Estado compartido entre el fondo (la hoja) y el panel del formulario. Los campos se leen SOLO al dibujar (dentro de
 * `drawBehind`), así que cambiarlos (tamaño, imágenes que llegan) vuelve a dibujar pero no recompone.
 *  - [panelImage]: la copia MUY borrosa que se ve dentro del panel (`auth_panel_*.webp`, ~6 KB).
 *  - [sheetSize]: tamaño de la imagen de la hoja, para que el panel use el mismo recorte que el fondo.
 *  - [size] y [origin]: tamaño y posición en la ventana del fondo.
 */
@Stable
internal class GlassBackdropState {
    var panelImage by mutableStateOf<ImageBitmap?>(null)
    var sheetSize by mutableStateOf(IntSize.Zero)
    var size by mutableStateOf(IntSize.Zero)
    var origin by mutableStateOf(Offset.Zero)
}

internal val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdropState?> { null }

private fun loadPanelBackdrop(resources: Resources, dark: Boolean): ImageBitmap? {
    val options = BitmapFactory.Options().apply { inScaled = false }
    return BitmapFactory.decodeResource(
        resources, if (dark) R.drawable.auth_panel_dark else R.drawable.auth_panel_light, options,
    )?.asImageBitmap()
}

private fun loadBlurredBackdrop(resources: Resources, dark: Boolean): ImageBitmap? {
    // `drawable-nodpi` + inScaled=false: sin reescalado por densidad; el recorte lo hace el dibujo.
    val options = BitmapFactory.Options().apply { inScaled = false }
    val blurred = BitmapFactory.decodeResource(
        resources, if (dark) R.drawable.auth_bg_dark_blur else R.drawable.auth_bg_light_blur, options,
    )
    return blurred?.asImageBitmap()
}

/** Hoja de vidrio de pantalla completa: copia borrosa del fondo con un velo en degradado. Envuelve las pantallas de entrada. */
@Composable
fun AuthBackdrop(content: @Composable () -> Unit) {
    val dark = LocalAppDarkTheme.current
    val glass = LocalGlassColors.current
    val base = MaterialTheme.colorScheme.background
    val resources = LocalContext.current.resources
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    val state = remember { GlassBackdropState() }

    // El modo no cambia mientras se ven estas pantallas (el interruptor está en Home), pero la clave lo cubre igual.
    LaunchedEffect(dark) {
        val (sheet, panel) = withContext(Dispatchers.Default) { loadBlurredBackdrop(resources, dark) to loadPanelBackdrop(resources, dark) }
        state.sheetSize = if (sheet != null) IntSize(sheet.width, sheet.height) else IntSize.Zero
        state.panelImage = panel
        image = sheet
    }

    // Paradas igualmente espaciadas de 0 a 1; el alto del degradado es el de la ventana (endY por defecto = alto).
    val veil = remember(glass) {
        val last = (glass.veilAlphas.size - 1).coerceAtLeast(1)
        Brush.verticalGradient(
            *Array(glass.veilAlphas.size) { k -> (k.toFloat() / last) to glass.veil.copy(alpha = glass.veilAlphas[k]) },
        )
    }

    // propagateMinConstraints: el NavHost de adentro ocupa toda la ventana, como cuando estaba directo bajo el Surface.
    Box(
        propagateMinConstraints = true,
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                state.size = it.size
                state.origin = it.positionInRoot()
            }
            .clipToBounds()
            .drawBehind {
                drawRect(base)
                val blurred = image
                if (blurred != null) {
                    drawCover(blurred, coverCropRect(blurred.width, blurred.height, size.width, size.height))
                    drawRect(veil)
                }
            },
    ) {
        CompositionLocalProvider(LocalGlassBackdrop provides state) { content() }
    }
}

/**
 * Panel de vidrio blanco que lleva el formulario (título, campos, botón y enlaces), encima de la hoja de fondo, que no
 * cambia. Dibuja la copia MUY borrosa de la misma imagen en el mismo rectángulo que el fondo pero trasladada por la
 * posición real del panel (`blurRectInPanel`), así al desplazar se ve lo que hay detrás; encima, el tinte y un borde
 * fino. Sin la copia (todavía no cargó) queda solo el tinte. Debe ir dentro de [AuthBackdrop] y de [GlassContent].
 * Todo lo de adentro usa los colores de [GlassBarContent]: oscuros en el modo claro, y sin sombra.
 */
@Composable
internal fun GlassPanel(content: @Composable ColumnScope.() -> Unit) {
    val backdrop = LocalGlassBackdrop.current
    val glass = LocalGlassColors.current
    val shape = RoundedCornerShape(28.dp)
    // Posición del panel en la ventana; se actualiza al desplazar y solo se lee al dibujar.
    var panelOrigin by remember { mutableStateOf(Offset.Zero) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { panelOrigin = it.positionInRoot() }
            .clip(shape)
            .drawBehind {
                val panel = backdrop?.panelImage
                if (backdrop != null && panel != null && backdrop.sheetSize.width > 0 && backdrop.size.width > 0) {
                    val image = coverCropRect(
                        backdrop.sheetSize.width, backdrop.sheetSize.height,
                        backdrop.size.width.toFloat(), backdrop.size.height.toFloat(),
                    )
                    drawCover(panel, blurRectInPanel(image, panelOrigin.x - backdrop.origin.x, panelOrigin.y - backdrop.origin.y))
                }
                drawRect(glass.panelTint)
            }
            .border(1.dp, glass.panelBorder, shape)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val scope = this
        GlassBarContent { scope.content() }
    }
}

/**
 * Texto de las pantallas de entrada: colores de la paleta del modo, salvo en modo claro, que usa los de
 * `GlassColors.content` (crema), y con [GlassTextShadow] en todos los estilos. Debe ir dentro de [AuthBackdrop].
 */
@Composable
internal fun GlassContent(content: @Composable () -> Unit) {
    val glass = LocalGlassColors.current
    val baseScheme = MaterialTheme.colorScheme
    val scheme = remember(baseScheme, glass) {
        val c = glass.content
        if (c == null) {
            baseScheme
        } else {
            baseScheme.copy(
                onSurface = c.onSurface,
                onBackground = c.onSurface,
                onSurfaceVariant = c.onSurfaceVariant,
                primary = c.primary,
                error = c.error,
                outline = c.outline,
            )
        }
    }
    // MaterialTheme.typography es @Composable: se lee fuera del bloque de remember.
    val baseTypography = MaterialTheme.typography
    val typography = remember(baseTypography) { baseTypography.withShadow(GlassTextShadow) }
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = MaterialTheme.shapes) {
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface, content = content)
    }
}

/**
 * Texto de lo que va DENTRO del panel ([GlassPanel]): sin sombra (el fondo del panel es liso), con los PESOS ORIGINALES
 * de la tipografía de la app (diseño "cristal": letra fina; fuera del panel `GlassContent` sigue subiéndolos a SemiBold)
 * y, en el modo claro, con los colores oscuros de `GlassColors.barContent`, que se leen sobre el panel blanco. Sin
 * `barContent` (modo oscuro) conserva la paleta del modo. Debe ir dentro de [GlassContent].
 */
@Composable
internal fun GlassBarContent(content: @Composable () -> Unit) {
    val ink = LocalGlassColors.current.barContent
    val baseScheme = MaterialTheme.colorScheme
    val scheme = remember(baseScheme, ink) {
        if (ink == null) {
            baseScheme
        } else {
            baseScheme.copy(
                onSurface = ink.onSurface,
                onBackground = ink.onSurface,
                onSurfaceVariant = ink.onSurfaceVariant,
                primary = ink.primary,
                error = ink.error,
                outline = ink.outline,
            )
        }
    }
    val typography = remember { AppTypography.withoutShadow() }
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = MaterialTheme.shapes) {
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface, content = content)
    }
}
