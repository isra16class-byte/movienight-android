package com.isra16.movienight.net

/** Largo máximo (en unidades de texto de Kotlin) de la cita de una respuesta (Fase 8, paso 5o). */
const val REPLY_QUOTE_MAX_CHARS = 100

/** Cuánto sigue la burbuja al dedo (resistencia) al deslizar para responder: 0,8 del recorrido real. */
const val REPLY_SWIPE_RESISTANCE = 0.8f

/** Tope del desplazamiento de la burbuja, en dp. */
const val REPLY_SWIPE_MAX_DP = 72f

/** Desplazamiento (ya con resistencia) a partir del cual, al soltar, se responde, en dp. */
const val REPLY_SWIPE_THRESHOLD_DP = 56f

/** Los avisos del sistema y los mensajes vacíos o en blanco no se pueden responder. */
fun canReplyTo(message: ChatMessage): Boolean = !message.system && message.text.isNotBlank()

/**
 * La cita que viaja con la respuesta (`replyTo` de `chat-message`), o `null` si [message] no se puede responder. `user` e `isHost` van tal cual;
 * el texto se limpia (saltos de línea y espacios repetidos pasan a un solo espacio, con `trim`) y se recorta a [REPLY_QUOTE_MAX_CHARS]: si
 * pasa de ese largo quedan 99 caracteres y "…". El recorte no parte un par sustituto (un emoji): si el corte cae entre sus dos mitades, se
 * descarta la mitad suelta.
 */
fun replyFor(message: ChatMessage): ReplyTo? {
    if (!canReplyTo(message)) return null
    return ReplyTo(user = message.user, text = quoteText(message.text), isHost = message.isHost)
}

internal fun quoteText(raw: String): String {
    val collapsed = StringBuilder(raw.length)
    var pendingSpace = false
    for (ch in raw) {
        if (ch.isWhitespace()) {
            pendingSpace = collapsed.isNotEmpty()
        } else {
            if (pendingSpace) collapsed.append(' ')
            pendingSpace = false
            collapsed.append(ch)
        }
    }
    val text = collapsed.toString()
    if (text.length <= REPLY_QUOTE_MAX_CHARS) return text
    var end = REPLY_QUOTE_MAX_CHARS - 1
    if (Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end -= 1
    return text.substring(0, end) + "…"
}

/**
 * Cuánto se desplaza la burbuja para un arrastre horizontal acumulado de [rawDrag] (px; positivo = a la derecha). Solo hacia la derecha: lo
 * negativo da 0. Con resistencia ([REPLY_SWIPE_RESISTANCE]) y con tope en [maxOffset].
 */
fun replySwipeOffset(rawDrag: Float, maxOffset: Float): Float {
    if (rawDrag <= 0f || maxOffset <= 0f) return 0f
    return (rawDrag * REPLY_SWIPE_RESISTANCE).coerceAtMost(maxOffset)
}

/** `true` si al soltar con [offset] se responde (llegó al [threshold]). */
fun replySwipeReached(offset: Float, threshold: Float): Boolean = threshold > 0f && offset >= threshold

/** Opacidad del icono de responder (0 a 1) según el avance hacia el [threshold]. */
fun replyIconAlpha(offset: Float, threshold: Float): Float {
    if (threshold <= 0f) return 0f
    return (offset / threshold).coerceIn(0f, 1f)
}
