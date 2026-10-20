package com.isra16.movienight.net

/*
 * Lógica pura de la escena de la intro a pantalla completa (Fase 8, paso 6, sesión B). JVM puro, sin Android.
 *
 * Completa a IntroAnimationLogic.kt (que NO cambia: avances, desplazamientos de las nubes del logo y su escalonado).
 * La pantalla se llena de nubes con las formas del logo; se abren hacia los lados y dejan ver el logo, que es el de
 * branding/logo.svg sin el cuadro redondeado (el verde es toda la pantalla). Aquí van las nubes EXTRA (las que salen
 * de la pantalla), la geometría de las nubes del logo para colocarlas y las cuentas de la distribución.
 *
 * Unidades: «unidades del logo» = el lienzo de 108 x 108 de logo.svg; la pantalla las pinta con `unit = logoSize / 108`.
 */

/** Lado del lienzo del logo, en unidades. */
const val INTRO_LOGO_UNITS = 108f

/** El logo está centrado en horizontal: su centro en x. */
const val INTRO_CENTER_X_UNITS = INTRO_LOGO_UNITS / 2f

/** Geometría (de logo.svg) de las nubes del logo: centro y mitad del ancho de cada sprite (recorte verde incluido). */
const val INTRO_CLOUD_LEFT_CENTER_X = 9.7f
const val INTRO_CLOUD_LEFT_CENTER_Y = 49.6f
const val INTRO_CLOUD_LEFT_HALF_WIDTH = 16.8f
const val INTRO_CLOUD_RIGHT_CENTER_X = 87.4f
const val INTRO_CLOUD_RIGHT_CENTER_Y = 34.2f
const val INTRO_CLOUD_RIGHT_HALF_WIDTH = 24.4f

/** Cuánto más allá del borde de la pantalla viajan las nubes extra, en unidades (cubre su mitad de ancho escalada). */
const val INTRO_EXTRA_EXIT_MARGIN_UNITS = 90f

/** Al final la escena se acerca un poco a quien mira (escala final del conjunto). */
const val INTRO_EXIT_ZOOM = 1.06f

/** Dónde empieza el nombre, en unidades del logo (bajo el reflejo, que termina en 91,4). */
const val INTRO_NAME_TOP_UNITS = 97f

/** El nombre no crece más allá de este tamaño de letra del sistema (para que quepa en una línea). */
const val INTRO_NAME_MAX_FONT_SCALE = 1.3f

/** El logo ocupa como mucho esta fracción de la altura (horizontal y pantallas bajas). */
const val INTRO_LOGO_MAX_HEIGHT_FRACTION = 0.5f

/** Alturas (dp) que se reservan bajo el logo: nombre (a escala 1), hueco y 2 líneas del eslogan (a escala 1). */
const val INTRO_NAME_RESERVE_DP = 44f
const val INTRO_NAME_TAGLINE_GAP_DP = 8f
const val INTRO_TAGLINE_RESERVE_DP = 48f

/**
 * Una nube extra: [onLeft] elige la forma (nube izquierda o derecha del logo) y el lado por donde sale; [yFraction] es
 * su altura como fracción de la altura de la pantalla; [scale] su tamaño; [startDx] cuánto se aparta, en x, de su
 * sitio en el logo al inicio (las lleva al centro de la pantalla).
 */
data class IntroExtraCloud(
    val onLeft: Boolean,
    val yFraction: Float,
    val scale: Float,
    val startDx: Float,
) {
    /** −1 hacia la izquierda, +1 hacia la derecha. */
    val direction: Float get() = if (onLeft) -1f else 1f
    val slotCenterX: Float get() = if (onLeft) INTRO_CLOUD_LEFT_CENTER_X else INTRO_CLOUD_RIGHT_CENTER_X
    val slotCenterY: Float get() = if (onLeft) INTRO_CLOUD_LEFT_CENTER_Y else INTRO_CLOUD_RIGHT_CENTER_Y
    val halfWidth: Float get() = (if (onLeft) INTRO_CLOUD_LEFT_HALF_WIDTH else INTRO_CLOUD_RIGHT_HALF_WIDTH) * scale
}

