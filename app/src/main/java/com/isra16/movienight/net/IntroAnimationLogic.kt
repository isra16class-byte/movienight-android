package com.isra16.movienight.net

/*
 * Lógica pura de la intro con el logo, «el despeje» (Fase 8, paso 6, sesión A). JVM puro, sin Android.
 *
 * Las nubes del logo (branding/logo.svg, lienzo de 108 x 108 unidades) arrancan juntas en el centro, sobre la luna y
 * el play, y se abren hacia afuera hasta su sitio. El logo final es EXACTAMENTE el del SVG: solo se desplazan las nubes
 * (la nube de cada lado se mueve junto con su recorte verde); ninguna forma ni color cambia.
 *
 * El progreso `p` va de 0 a 1 y lo manda la animación de la pantalla (sesión B). Aquí solo se calcula, para cada
 * elemento, cuánto lleva hecho (0 = aún no, 1 = terminó) y cuánto se desplazan las nubes. Los tiempos reales
 * (INTRO_ANIMATION_MS, INTRO_DURATION_MS) no se tocan: esto trabaja en fracciones del progreso.
 */

/** Desplazamiento inicial en x, en unidades del lienzo del logo, de las nubes de la IZQUIERDA (+: hacia la derecha). */
const val INTRO_CLOUD_LEFT_START_X = 46f

/** Desplazamiento inicial en x, en unidades del lienzo del logo, de las nubes de la DERECHA (−: hacia la izquierda). */
const val INTRO_CLOUD_RIGHT_START_X = -33f

/** El logo aparece con un fundido corto al inicio. */
const val INTRO_LOGO_FADE_START = 0.00f
const val INTRO_LOGO_FADE_END = 0.15f

/** Las nubes se abren durante casi todo el movimiento. */
const val INTRO_CLOUDS_START = 0.05f
const val INTRO_CLOUDS_END = 0.90f

/** El nombre sube un poco y aparece hacia la mitad. */
const val INTRO_NAME_START = 0.40f
const val INTRO_NAME_END = 0.70f

/** El eslogan aparece al final. */
const val INTRO_TAGLINE_START = 0.75f
const val INTRO_TAGLINE_END = 1.00f

/**
 * Avance de un elemento: 0 antes de [start], 1 después de [end] y lineal en medio.
 * Con `end <= start` no hay rango: 0 si `progress < start` y 1 si no. Un progreso que no es un número da 0.
 */
fun introStage(progress: Float, start: Float, end: Float): Float {
    if (progress.isNaN()) return 0f
    if (end <= start) return if (progress < start) 0f else 1f
    if (progress <= start) return 0f
    if (progress >= end) return 1f
    return (progress - start) / (end - start)
}

/** Avance (0 a 1) de cada elemento de la intro para un progreso dado. */
data class IntroAdvance(
    val logo: Float,
    val clouds: Float,
    val name: Float,
    val tagline: Float,
)

/** Los cuatro avances para el progreso [progress] (0 a 1; fuera de ese rango se recorta). */
fun introAdvanceFor(progress: Float): IntroAdvance = IntroAdvance(
    logo = introStage(progress, INTRO_LOGO_FADE_START, INTRO_LOGO_FADE_END),
    clouds = introStage(progress, INTRO_CLOUDS_START, INTRO_CLOUDS_END),
    name = introStage(progress, INTRO_NAME_START, INTRO_NAME_END),
    tagline = introStage(progress, INTRO_TAGLINE_START, INTRO_TAGLINE_END),
)

/** Desplazamiento en x de las nubes de cada lado, en unidades del lienzo del logo. */
data class CloudOffsets(val left: Float, val right: Float)

/**
 * `offset = inicial * (1 − avanceNubes)`: con avance 0 son los iniciales y con avance 1 son EXACTAMENTE 0 (el logo
 * del SVG). Un avance fuera de [0, 1] o que no es un número se recorta.
 */
fun introCloudOffsets(cloudsAdvance: Float): CloudOffsets {
    val remaining = 1f - cloudsAdvance.let { if (it.isNaN()) 0f else it.coerceIn(0f, 1f) }
    // Con remaining = 0 se devuelve un 0 positivo: −33 * 0 daría −0,0.
    if (remaining == 0f) return CloudOffsets(0f, 0f)
    return CloudOffsets(
        left = INTRO_CLOUD_LEFT_START_X * remaining,
        right = INTRO_CLOUD_RIGHT_START_X * remaining,
    )
}
