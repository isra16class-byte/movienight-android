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
// "Desfase" = posición del host menos posición local: positivo = la app va atrasada.
//
//  Heartbeat con el host reproduciendo:
//    |desfase| <= 0.5 s  -> velocidad 1.0 (dentro de lo tolerable: no se toca nada)
//    0.5 < |desfase| <= 1 s -> sin saltar; velocidad 1.06 si va atrasada / 0.94 si va adelantada
//    |desfase| > 1 s     -> salto (seek) a la posición del host, velocidad 1.0
//  Cambio respecto a la 3B inicial (y a room.html, que salta recién a los 4 s): en la prueba en el
//  emulador un desfase de 2-3 s tras un seek del host o al entrar con el video en marcha tardaba
//  30-40 s en cerrarse solo con 6 % de velocidad. Saltar es barato cuando la app va atrasada (lo que
//  falta ya está en el buffer); lo caro es saltar en cadena, y eso lo evitan las dos reglas siguientes.
//  - Si el reproductor no está listo (cargando, recargando tras un seek) el desfase que se mide no es
//    real: se espera al próximo heartbeat en vez de saltar encima de un buffering.
//  - Después de un salto por heartbeat hay una pausa de 8 s sin nuevos saltos (solo velocidad), para
//    que una conexión lenta no entre en un ciclo "salto -> recarga -> sigo atrasada -> salto". Un
//    desfase de más de 10 s se corrige igual, pausa o no.
//  - Corrección al terminar de cargar (planReadyResync): tras un salto o al entrar, el video tarda en
//    cargar y el host sigue avanzando, así que la app queda atrasada justo lo que duró la carga (1-2 s
//    en el emulador). Al volver a estar lista se estima dónde está el host AHORA (su última posición
//    conocida más el tiempo transcurrido, si estaba reproduciendo) y se salta ahí, una sola vez por
//    salto: lo que acaba de cargarse cubre ese tramo, así que el segundo salto es casi instantáneo.
//  Heartbeat con el host en pausa: no hay nada que "alcanzar" con la velocidad, así que si
//    |desfase| > 0.5 s se salta (con el video quieto el salto no se nota).
//  play / pause / seek (acciones puntuales del host): se salta solo si |desfase| > 1 s (misma
//    regla que la web), y la velocidad vuelve a 1.0.

/** Desfase a partir del cual, con el host reproduciendo, se salta en vez de ajustar la velocidad. */
const val HARD_SEEK_THRESHOLD_MS = 1_000L

/** Tras un salto por heartbeat, tiempo durante el cual no se vuelve a saltar por heartbeat (salvo desfase enorme). */
const val HARD_SEEK_COOLDOWN_MS = 8_000L

/** Desfase a partir del cual se salta aunque haya una pausa de saltos en curso. */
const val HARD_SEEK_COOLDOWN_OVERRIDE_MS = 10_000L

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

/**
 * Decide cómo aplicar [msg] sabiendo que el reproductor local está en [localPositionMs].
 * [playerReady] = el reproductor está reproduciendo con normalidad (no cargando). [msSinceLastHardSeek]
 * = cuánto pasó desde el último salto por heartbeat (`Long.MAX_VALUE` si nunca hubo).
 */
fun planSync(
    msg: SyncMessage,
    localPositionMs: Long,
    playerReady: Boolean = true,
    msSinceLastHardSeek: Long = Long.MAX_VALUE,
): SyncPlan {
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
    // Reproduciendo (o sin decir): con el reproductor cargando el desfase no es real, no se corrige.
    if (!playerReady) return SyncPlan(null, 1f, play)

    val cooldownActive = msSinceLastHardSeek < HARD_SEEK_COOLDOWN_MS
    val mayHardSeek = !cooldownActive || absDrift > HARD_SEEK_COOLDOWN_OVERRIDE_MS
    return when {
        absDrift > HARD_SEEK_THRESHOLD_MS && mayHardSeek -> SyncPlan(msg.timeMs, 1f, play)
        absDrift > SOFT_DRIFT_THRESHOLD_MS ->
            SyncPlan(null, if (drift > 0) SPEED_CATCH_UP else SPEED_SLOW_DOWN, play)
        else -> SyncPlan(null, 1f, play)
    }
}

// --- Dónde está el host ahora ------------------------------------------------------------------

/**
 * Lo último que se sabe del host: estaba en [timeMs] cuando se recibió ([atMs], reloj monotónico del
 * dispositivo, en ms) y [playing] = reproduciendo / en pausa / `null` si todavía no se sabe.
 */
data class HostReference(val timeMs: Long, val atMs: Long, val playing: Boolean?)

/** Actualiza la referencia con un `sync`; si el mensaje no dice si el host reproduce (seek), conserva lo anterior. */
fun updateHostReference(prev: HostReference?, msg: SyncMessage, nowMs: Long): HostReference =
    HostReference(msg.timeMs, nowMs, msg.hostPaused?.not() ?: prev?.playing)

/** Referencia inicial a partir de `room-data.position`. */
fun hostReferenceFrom(position: RoomPosition, nowMs: Long): HostReference =
    HostReference(position.timeMs, nowMs, !position.paused)

/** Dónde estima que está el host en [nowMs]: avanza con el reloj solo si estaba reproduciendo. */
fun estimateHostPosition(ref: HostReference, nowMs: Long): Long =
    if (ref.playing == true) ref.timeMs + (nowMs - ref.atMs).coerceAtLeast(0L) else ref.timeMs

/** Diferencia a partir de la cual la corrección al terminar de cargar salta. */
const val READY_RESYNC_THRESHOLD_MS = 500L

/**
 * Corrección de una sola vez cuando el reproductor vuelve a estar listo tras un salto o la carga
 * inicial: a dónde saltar ([localPositionMs] ya es la posición actual), o `null` si no hace falta o
 * no se sabe si el host reproduce.
 */
fun planReadyResync(ref: HostReference?, localPositionMs: Long, nowMs: Long): Long? {
    if (ref == null || ref.playing == null) return null
    val target = estimateHostPosition(ref, nowMs)
    return if (abs(target - localPositionMs) > READY_RESYNC_THRESHOLD_MS) target else null
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
