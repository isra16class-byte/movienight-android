package com.isra16.movienight.ui.auth

import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/*
 * Fondo quieto y vidrio de login, registro y recuperar contraseña (Fase 8, paso 3).
 *
 * Cómo funciona:
 *  - [AuthBackdrop] envuelve el NavHost de las tres pantallas, así el fondo es UNO solo y no parpadea al pasar de una
 *    a otra. Dibuja la imagen del modo actual a pantalla completa (recorte centrado, `coverCropRect`) y un velo
 *    encima. Queda FUERA del `safeDrawingPadding` de las pantallas: no se mueve con el teclado.
 *  - [GlassPanel] es el panel de cada pantalla (va dentro del scroll). Dibuja la COPIA BORROSA de la misma imagen,
 *    en el mismo rectángulo que el fondo pero trasladado por la posición real del panel (`blurRectInPanel`), así que
 *    al desplazar se ve lo que hay detrás. Encima, el velo y el tinte del panel y un borde fino.
 *  - Sin `Modifier.blur` ni librerías: el desenfoque está horneado en `drawable-nodpi/auth_bg_*_blur.webp`.
 *  - Las imágenes se decodifican fuera del hilo principal; mientras no están, se ve el color de fondo del tema y el
 *    panel solo con su tinte (que es también el "plan B" sin desenfoque).
 */

/**
 * Interruptor del desenfoque. `false` = plan B: el panel es solo un tinte semitransparente con borde, sin copia
 * borrosa (no necesita alineación). Es lo que hay que poner si la copia borrosa se ve desalineada al desplazar.
 */
private const val USE_BLURRED_COPY = true

/** Las dos imágenes del modo actual: la nítida (fondo) y su copia borrosa (dentro del panel). */
internal class BackdropImages(val sharp: ImageBitmap, val blurred: ImageBitmap)

/**
 * Estado compartido entre el fondo y los paneles. Los campos se leen SOLO al dibujar (dentro de `drawBehind`), así
 * que cambiarlos (tamaño, posición al desplazar, imágenes que llegan) vuelve a dibujar pero no recompone.
 */
@Stable
internal class GlassBackdropState {
    var images by mutableStateOf<BackdropImages?>(null)
    var size by mutableStateOf(IntSize.Zero)
    var origin by mutableStateOf(Offset.Zero)
}

internal val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdropState?> { null }

/** Dibuja [image] ocupando [rect], con el mismo redondeo para el fondo y para la copia borrosa. */
private fun DrawScope.drawCover(image: ImageBitmap, rect: GlassRect) {
    if (rect.width <= 0f || rect.height <= 0f) return
    drawImage(
        image = image,
        dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
        dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
    )
}

private fun loadBackdropImages(resources: Resources, dark: Boolean): BackdropImages? {
    // `drawable-nodpi` + inScaled=false: sin reescalado por densidad; el recorte lo hace el dibujo.
    val options = BitmapFactory.Options().apply { inScaled = false }
    val sharp = BitmapFactory.decodeResource(
        resources, if (dark) R.drawable.auth_bg_dark else R.drawable.auth_bg_light, options,
    )
    val blurred = BitmapFactory.decodeResource(
        resources, if (dark) R.drawable.auth_bg_dark_blur else R.drawable.auth_bg_light_blur, options,
    )
    if (sharp == null || blurred == null) return null
    return BackdropImages(sharp.asImageBitmap(), blurred.asImageBitmap())
}

/** Fondo quieto con velo, a pantalla completa y detrás de las barras del sistema. Envuelve las pantallas de entrada. */
@Composable
fun AuthBackdrop(content: @Composable () -> Unit) {
    val dark = LocalAppDarkTheme.current
    val glass = LocalGlassColors.current
    val base = MaterialTheme.colorScheme.background
    val resources = LocalContext.current.resources
    val state = remember { GlassBackdropState() }

    // El modo no cambia mientras se ven estas pantallas (el interruptor está en Home), pero la clave lo cubre igual.
    LaunchedEffect(dark) {
        state.images = withContext(Dispatchers.Default) { loadBackdropImages(resources, dark) }
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
                val images = state.images
                if (images != null) {
                    drawCover(images.sharp, coverCropRect(images.sharp.width, images.sharp.height, size.width, size.height))
                    drawRect(glass.scrim)
                }
            },
    ) {
        CompositionLocalProvider(LocalGlassBackdrop provides state) { content() }
    }
}

/**
 * Panel de vidrio: lo que se ve detrás (la copia borrosa alineada con el fondo) más el velo, el tinte y un borde fino.
 * Debe ir dentro de [AuthBackdrop]; sin él, o con [USE_BLURRED_COPY] en `false`, es un panel solo con tinte.
 * Los textos del panel usan `onSurface` por defecto (con ese color se midió el contraste); en modo claro, con los
 * colores de `GlassColors.content` (ver `Color.kt`).
 */
@Composable
internal fun GlassPanel(content: @Composable ColumnScope.() -> Unit) {
    val backdrop = LocalGlassBackdrop.current
    val glass = LocalGlassColors.current
    val shape = RoundedCornerShape(24.dp)
    // Posición del panel en la ventana; se actualiza al desplazar y solo se lee al dibujar.
    var panelOrigin by remember { mutableStateOf(Offset.Zero) }
    // Modo claro: texto y contornos más oscuros SOLO dentro del panel, para poder dejarlo más transparente.
    val baseScheme = MaterialTheme.colorScheme
    val panelScheme = remember(baseScheme, glass) {
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

    MaterialTheme(colorScheme = panelScheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        CompositionLocalProvider(LocalContentColor provides panelScheme.onSurface) {
            Column(
                modifier = Modifier
                    .onGloballyPositioned { panelOrigin = it.positionInRoot() }
                    .clip(shape)
                    .drawBehind {
                        val images = backdrop?.images
                        if (USE_BLURRED_COPY && backdrop != null && images != null) {
                            val image = coverCropRect(
                                images.sharp.width, images.sharp.height,
                                backdrop.size.width.toFloat(), backdrop.size.height.toFloat(),
                            )
                            drawCover(
                                images.blurred,
                                blurRectInPanel(image, panelOrigin.x - backdrop.origin.x, panelOrigin.y - backdrop.origin.y),
                            )
                            drawRect(glass.scrim)
                        }
                        drawRect(glass.panelTint)
                    }
                    .border(1.dp, glass.panelBorder, shape)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}
