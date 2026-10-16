package com.isra16.movienight.net

import kotlin.math.max
import kotlin.math.min

/*
 * Alto del recuadro del video en la vista vertical. JVM puro, sin Android.
 * Copia el criterio de la web (`public/style.css`, rama plan-produccion): sin teclado el video es 16:9; con el teclado
 * abierto en vertical "puede achicarse" a un tope de 38 % del alto visible y un piso de 90 px, porque la prioridad es
 * que el video se siga viendo grande, no maximizar el chat (`html.keyboard-open:not(.device-landscape) .screen-wrap`).
 */

/** Tope del alto del video con el teclado abierto, como fracción del alto visible (`38dvh` en la web). */
const val VIDEO_KEYBOARD_MAX_FRACTION = 0.38f

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

/**
 * La cabecera de la sala (Salir, nombre, conectados) se ve solo con el teclado cerrado: con el teclado abierto se esconde para que el
 * video tenga ese alto. [imeBottomPx] es el alto del teclado en píxeles; 0 o menos es teclado cerrado (mismo criterio que `showHomeBottomBar`).
 */
fun showRoomHeader(imeBottomPx: Int): Boolean = imeBottomPx <= 0
