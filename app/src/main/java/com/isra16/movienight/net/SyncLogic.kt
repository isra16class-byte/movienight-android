package com.isra16.movienight.net

import org.json.JSONObject
import kotlin.math.abs

/*
 * Lógica pura (sin Android ni Media3) de la sincronización con el host — Fase 3B.
 *
 * Forma de los datos, verificada en `server.js` (rama plan-produccion):
 *  - `sync` { type: 'play'|'pause'|'seek'|'heartbeat', time: number (segundos), paused?: boolean }.
 *    El server lo retransmite TAL CUAL a todos menos al host (`socket.to(room).emit('sync', data)`):
 *    no valida `type` ni `time`, así que acá se parsea con desconfianza. `paused` solo viene en el
 *    `heartbeat` (cada 4 s, del host).
 *  - `room-data.position` { time: number (segundos), paused: boolean }, al hacer `join-room`.
 *  - `host-status` { isHost, hostToken }: ya lo interpreta `parseServerEvent`.
 */

/** Tipos de `sync` que manda el host (los mismos que `room.html`). */
enum class SyncType { PLAY, PAUSE, SEEK, HEARTBEAT }

/**
 * Un `sync` ya interpretado. [timeMs] = posición del host en milisegundos (el server la manda en
 * segundos). [paused] solo viene en el [SyncType.HEARTBEAT]; `null` = el host no lo dijo.
 */
data class SyncMessage(val type: SyncType, val timeMs: Long, val paused: Boolean?) {
    /**
     * ¿El host está en pausa según este mensaje? `play`/`pause` lo dicen por su tipo, el `heartbeat`
     * por [paused]; `seek` no dice nada (solo cambia el minuto) y un `heartbeat` sin `paused` tampoco.
     */
    val hostPaused: Boolean?
        get() = when (type) {
            SyncType.PLAY -> false
            SyncType.PAUSE -> true
            SyncType.SEEK -> null
            SyncType.HEARTBEAT -> paused
        }
}

/** Estado del video en la sala al entrar (`room-data.position`). */
data class RoomPosition(val timeMs: Long, val paused: Boolean)

/** Tope de `time` aceptado: 100 h. Más que eso solo puede ser un valor roto (el server no lo valida). */
private const val MAX_SYNC_SECONDS = 360_000.0

private fun secondsToMs(value: Any?): Long? {
    val seconds = (value as? Number)?.toDouble() ?: return null
    if (!seconds.isFinite()) return null
    return Math.round(seconds.coerceIn(0.0, MAX_SYNC_SECONDS) * 1000.0)
}

/**
 * Interpreta el payload de un `sync`. `null` si el tipo es desconocido o `time` no es un número
 * (el server también exige `typeof time === 'number'` para guardar la posición). Un `time` negativo
 * se lleva a 0. Un `paused` que no sea booleano se ignora (queda `null`).
 */
fun parseSyncMessage(obj: JSONObject): SyncMessage? {
    val type = when (obj.opt("type")) {
        "play" -> SyncType.PLAY
        "pause" -> SyncType.PAUSE
        "seek" -> SyncType.SEEK
        "heartbeat" -> SyncType.HEARTBEAT
        else -> return null
    }
    val timeMs = secondsToMs(obj.opt("time")) ?: return null
    val paused = if (type == SyncType.HEARTBEAT) obj.opt("paused") as? Boolean else null
    return SyncMessage(type, timeMs, paused)
}

/**
 * Interpreta `room-data.position`. `null` si falta o `time` no es un número. Si `paused` no viene,
 * se asume en pausa (ante la duda, no arrancar el video solo).
 */
fun parseRoomPosition(obj: JSONObject?): RoomPosition? {
    obj ?: return null
    val timeMs = secondsToMs(obj.opt("time")) ?: return null
    return RoomPosition(timeMs, paused = obj.opt("paused") as? Boolean ?: true)
}

