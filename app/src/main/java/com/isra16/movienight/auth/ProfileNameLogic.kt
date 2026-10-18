package com.isra16.movienight.auth

/** Largo máximo del nombre, en unidades UTF-16 (como cuenta el server al hacer `slice(0, 40)` en `join-room`). */
const val DISPLAY_NAME_MAX_LENGTH = 40

/** Mínimo de caracteres (puntos de código: un emoji cuenta como uno) para que un nombre sea válido. */
const val DISPLAY_NAME_MIN_LENGTH = 2

const val MSG_NAME_EMPTY = "Escribe un nombre."
const val MSG_NAME_TOO_SHORT = "Usa al menos 2 caracteres."

/**
 * Recorta a [DISPLAY_NAME_MAX_LENGTH] sin partir un par sustituto (un emoji). No toca los espacios: sirve mientras la persona
 * escribe, cuando un espacio al final es normal. Sin dependencias de Android, para poder testearla como JVM puro.
 */
fun limitDisplayNameInput(input: String): String {
    if (input.length <= DISPLAY_NAME_MAX_LENGTH) return input
    var end = DISPLAY_NAME_MAX_LENGTH
    if (Character.isHighSurrogate(input[end - 1]) && Character.isLowSurrogate(input[end])) end--
    return input.substring(0, end)
}

/**
 * Limpia el texto: los espacios, tabuladores y saltos de línea pasan a un solo espacio (así "a\nb" queda "a b", no "ab"); los
 * demás caracteres de control se quitan; sin espacios al principio ni al final; y se recorta a 40 sin partir un emoji. Puede
 * devolver una cadena vacía o de 1 carácter: [normalizeDisplayName] y [displayNameError] deciden si vale.
 */
private fun cleanDisplayName(input: String): String {
    val out = StringBuilder()
    var pendingSpace = false
    var i = 0
    while (i < input.length) {
        val cp = input.codePointAt(i)
        i += Character.charCount(cp)
        if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
            pendingSpace = true
            continue
        }
        if (Character.isISOControl(cp)) continue
        if (pendingSpace && out.isNotEmpty()) out.append(' ')
        pendingSpace = false
        out.appendCodePoint(cp)
    }
    return limitDisplayNameInput(out.toString()).trimEnd()
}

/** El nombre ya limpio, o null si queda con menos de [DISPLAY_NAME_MIN_LENGTH] caracteres. */
fun normalizeDisplayName(input: String): String? {
    val clean = cleanDisplayName(input)
    return if (clean.codePointCount(0, clean.length) >= DISPLAY_NAME_MIN_LENGTH) clean else null
}

/** Mensaje para mostrar bajo el campo, o null si el nombre es válido. El tope de 40 se aplica al escribir, sin mensaje. */
fun displayNameError(input: String): String? {
    val clean = cleanDisplayName(input)
    return when {
        clean.isEmpty() -> MSG_NAME_EMPTY
        clean.codePointCount(0, clean.length) < DISPLAY_NAME_MIN_LENGTH -> MSG_NAME_TOO_SHORT
        else -> null
    }
}

/** El nombre con el que se entra a una sala: el elegido si es válido y, si no, el del correo ([defaultUsername]). */
fun effectiveUsername(customName: String?, email: String): String =
    customName?.let(::normalizeDisplayName) ?: defaultUsername(email)