/** Las 10 nubes extra: alternan lado y bajan por la pantalla; las de la izquierda salen por la izquierda. */
val INTRO_EXTRA_CLOUDS: List<IntroExtraCloud> = listOf(
    IntroExtraCloud(onLeft = true, yFraction = 0.06f, scale = 1.6f, startDx = 48f),
    IntroExtraCloud(onLeft = false, yFraction = 0.14f, scale = 1.2f, startDx = -28f),
    IntroExtraCloud(onLeft = true, yFraction = 0.24f, scale = 2.0f, startDx = 40f),
    IntroExtraCloud(onLeft = false, yFraction = 0.34f, scale = 1.8f, startDx = -38f),
    IntroExtraCloud(onLeft = true, yFraction = 0.44f, scale = 1.2f, startDx = 52f),
    IntroExtraCloud(onLeft = false, yFraction = 0.54f, scale = 1.4f, startDx = -26f),
    IntroExtraCloud(onLeft = true, yFraction = 0.63f, scale = 1.9f, startDx = 36f),
    IntroExtraCloud(onLeft = false, yFraction = 0.73f, scale = 1.5f, startDx = -40f),
    IntroExtraCloud(onLeft = true, yFraction = 0.83f, scale = 1.3f, startDx = 46f),
    IntroExtraCloud(onLeft = false, yFraction = 0.93f, scale = 2.1f, startDx = -30f),
)

/** Cuánto recorren las nubes extra para salir del todo: media pantalla (en unidades) más el margen. */
fun introExtraTravelUnits(visibleWidthUnits: Float): Float = visibleWidthUnits / 2f + INTRO_EXTRA_EXIT_MARGIN_UNITS

/** Desplazamiento en x de una nube extra respecto a su sitio en el logo, según el avance de las nubes (0 a 1). */
fun introExtraCloudDx(cloud: IntroExtraCloud, cloudsAdvance: Float, travelUnits: Float): Float {
    val a = if (cloudsAdvance.isNaN()) 0f else cloudsAdvance.coerceIn(0f, 1f)
    return cloud.startDx + cloud.direction * travelUnits * a
}

/**
 * Desplazamiento en y (unidades) que lleva el centro de la nube a `yFraction` de la altura de la pantalla, sabiendo
 * dónde empieza el logo ([originYPx]) y cuántos píxeles mide una unidad ([unitPx]).
 */
fun introExtraCloudDy(cloud: IntroExtraCloud, screenHeightPx: Float, originYPx: Float, unitPx: Float): Float =
    (cloud.yFraction * screenHeightPx - originYPx) / unitPx - cloud.slotCenterY

/** Lado del logo (dp): todo el ancho, pero sin pasar de la mitad de la altura. */
fun introLogoSize(maxWidth: Float, maxHeight: Float): Float =
    minOf(maxWidth, maxHeight * INTRO_LOGO_MAX_HEIGHT_FRACTION).coerceAtLeast(0f)

/** Altura (dp) que se reserva bajo el nombre y el eslogan; crece con el tamaño de letra del sistema. */
fun introTextReserve(fontScale: Float): Float {
    val fs = if (fontScale.isNaN()) 1f else fontScale.coerceAtLeast(1f)
    val nameScale = minOf(fs, INTRO_NAME_MAX_FONT_SCALE)
    return INTRO_NAME_RESERVE_DP * nameScale + INTRO_NAME_TAGLINE_GAP_DP + INTRO_TAGLINE_RESERVE_DP * fs
}

/** Arriba del logo (dp): el conjunto (logo hasta el nombre + texto) centrado en la altura; nunca negativo. */
fun introLogoTop(maxHeight: Float, logoSize: Float, textReserve: Float): Float {
    val content = logoSize * (INTRO_NAME_TOP_UNITS / INTRO_LOGO_UNITS) + textReserve
    return ((maxHeight - content) / 2f).coerceAtLeast(0f)
}
