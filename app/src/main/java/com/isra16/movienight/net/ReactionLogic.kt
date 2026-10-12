package com.isra16.movienight.net

/*
 * Reacciones (emojis) flotantes sobre el video en pantalla completa (Fase 7, parte 2B). JVM puro, sin Android.
 * Copia el comportamiento de `public/room.html` de la web (rama plan-produccion: `socket.on('reaction')`,
 * `.float-emoji`, `floatUp`, el panel `.fs-emoji-panel`) salvo el tope de emojis a la vez ([REACTION_MAX_ACTIVE]),
 * que la web no tiene.
 *
 * El server retransmite `reaction` a TODA la sala, también a quien la mandó, y el payload es solo el emoji en
 * texto plano. Por eso quien reacciona ve su propio emoji cuando le vuelve del server: no se dibuja nada local.
 */

/**
 * Los emojis que se pueden mandar, en el mismo orden que los botones de `.reactions` en `room.html`. La web
 * tiene 14 (después de estos 8 siguen 🙌 💀 🎉 😅 😠 😴); por ahora la app ofrece solo estos 8 (decisión de la persona).
 */
val REACTION_EMOJIS: List<String> = listOf("😂", "😭", "😍", "😱", "🔥", "❤️", "👍", "👏")

/** Emojis flotando a la vez; los que llegan de más se descartan. Decisión nuestra: la web no tiene tope. */
const val REACTION_MAX_ACTIVE = 20

/** Lo que tarda un emoji en subir y desvanecerse (`floatUp 2.5s` en la web). */
const val REACTION_DURATION_MS = 2_500L

/** Cuánto sube un emoji (`translateY(-280px)` en la web). */
const val REACTION_RISE_DP = 280

/** Distancia del borde inferior donde nace (`bottom: 70px` en la web). */
const val REACTION_BOTTOM_DP = 70

/** Posición horizontal de nacimiento: de 0 a 80 % del ancho (`Math.random() * 80 + '%'` en la web). */
const val REACTION_LEFT_MAX_FRACTION = 0.8f

/**
 * Largo máximo (unidades UTF-16) de lo que se acepta como reacción. El server no valida nada y cualquier cliente
 * puede mandar texto largo; un emoji con modificadores (familia, bandera con tono) llega a ~11. Decisión nuestra.
 */
const val REACTION_MAX_CHARS = 16

/** Columnas del panel de emojis (`grid-template-columns: repeat(4, 1fr)` en la web). */
const val REACTION_PANEL_COLUMNS = 4

/** El emoji de un `reaction` entrante, o `null` si no es un texto usable (no es texto, está vacío o es demasiado largo). */
fun parseReactionEmoji(raw: Any?): String? {
    val text = (raw as? String)?.trim() ?: return null
    if (text.isEmpty() || text.length > REACTION_MAX_CHARS) return null
    return text
}

/** Solo se mandan los emojis de la lista [REACTION_EMOJIS]. */
fun isSendableReaction(emoji: String): Boolean = emoji in REACTION_EMOJIS

/** Un emoji ya decidido: dónde nace (fracción del ancho) y cuánto dura. */
data class ReactionSpec(val emoji: String, val leftFraction: Float, val durationMs: Long)

/** Posición horizontal a partir de un número al azar [random] (0 a 1): el azar entra por parámetro para poder probarlo. */
fun reactionLeftFraction(random: Float): Float = random.coerceIn(0f, 1f) * REACTION_LEFT_MAX_FRACTION

/** Decide cómo se muestra una reacción; `null` si se descarta porque ya hay [REACTION_MAX_ACTIVE] en pantalla. */
fun planReaction(emoji: String, activeCount: Int, random: Float): ReactionSpec? {
    if (activeCount >= REACTION_MAX_ACTIVE) return null
    return ReactionSpec(emoji, reactionLeftFraction(random), REACTION_DURATION_MS)
}
