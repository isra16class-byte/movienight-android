package com.isra16.movienight.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import com.isra16.movienight.net.INTRO_EXTRA_CLOUDS
import com.isra16.movienight.net.INTRO_LOGO_UNITS
import com.isra16.movienight.net.introAdvanceFor
import com.isra16.movienight.net.introCloudOffsets
import com.isra16.movienight.net.introExtraCloudDx
import com.isra16.movienight.net.introExtraCloudDy
import com.isra16.movienight.net.introExtraTravelUnits

/** Los dos colores planos del logo (branding/logo.svg). Iguales en modo claro y oscuro. */
val MovieNightGreen = Color(0xFF75A38F)
val MovieNightCream = Color(0xFFF4FAED)

/*
 * Rutas de branding/logo.svg, copiadas tal cual y en el ORDEN de sus capas (lienzo de 108 x 108):
 * luna (2 piezas), horizonte, nubes de la izquierda (recorte verde y nube), nubes de la derecha (nube de arriba,
 * recorte verde y nube), play y los dos trazos del reflejo. El cuadro redondeado del SVG no se dibuja: aquí el verde
 * es toda la pantalla. Ninguna forma ni color cambia; las nubes solo se trasladan (y las extra, también se escalan).
 */
private const val MOON_LEFT = "M61.52,15.47A38,38 0 0,0 24.3,77L53.5,77A31.41,31.41 0 0,1 61.52,15.47z"
private const val MOON_RIGHT = "M90.54,60.79A38,38 0 0,1 82.56,77L71.26,77A31.41,31.41 0 0,0 90.54,60.79z"
private const val HORIZON = "M-2,75.65H110V78.4H-2z"
private const val CLOUD_LEFT_CUT = "M8.46,58.2A8,8 0 1,0 -6.42,58.2zM16.67,58.2A10.15,10.15 0 1,0 1.93,58.2zM25.42,58.2A7.41,7.41 0 1,0 12.68,58.2z"
private const val CLOUD_LEFT = "M6.02,55.2A5,5 0 0,0 -3.98,55.2zM15.24,55.2A7.15,7.15 0 1,0 3.36,55.2zM23.39,55.2A4.41,4.41 0 1,0 14.71,55.2z"
private const val CLOUD_RIGHT_TOP = "M75.09,33.9A4.57,4.57 0 1,0 67.81,33.9zM80.74,33.9A5.65,5.65 0 1,0 76.78,33.9zM68.97,33.9A2.95,2.95 0 1,0 63.07,33.9z"
private const val CLOUD_RIGHT_CUT = "M79.14,45.6A5.92,5.92 0 1,0 71.42,45.6zM86.89,45.6A7.62,7.62 0 1,0 78.03,45.6zM97.42,45.6A10.62,10.62 0 1,0 89.14,45.6zM108.77,45.6A8,8 0 1,0 98.73,45.6z"
private const val CLOUD_RIGHT = "M77.79,42.6A2.92,2.92 0 1,0 72.77,42.6zM85.79,42.6A4.62,4.62 0 1,0 79.13,42.6zM96.76,42.6A7.62,7.62 0 1,0 89.8,42.6zM107.57,42.6A5,5 0 1,0 99.93,42.6z"
private const val PLAY = "M47.11,48.9L47.13,67.55A1.81,1.81 0 0,1 49.86,69.11L65.79,59.68A1.81,1.81 0 0,1 65.78,56.55L49.83,47.33A1.81,1.81 0 0,1 47.11,48.9z"
private const val REFLECTION_1 = "M29.07,82.08H78.39L75.12,85.37H32.07z"
private const val REFLECTION_2 = "M39.81,88.11H66.83A13.51,3.29 0 0,1 39.81,88.11z"

private class LogoPaths {
    val moonLeft = parse(MOON_LEFT)
    val moonRight = parse(MOON_RIGHT)
    val horizon = parse(HORIZON)
    val leftCut = parse(CLOUD_LEFT_CUT)
    val leftCloud = parse(CLOUD_LEFT)
    val rightTop = parse(CLOUD_RIGHT_TOP)
    val rightCut = parse(CLOUD_RIGHT_CUT)
    val rightCloud = parse(CLOUD_RIGHT)
    val play = parse(PLAY)
    val reflection1 = parse(REFLECTION_1)
    val reflection2 = parse(REFLECTION_2)

    private fun parse(d: String): Path = PathParser().parsePathString(d).toPath()
}

/**
 * La escena del logo a pantalla completa (decorativa: no se lee aparte). El logo mide [logoSize] de lado y su borde
 * de arriba está a [logoTop] de la parte alta; el verde de fondo lo pone quien la usa. [progress] (0 a 1) se lee al
 * DIBUJAR, no al componer, para no recomponer en cada fotograma.
 *
 * Orden de dibujo: luna, horizonte, play y reflejo primero y las nubes al final, para que las nubes puedan tapar la
 * luna y el play al inicio. Con las nubes en su sitio el resultado es idéntico al del SVG (en el SVG el play y el
 * reflejo van después de las nubes, pero no se tocan con ellas).
 */
@Composable
fun MovieNightLogo(
    progress: () -> Float,
    logoSize: Dp,
    logoTop: Dp,
    modifier: Modifier = Modifier,
) {
    val paths = remember { LogoPaths() }
    Canvas(modifier) {
        val advance = introAdvanceFor(progress())
        val offsets = introCloudOffsets(advance.clouds)
        val logoPx = logoSize.toPx()
        val unit = logoPx / INTRO_LOGO_UNITS
        val originX = (size.width - logoPx) / 2f
        val originY = logoTop.toPx()
        withTransform({
            translate(originX, originY)
            scale(unit, unit, Offset.Zero)
        }) {
            drawBase(paths)
            if (advance.clouds < 1f) {
                val travel = introExtraTravelUnits(size.width / unit)
                for (cloud in INTRO_EXTRA_CLOUDS) {
                    val dx = introExtraCloudDx(cloud, advance.clouds, travel)
                    val dy = introExtraCloudDy(cloud, size.height, originY, unit)
                    val pivot = Offset(cloud.slotCenterX, cloud.slotCenterY)
                    withTransform({
                        translate(dx, dy)
                        scale(cloud.scale, cloud.scale, pivot)
                    }) {
                        if (cloud.onLeft) drawLeftCloud(paths) else drawRightCloud(paths)
                    }
                }
            }
            withTransform({ translate(offsets.left, 0f) }) { drawLeftCloud(paths) }
            withTransform({ translate(offsets.right, 0f) }) { drawRightCloud(paths) }
        }
    }
}

private fun DrawScope.drawBase(p: LogoPaths) {
    drawPath(p.moonLeft, MovieNightCream)
    drawPath(p.moonRight, MovieNightCream)
    drawPath(p.horizon, MovieNightCream)
    drawPath(p.play, MovieNightCream)
    drawPath(p.reflection1, MovieNightCream)
    drawPath(p.reflection2, MovieNightCream)
}

/** Nube de la izquierda: primero el recorte verde y después la nube (el recorte se mueve junto con ella). */
private fun DrawScope.drawLeftCloud(p: LogoPaths) {
    drawPath(p.leftCut, MovieNightGreen)
    drawPath(p.leftCloud, MovieNightCream)
}

/** Nubes de la derecha: la nube de arriba y, abajo, el recorte verde y la nube. */
private fun DrawScope.drawRightCloud(p: LogoPaths) {
    drawPath(p.rightTop, MovieNightCream)
    drawPath(p.rightCut, MovieNightGreen)
    drawPath(p.rightCloud, MovieNightCream)
}
