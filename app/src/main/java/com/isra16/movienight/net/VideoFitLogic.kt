package com.isra16.movienight.net

import kotlin.math.max
import kotlin.math.min

/*
 * Cómo se acomoda el video dentro de la pantalla completa. JVM puro, sin Android.
 * Es una preferencia solo visual y local de cada persona: no se sincroniza ni toca el reproductor compartido.
 * La web no define ningún ajuste (usa el del navegador, que muestra el video entero); estos niveles son propios de la app.
 *
 * Los niveles no dependen del teléfono: se calculan con el tamaño real de la ventana y del video, así sirven igual en
 * una pantalla de 16:9, de 20:9, en una tablet o en un plegable.
 */
enum class VideoFit {
    /** El video entero, sin recortar (franjas negras si la proporción no coincide). Es el de siempre y el valor inicial. */
    FIT,

    /** A mitad de camino entre [FIT] y [FILL]: recorta menos que [FILL] pero deja menos franjas que [FIT] (útil en 4:3). */
    MEDIUM,

    /** El video cubre toda la ventana, sin deformarse, y se recorta lo que sobre. */
    FILL,

    /** Como [FILL] pero tratando el cuadro como si fuera de 2,39:1: quita las franjas de cine metidas en el propio archivo. */
    CINEMA,
    ;

    /** El siguiente nivel de la rueda (de menos a más zoom); después de [CINEMA] vuelve a [FIT]. */
    fun next(): VideoFit = entries[(ordinal + 1) % entries.size]

    /** [MEDIUM] y [CINEMA] no existen en `PlayerView`: se dibujan con un tamaño calculado por [videoScale]. */
    val usesCustomScale: Boolean get() = this == MEDIUM || this == CINEMA
}

/** Proporción del cine "scope" (CinemaScope): la de las películas con franjas negras grandes en una pantalla 16:9. */
const val CINEMA_ASPECT = 2.39f

/** Nombre corto del nivel, para mostrarlo junto al botón. */
fun videoFitName(fit: VideoFit): String = when (fit) {
    VideoFit.FIT -> "Entero"
    VideoFit.MEDIUM -> "Medio"
    VideoFit.FILL -> "Llenar"
    VideoFit.CINEMA -> "Cine"
}

/** Descripción para el lector de pantalla: el nivel actual y el que viene al tocar. */
fun videoFitButtonLabel(current: VideoFit): String =
    "Ajuste del video: ${videoFitName(current)}. Toca para pasar a ${videoFitName(current.next())}"

/**
 * Cuántos píxeles de pantalla ocupa cada píxel del video en el nivel [fit], dentro de una ventana de
 * [viewW] x [viewH] con un video de [videoW] x [videoH] (el ancho ya con la proporción de píxel aplicada).
 * Devuelve `null` si algún tamaño no es usable (por ejemplo, el video aún no informó su tamaño).
 *
 * - [VideoFit.FIT]: el mayor que deja el video entero dentro.
 * - [VideoFit.FILL]: el menor que cubre toda la ventana.
 * - [VideoFit.MEDIUM]: el promedio de los dos; nunca queda por debajo de FIT ni por encima de FILL.
 * - [VideoFit.CINEMA]: cubre la ventana con el cuadro recortado a 2,39:1 (centrado). Si el video ya es igual de
 *   ancho o más ancho que eso no hay franjas incrustadas que quitar y queda igual que FILL. Nunca es menor que FILL.
 */
fun videoScale(fit: VideoFit, viewW: Float, viewH: Float, videoW: Float, videoH: Float): Float? {
    val sizes = floatArrayOf(viewW, viewH, videoW, videoH)
    if (sizes.any { !it.isFinite() || it <= 0f }) return null
    val byWidth = viewW / videoW
    val byHeight = viewH / videoH
    val fitScale = min(byWidth, byHeight)
    val fillScale = max(byWidth, byHeight)
    return when (fit) {
        VideoFit.FIT -> fitScale
        VideoFit.MEDIUM -> (fitScale + fillScale) / 2f
        VideoFit.FILL -> fillScale
        VideoFit.CINEMA ->
            if (videoW / videoH >= CINEMA_ASPECT) fillScale
            // Cuadro útil: todo el ancho y videoW / 2,39 de alto.
            else max(fillScale, max(byWidth, viewH * CINEMA_ASPECT / videoW))
    }
}
