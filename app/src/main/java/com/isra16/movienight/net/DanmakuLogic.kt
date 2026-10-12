package com.isra16.movienight.net

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/*
 * Comentarios flotantes del chat sobre el video en pantalla completa (Fase 7, parte 2A). JVM puro, sin Android.
 * Copia el comportamiento de `public/room.html` de la web (rama plan-produccion: `spawnDanmaku`, `pickDanmakuLane`,
 * `usernameColor`) salvo el tope de comentarios a la vez ([DANMAKU_MAX_ACTIVE]), que la web no tiene.
 */

/** Carriles a distintas alturas; `room.html` también usa 6 (`DANMAKU_LANES`). */
const val DANMAKU_LANES = 6

/** Tras usar un carril no se reutiliza hasta pasado este tiempo, salvo que todos estén ocupados (como la web). */
const val DANMAKU_LANE_RESERVE_MS = 1_300L

/** Tope de comentarios en pantalla a la vez; los que llegan de más se descartan. Decisión nuestra: la web no tiene tope. */
const val DANMAKU_MAX_ACTIVE = 12

private const val DANMAKU_MIN_SECONDS = 7.0
private const val DANMAKU_MAX_SECONDS = 15.0

/** Separación entre carriles: 7,5 % de la altura (`10 + lane * 7.5` en la web). */
const val DANMAKU_TOP_STEP = 0.075f

/** Primer carril a 10 % de la altura y [step] más por cada carril siguiente (por defecto, 7,5 % como la web). */
fun danmakuTopFraction(lane: Int, step: Float = DANMAKU_TOP_STEP): Float = 0.10f + lane * step

/**
 * Cómo se reparten y dibujan los comentarios según dónde estén: [laneCount] carriles separados [topStep] (fracción de
 * la altura), hasta [maxActive] a la vez y letra de [fontSp] (`null`: la decide la UI según el ancho de pantalla,
 * 22 o 16 como la web). [FULLSCREEN] es la pantalla completa (2A, sin cambios). [PIP] es la ventana flotante, muy
 * chica: letra de 11 sp, 3 carriles (a 10, 25 y 40 % del alto, para que no se encimen) y 4 a la vez. Los valores de
 * [PIP] son estimaciones, a ajustar probando en el teléfono.
 */
data class DanmakuProfile(val laneCount: Int, val topStep: Float, val maxActive: Int, val fontSp: Float?) {
    fun newLanes() = DanmakuLanes(laneCount, topStep, maxActive)

    companion object {
        val FULLSCREEN = DanmakuProfile(DANMAKU_LANES, DANMAKU_TOP_STEP, DANMAKU_MAX_ACTIVE, null)
        val PIP = DanmakuProfile(laneCount = 3, topStep = 0.15f, maxActive = 4, fontSp = 11f)
    }
}

/**
 * Lo que tarda un comentario en cruzar la pantalla: `min(15, max(7, largo / 10 + 6))` segundos. Los mensajes largos
 * van más lento para poder leerse. [textLength] cuenta unidades UTF-16, igual que `text.length` en JavaScript.
 */
fun danmakuDurationMs(textLength: Int): Long {
    val seconds = min(DANMAKU_MAX_SECONDS, max(DANMAKU_MIN_SECONDS, textLength / 10.0 + 6.0))
    return (seconds * 1000).roundToLong()
}

/** Solo lo que escribe la gente: los avisos del sistema (entró, salió, cambio de host o de video) se quedan afuera. */
fun shouldShowDanmaku(message: ChatMessage): Boolean = !message.system && message.text.isNotBlank()

/** Los saltos de línea se convierten en espacios: el comentario va en una sola línea. */
fun danmakuText(text: String): String = text.replace('\r', ' ').replace('\n', ' ')

private val USERNAME_COLORS: List<Int> = listOf(
    0xFFFF2E9A, 0xFFB18AFF, 0xFFFF7A45, 0xFF4DFF9E, 0xFFFFE066, 0xFF5EC8FF, 0xFFFF5C72,
).map { it.toInt() }

/** Color del host (`HOST_NAME_COLOR` en la web), ARGB. */
val DANMAKU_HOST_COLOR: Int = 0xFF00E5FF.toInt()

/**
 * Color del nombre (ARGB): el host siempre cian; los demás uno de 7 según un hash del nombre, el mismo hash que la
 * web (`hash * 31 + código`, con desborde de 32 bits), para que la misma persona tenga el mismo color en la app y en
 * el navegador. El valor absoluto se toma en `Long` porque en JavaScript `Math.abs` no desborda con `-2^31`.
 */
fun danmakuNameColor(name: String, isHost: Boolean): Int {
    if (isHost) return DANMAKU_HOST_COLOR
    var hash = 0
    for (ch in name) hash = hash * 31 + ch.code
    return USERNAME_COLORS[(abs(hash.toLong()) % USERNAME_COLORS.size).toInt()]
}

/** Un comentario ya decidido: en qué carril va, cuánto dura y con qué color. */
data class DanmakuSpec(
    val lane: Int,
    val topFraction: Float,
    val durationMs: Long,
    val user: String,
    val text: String,
    val nameColor: Int,
)

/**
 * Reparte los carriles. [now] es cualquier reloj en milisegundos que no retroceda (en la app, `elapsedRealtime`).
 * Se crea uno por cada entrada a pantalla completa: al salir se descarta y los carriles vuelven a empezar, como
 * `clearDanmaku` en la web.
 */
class DanmakuLanes(
    private val laneCount: Int = DANMAKU_LANES,
    private val topStep: Float = DANMAKU_TOP_STEP,
    private val maxActive: Int = DANMAKU_MAX_ACTIVE,
) {
    private val freeAt = LongArray(laneCount)

    /**
     * Elige el primer carril libre; si todos están ocupados, el que se libera antes (los comentarios pueden
     * encimarse, igual que en la web). Reserva el carril elegido [DANMAKU_LANE_RESERVE_MS].
     */
    fun pick(now: Long): Int {
        val free = freeAt.indexOfFirst { it <= now }
        val lane = if (free >= 0) free else freeAt.indices.minByOrNull { freeAt[it] } ?: 0
        freeAt[lane] = now + DANMAKU_LANE_RESERVE_MS
        return lane
    }

    fun clear() {
        freeAt.fill(0L)
    }

    /**
     * Decide si un mensaje en vivo se muestra y cómo; `null` si se descarta (mensaje de sistema o vacío, o ya hay
     * [maxActive] en pantalla). Un mensaje descartado no gasta carril.
     */
    fun plan(message: ChatMessage, activeCount: Int, now: Long): DanmakuSpec? {
        if (!shouldShowDanmaku(message)) return null
        if (activeCount >= maxActive) return null
        val text = danmakuText(message.text)
        val lane = pick(now)
        return DanmakuSpec(
            lane = lane,
            topFraction = danmakuTopFraction(lane, topStep),
            durationMs = danmakuDurationMs(text.length),
            user = message.user,
            text = text,
            nameColor = danmakuNameColor(message.user, message.isHost),
        )
    }
}
