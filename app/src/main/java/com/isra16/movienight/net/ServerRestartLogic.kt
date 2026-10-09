package com.isra16.movienight.net

// Aviso de reinicio del server (`server-restarting`). Lógica pura, sin Android, para poder probarla.
//
// Verificado contra `server.js` (rama plan-produccion) y con un server real el 2026-10-09:
//  - Un SIGTERM/SIGINT (o el mensaje IPC de PM2) dispara `gracefulShutdown`, que hace `io.emit('server-restarting')`
//    a TODOS los sockets, SIN payload (llega sin argumentos), y corta los sockets `SHUTDOWN_GRACE_MS` después
//    (5 s por defecto). El cliente ve `disconnect` con razón `transport close`, o sea que reconecta solo.
//  - Mientras el server está caído, cada intento de reconexión falla con `connect_error`. Eso NO debe pisar el aviso.
//  - Al volver, `join-room` devuelve chat-history, host-status y room-data. La sala se recupera de Redis, y la
//    posición que guardó el server puede estar atrasada (ver [planRejoin]).

/** Cuánto se mantiene el aviso de reinicio si la conexión no vuelve (después se vuelve al aviso genérico). */
const val RESTART_GIVE_UP_MS = 90_000L

const val RESTART_ANNOUNCED_MESSAGE = "El servidor se va a reiniciar en unos segundos. La app reconecta sola."
const val RESTART_RECONNECTING_MESSAGE = "El servidor se está reiniciando. Reconectando en cuanto vuelva…"
const val RECONNECTING_MESSAGE = "Reconectando…"

/**
 * Qué se sabe del reinicio: [announced] = llegó `server-restarting` y todavía no nos volvimos a unir;
 * [disconnected] = además ya se cortó el socket desde entonces; [bannerGivenUp] = pasó tanto tiempo sin volver que
 * se dejó de mostrar el aviso (la vuelta, cuando llegue, sigue contando como la de un reinicio). Inmutable: cada
 * paso devuelve el estado nuevo.
 */
data class RestartState(
    val announced: Boolean = false,
    val disconnected: Boolean = false,
    val bannerGivenUp: Boolean = false,
) {

    /** Llegó `server-restarting`. Un segundo aviso reinicia el seguimiento. */
    fun onAnnounced(): RestartState = RestartState(announced = true)

    /** Se cortó el socket. Solo cuenta si había un reinicio anunciado: una caída cualquiera no es un reinicio. */
    fun onDisconnected(): RestartState = if (announced) copy(disconnected = true) else this

    /**
     * El server aceptó el `join-room` (llegó `room-data`). Devuelve el estado nuevo y si este ingreso es la vuelta
     * de un reinicio. Solo lo es si el socket llegó a cortarse: un `join-room` que ya venía en camino cuando se
     * anunció el reinicio no lo es, y el aviso sigue (el corte todavía no pasó).
     */
    fun onJoined(): Pair<RestartState, Boolean> =
        if (announced && disconnected) RestartState() to true else this to false

    /** Hay un reinicio anunciado y el socket ya se cortó: lo que llegue ahora es la vuelta de ese reinicio. */
    val isRejoining: Boolean get() = announced && disconnected

    /** ¿Se muestra el aviso de reinicio? */
    val showBanner: Boolean get() = announced && !bannerGivenUp

    /** Pasó demasiado tiempo sin volver: se deja de mostrar el aviso, pero se sigue sabiendo que venimos de un reinicio. */
    fun giveUpBanner(): RestartState = if (announced) copy(bannerGivenUp = true) else this
}

/**
 * El aviso de la franja sobre el chat, o `null` si no hay nada que decir. Un reinicio anunciado manda sobre el resto
 * (errores de conexión repetidos, límite de mensajes), porque es la explicación de por qué no hay conexión.
 */
fun connectionBanner(isConnected: Boolean, restart: RestartState, notice: String?): String? = when {
    restart.showBanner -> if (isConnected) RESTART_ANNOUNCED_MESSAGE else RESTART_RECONNECTING_MESSAGE
    !isConnected -> notice ?: RECONNECTING_MESSAGE
    else -> notice
}

/**
 * Mensaje de un `room-error` que llega al volver de un reinicio (típico: la sala no se recuperó). El server
 * dice solo "La sala no existe."; se agrega el contexto para que no parezca un fallo de la app.
 */
fun roomErrorAfterRestart(message: String): String =
    "$message El servidor se estaba reiniciando y puede que la sala no se haya recuperado."
