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

/** Los 14 emojis de los botones de `.reactions` en `room.html`, en el mismo orden. La barra de la vista vertical los ofrece todos. */
val REACTION_BAR_EMOJIS: List<String> = listOf("😂", "😭", "😍", "😱", "🔥", "❤️", "👍", "👏", "🙌", "💀", "🎉", "😅", "😠", "😴")

/** Cuántos emojis ofrece el panel de la pantalla completa: los primeros de [REACTION_BAR_EMOJIS] (decisión de la persona: 8 ahí, 14 en la barra). */
const val REACTION_PANEL_SIZE = 8

/** Los emojis del panel de la pantalla completa (rejilla de [REACTION_PANEL_COLUMNS] columnas). */
val REACTION_PANEL_EMOJIS: List<String> = REACTION_BAR_EMOJIS.take(REACTION_PANEL_SIZE)

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

/** Solo se mandan los emojis de [REACTION_BAR_EMOJIS] (el panel de la pantalla completa ofrece una parte). */
fun isSendableReaction(emoji: String): Boolean = emoji in REACTION_BAR_EMOJIS

/** Un emoji ya decidido: dónde nace (fracción del ancho) y cuánto dura. */
data class ReactionSpec(val emoji: String, val leftFraction: Float, val durationMs: Long)

/** Posición horizontal a partir de un número al azar [random] (0 a 1): el azar entra por parámetro para poder probarlo. */
fun reactionLeftFraction(random: Float): Float = random.coerceIn(0f, 1f) * REACTION_LEFT_MAX_FRACTION

/** Decide cómo se muestra una reacción; `null` si se descarta porque ya hay [maxActive] en pantalla. */
fun planReaction(emoji: String, activeCount: Int, random: Float, maxActive: Int = REACTION_MAX_ACTIVE): ReactionSpec? {
    if (activeCount >= maxActive) return null
    return ReactionSpec(emoji, reactionLeftFraction(random), REACTION_DURATION_MS)
}

/** Dónde se dibujan los emojis flotantes. */
enum class ReactionSurface {
    /** La pantalla completa horizontal (2B): los números de la web, sin cambios. */
    FULLSCREEN,

    /** La caja del video en la vista vertical (como `.screen-wrap` en la web móvil). */
    PORTRAIT,

    /** La ventana flotante (PiP): muy chica, sin referencia en la web. */
    PIP,
}

/** Tamaño del emoji (sp) y cuántos puede haber a la vez en cada superficie. */
data class ReactionLook(val fontSp: Float, val maxActive: Int)

/** En la ventana flotante: emoji de 18 sp y hasta 8 a la vez. Valores estimados, a ajustar probando en el teléfono. */
const val REACTION_PIP_FONT_SP = 18f
const val REACTION_PIP_MAX_ACTIVE = 8

fun reactionLookFor(surface: ReactionSurface): ReactionLook = when (surface) {
    ReactionSurface.FULLSCREEN -> ReactionLook(30f, REACTION_MAX_ACTIVE)
    ReactionSurface.PORTRAIT -> ReactionLook(30f, REACTION_MAX_ACTIVE)
    ReactionSurface.PIP -> ReactionLook(REACTION_PIP_FONT_SP, REACTION_PIP_MAX_ACTIVE)
}

/** Dónde nace un emoji (distancia al borde de abajo) y cuánto sube, en dp. */
data class ReactionTravel(val bottomDp: Float, val riseDp: Float)

/**
 * El recorrido de un emoji. En pantalla completa son los de la web (70 dp de margen, 280 dp de subida). En una caja
 * más baja (video vertical, ventana flotante) esos números se saldrían por arriba, así que se adaptan al alto
 * [heightDp] de la caja: nace al 15 % del alto (entre 4 y 70 dp) y sube hasta dejar el emoji entero dentro, sin
 * pasar de 280 dp. [fontSp] es el tamaño del emoji (alto aproximado: 1,25 veces el tamaño en sp).
 */
fun reactionTravel(surface: ReactionSurface, heightDp: Float, fontSp: Float): ReactionTravel {
    if (surface == ReactionSurface.FULLSCREEN) return ReactionTravel(REACTION_BOTTOM_DP.toFloat(), REACTION_RISE_DP.toFloat())
    val bottom = (heightDp * 0.15f).coerceIn(4f, REACTION_BOTTOM_DP.toFloat())
    val rise = (heightDp - bottom - fontSp * 1.25f).coerceIn(0f, REACTION_RISE_DP.toFloat())
    return ReactionTravel(bottom, rise)
}
