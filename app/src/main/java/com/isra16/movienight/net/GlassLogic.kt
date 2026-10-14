package com.isra16.movienight.net

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/*
 * Cuentas del fondo y del vidrio de las pantallas de entrada (Fase 8, paso 3). JVM puro, sin Android ni Compose.
 *
 * Qué resuelve:
 *  - `coverCropRect`: dónde se dibuja la imagen de fondo para cubrir la ventana, centrada y recortada (lo mismo que
 *    ContentScale.Crop con alineación al centro, pero calculado aquí para que el fondo nítido y la copia borrosa
 *    usen EXACTAMENTE el mismo rectángulo).
 *  - `blurRectInPanel`: dónde dibujar la copia borrosa DENTRO del panel para que quede alineada con el fondo nítido,
 *    aunque el panel se mueva al desplazar. Es el mismo rectángulo del fondo, trasladado por la posición del panel.
 *  - `visibleSourceFraction`: qué parte de la imagen queda a la vista (útil en horizontal, donde se recorta a una franja).
 *  - Contraste WCAG (`relativeLuminance`, `contrastRatio`, `contrastOnGlass`): la misma cuenta que se usó para medir
 *    la imagen real detrás del panel y fijar los tokens de `Color.kt`.
 */

/** Rectángulo en píxeles de la ventana (o del panel): esquina superior izquierda y tamaño. */
data class GlassRect(val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * Rectángulo donde se dibuja una imagen de [srcWidth] x [srcHeight] para cubrir una ventana de
 * [dstWidth] x [dstHeight] sin deformarla: se escala hasta cubrir las dos medidas y se centra (lo que sobra se recorta,
 * y por eso `left` o `top` salen negativos). Con tamaños no positivos devuelve un rectángulo vacío.
 */
fun coverCropRect(srcWidth: Int, srcHeight: Int, dstWidth: Float, dstHeight: Float): GlassRect {
    if (srcWidth <= 0 || srcHeight <= 0 || dstWidth <= 0f || dstHeight <= 0f) return GlassRect(0f, 0f, 0f, 0f)
    val scale = max(dstWidth / srcWidth, dstHeight / srcHeight)
    val width = srcWidth * scale
    val height = srcHeight * scale
    return GlassRect((dstWidth - width) / 2f, (dstHeight - height) / 2f, width, height)
}

/**
 * Rectángulo donde dibujar la copia borrosa dentro de un panel cuya esquina superior izquierda está en
 * ([panelLeft], [panelTop]), medida en el mismo sistema que [imageRect] (el del fondo). Mismo tamaño que el fondo,
 * trasladado en sentido contrario al panel: así, lo que se ve a través del vidrio es lo que hay detrás.
 */
fun blurRectInPanel(imageRect: GlassRect, panelLeft: Float, panelTop: Float): GlassRect =
    GlassRect(imageRect.left - panelLeft, imageRect.top - panelTop, imageRect.width, imageRect.height)

/** Parte de la imagen que queda a la vista, en fracciones de 0 a 1 de su ancho y su alto. */
data class VisibleFraction(val left: Float, val top: Float, val width: Float, val height: Float)

/** Qué parte de la imagen se ve al cubrir una ventana de [dstWidth] x [dstHeight] con el recorte centrado. */
fun visibleSourceFraction(srcWidth: Int, srcHeight: Int, dstWidth: Float, dstHeight: Float): VisibleFraction {
    val rect = coverCropRect(srcWidth, srcHeight, dstWidth, dstHeight)
    if (rect.width <= 0f || rect.height <= 0f) return VisibleFraction(0f, 0f, 0f, 0f)
    val w = min(1f, dstWidth / rect.width)
    val h = min(1f, dstHeight / rect.height)
    return VisibleFraction((1f - w) / 2f, (1f - h) / 2f, w, h)
}

// --- Contraste (WCAG 2.x) ---------------------------------------------------------------------------------------

private fun linearChannel(c8: Double): Double {
    val c = c8 / 255.0
    return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

private fun luminanceOf(r: Double, g: Double, b: Double): Double =
    0.2126 * linearChannel(r) + 0.7152 * linearChannel(g) + 0.0722 * linearChannel(b)

private fun channel(argb: Int, shift: Int): Double = ((argb shr shift) and 0xFF).toDouble()

/** Luminancia relativa (0 negro, 1 blanco) de un color ARGB; el canal alfa se ignora. */
fun relativeLuminance(argb: Int): Double = luminanceOf(channel(argb, 16), channel(argb, 8), channel(argb, 0))

private fun ratio(l1: Double, l2: Double): Double = (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)

/** Razón de contraste WCAG entre dos colores opacos (de 1 a 21); el canal alfa se ignora. */
fun contrastRatio(argbA: Int, argbB: Int): Double = ratio(relativeLuminance(argbA), relativeLuminance(argbB))

/**
 * Contraste de un texto sobre lo que se ve en un panel de vidrio, con la misma pila de capas que dibuja la app:
 * la imagen ([imageRgb], el píxel de la copia borrosa), encima el velo ([scrim] con [scrimAlpha]) y encima el
 * tinte del panel ([tint] con [tintAlpha]). Las cuentas van en Double y sin redondear los canales.
 */
fun contrastOnGlass(
    textArgb: Int,
    imageRgb: Int,
    scrim: Int,
    scrimAlpha: Float,
    tint: Int,
    tintAlpha: Float,
): Double {
    fun over(top: Double, a: Double, below: Double) = top * a + below * (1.0 - a)
    var r = channel(imageRgb, 16)
    var g = channel(imageRgb, 8)
    var b = channel(imageRgb, 0)
    r = over(channel(scrim, 16), scrimAlpha.toDouble(), r)
    g = over(channel(scrim, 8), scrimAlpha.toDouble(), g)
    b = over(channel(scrim, 0), scrimAlpha.toDouble(), b)
    r = over(channel(tint, 16), tintAlpha.toDouble(), r)
    g = over(channel(tint, 8), tintAlpha.toDouble(), g)
    b = over(channel(tint, 0), tintAlpha.toDouble(), b)
    return ratio(luminanceOf(r, g, b), relativeLuminance(textArgb))
}