// --- Corrección de desfase -------------------------------------------------------------------
//
// Mismos umbrales que `room.html` (así la app y la web se comportan igual ante el mismo host).
// "Desfase" = posición del host menos posición local: positivo = la app va atrasada.
//
//  Heartbeat con el host reproduciendo:
//    |desfase| <= 0.5 s  -> velocidad 1.0 (dentro de lo tolerable: no se toca nada)
//    0.5 < |desfase| <= 4 s -> sin saltar; velocidad 1.06 si va atrasada / 0.94 si va adelantada
//                          (saltar corta el buffer y provoca un "se queda cargando")
//    |desfase| > 4 s     -> salto (seek) a la posición del host, velocidad 1.0
//  Heartbeat con el host en pausa: acá no hay nada que "alcanzar" con la velocidad, así que si
//    |desfase| > 0.5 s se salta (con el video quieto el salto no se nota). (La web solo ajusta la
//    velocidad en este caso; la diferencia es intencional.)
//  play / pause / seek (acciones puntuales del host): se salta solo si |desfase| > 1 s (misma
//    regla que la web), y la velocidad vuelve a 1.0.

/** Desfase a partir del cual, con el host reproduciendo, se salta en vez de ajustar la velocidad. */
const val HARD_SEEK_THRESHOLD_MS = 4_000L

/** Desfase tolerado: por debajo no se corrige; por encima se acelera/frena (o se salta si el host está en pausa). */
const val SOFT_DRIFT_THRESHOLD_MS = 500L

/** Desfase a partir del cual un `play`/`pause`/`seek` puntual del host hace saltar al video. */
const val EVENT_SEEK_THRESHOLD_MS = 1_000L

const val SPEED_CATCH_UP = 1.06f
const val SPEED_SLOW_DOWN = 0.94f

/**
 * Qué hay que hacerle al reproductor para seguir al host: saltar a [seekToMs] (`null` = no saltar),
 * poner la velocidad en [speed], y [play] = `true` reproducir / `false` pausar / `null` no tocar.
 */
data class SyncPlan(val seekToMs: Long?, val speed: Float, val play: Boolean?)

/** Decide cómo aplicar [msg] sabiendo que el reproductor local está en [localPositionMs]. */
fun planSync(msg: SyncMessage, localPositionMs: Long): SyncPlan {
    val drift = msg.timeMs - localPositionMs
    val absDrift = abs(drift)
    val play = msg.hostPaused?.not()

    if (msg.type != SyncType.HEARTBEAT) {
        return SyncPlan(
            seekToMs = if (absDrift > EVENT_SEEK_THRESHOLD_MS) msg.timeMs else null,
            speed = 1f,
            play = play,
        )
    }
    if (msg.paused == true) {
        return SyncPlan(
            seekToMs = if (absDrift > SOFT_DRIFT_THRESHOLD_MS) msg.timeMs else null,
            speed = 1f,
            play = false,
        )
    }
    return when {
        absDrift > HARD_SEEK_THRESHOLD_MS -> SyncPlan(msg.timeMs, 1f, play)
        absDrift > SOFT_DRIFT_THRESHOLD_MS ->
            SyncPlan(null, if (drift > 0) SPEED_CATCH_UP else SPEED_SLOW_DOWN, play)
        else -> SyncPlan(null, 1f, play)
    }
}

// --- Barra de progreso de solo lectura (invitados) ---------------------------------------------

/** `m:ss`, o `h:mm:ss` si pasa de la hora (igual que `formatTime` de `room.html`). Negativos valen 0. */
fun formatPlaybackTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L)) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val ss = s.toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$ss" else "$m:$ss"
}

/** Qué fracción (0..1) del video va por [positionMs]; 0 si no se conoce la duración. */
fun progressFraction(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0L) 0f else (positionMs.toDouble() / durationMs).toFloat().coerceIn(0f, 1f)
