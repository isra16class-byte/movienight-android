package com.isra16.movienight.net

import kotlin.math.max
import kotlin.math.min

/*
 * Alto del recuadro del video en la vista vertical. JVM puro, sin Android.
 * Sin teclado el video es 16:9 (como en la web). Con el teclado abierto en vertical se achica a un tope de 50 % del alto visible
 * (decisión propia de la Fase 8, paso 5j: la web usa 38 %, `38dvh` en `public/style.css`, pero aquí se prefirió un video más grande) y un piso
 * de 90 dp (`min-height: 90px` en la web), nunca más grande que el 16:9.
 */

/** Tope del alto del video con el teclado abierto, como fracción del alto visible. Decisión propia (la web usa 0,38; se subió a 0,50 en el paso 5j). */
const val VIDEO_KEYBOARD_MAX_FRACTION = 0.50f

/** Piso del alto del video con el teclado abierto, en dp (`min-height: 90px` en la web; el piso gana al tope). */
const val VIDEO_KEYBOARD_MIN_HEIGHT_DP = 90f

/**
 * Alto del video en dp para un recuadro de [widthDp] de ancho. Sin teclado es el 16:9 de siempre. Con el teclado
 * abierto es el 16:9 limitado a [VIDEO_KEYBOARD_MAX_FRACTION] de [availableHeightDp] (el alto que queda sobre el
 * teclado), nunca por debajo de [VIDEO_KEYBOARD_MIN_HEIGHT_DP] y nunca más grande que el 16:9. Si el alto disponible
 * no es un número usable se deja el 16:9.
 */
fun portraitVideoHeightDp(widthDp: Float, availableHeightDp: Float, keyboardOpen: Boolean): Float {
    if (!widthDp.isFinite() || widthDp <= 0f) return 0f
    val natural = widthDp * 9f / 16f
    if (!keyboardOpen || !availableHeightDp.isFinite() || availableHeightDp <= 0f) return natural
    return max(VIDEO_KEYBOARD_MIN_HEIGHT_DP, min(natural, availableHeightDp * VIDEO_KEYBOARD_MAX_FRACTION))
}

/** Cómo se ve la cabecera de la sala en la vista vertical: completa (teclado cerrado) o compacta (teclado abierto). */
enum class RoomHeaderMode { FULL, COMPACT }

/**
 * La cabecera de la sala es completa (Salir, nombre de la sala, línea de host y conectados) con el teclado cerrado y compacta (solo Salir y
 * conectados, en una fila baja) con el teclado abierto, para dejarle alto al video sin perder esos dos botones (Fase 8, paso 5i).
 * [imeBottomPx] es el alto del teclado en píxeles; 0 o menos es teclado cerrado (mismo criterio que `showHomeBottomBar`).
 */
fun roomHeaderModeFor(imeBottomPx: Int): RoomHeaderMode = if (imeBottomPx <= 0) RoomHeaderMode.FULL else RoomHeaderMode.COMPACT
