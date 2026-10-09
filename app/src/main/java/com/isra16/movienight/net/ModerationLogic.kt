package com.isra16.movienight.net

/**
 * Fase 5: moderación del host. Lógica pura (sin Android) de los tres eventos que el host le manda al server.
 *
 * Lo que hace el server (verificado en `server.js`, rama `plan-produccion`):
 * - Los tres los autoriza solo `socket.isHost` (NO el dueño de la sala, a diferencia de cambiar el video).
 * - El payload es el `id` de socket del objetivo como TEXTO PLANO (el `id` de cada integrante de `viewer-list`).
 * - No contestan nada: no hay confirmación ni error. Si algo no corresponde, lo ignoran en silencio. El resultado
 *   se ve en los eventos que ya llegan: `viewer-list` (silencio, host, quién sigue conectado), `host-status`
 *   (el que cedió el host deja de serlo) y `kicked` / `mute-status` en el objetivo.
 * - Rechazan solo `make-host` a uno mismo. `kick-user` y `toggle-mute` sobre uno mismo SÍ se ejecutan (te
 *   expulsarías o te silenciarías), así que la app no los ofrece nunca sobre la propia fila.
 */
enum class ModerationAction(val wire: String) {
    MAKE_HOST("make-host"),
    TOGGLE_MUTE("toggle-mute"),
    KICK("kick-user"),
}

/** Solo se moderan con el rol de host CONFIRMADO por `host-status` (igual que `canEmitSync`) y con conexión. */
fun canModerate(isHost: Boolean, isConnected: Boolean): Boolean = isHost && isConnected

/**
 * ¿Se le puede aplicar una acción a esta persona? No si es uno mismo ([myId] es el `id` de socket propio; si no
 * se conoce, por las dudas no se ofrece nada) ni si figura como host (el host es uno solo: sería uno mismo).
 */
fun isModerationTarget(viewer: Viewer, myId: String?): Boolean =
    viewer.id.isNotEmpty() && !myId.isNullOrEmpty() && viewer.id != myId && !viewer.isHost

/** Las acciones que se ofrecen sobre [viewer]: ninguna si no sos host confirmado, estás sin conexión o es uno mismo. */
fun availableActions(viewer: Viewer, myId: String?, isHost: Boolean, isConnected: Boolean): List<ModerationAction> =
    if (canModerate(isHost, isConnected) && isModerationTarget(viewer, myId)) {
        listOf(ModerationAction.MAKE_HOST, ModerationAction.TOGGLE_MUTE, ModerationAction.KICK)
    } else {
        emptyList()
    }

/** Resultado de comprobar un pedido de moderación contra el estado actual, antes de emitir nada. */
sealed interface ModerationCheck {
    data class Allowed(val target: Viewer) : ModerationCheck
    data class Rejected(val message: String) : ModerationCheck
}

/** Vuelve a comprobar, justo al emitir, lo que la lista ofrecía (el estado pudo cambiar mientras se confirmaba). */
fun checkModeration(
    targetId: String,
    viewers: List<Viewer>,
    myId: String?,
    isHost: Boolean,
    isConnected: Boolean,
): ModerationCheck {
    if (!isConnected) return ModerationCheck.Rejected("Sin conexión con la sala. Esperá a que se reconecte.")
    if (!isHost) return ModerationCheck.Rejected("Ya no sos el host de la sala.")
    val target = viewers.firstOrNull { it.id == targetId }
        ?: return ModerationCheck.Rejected("Esa persona ya no está en la sala.")
    if (!isModerationTarget(target, myId)) {
        return ModerationCheck.Rejected("Esa acción no se puede aplicar a esa persona.")
    }
    return ModerationCheck.Allowed(target)
}

/** Payload de los tres eventos: el `id` de socket del objetivo, tal cual (texto plano, no un objeto). `null` si no sirve. */
fun moderationPayload(targetId: String): String? = targetId.takeIf { it.isNotBlank() }

/** Texto del ítem del menú para [action] sobre [viewer]. */
fun actionLabel(action: ModerationAction, viewer: Viewer): String = when (action) {
    ModerationAction.MAKE_HOST -> "Hacer host"
    ModerationAction.TOGGLE_MUTE -> if (viewer.muted) "Quitar silencio" else "Silenciar"
    ModerationAction.KICK -> "Expulsar"
}

/** Cuadro de confirmación de una acción. */
data class ModerationConfirmation(val title: String, val text: String, val confirmLabel: String)

/**
 * Qué se le pregunta a la persona antes de emitir. Expulsar y pasar el host piden confirmar (no se deshacen
 * desde acá); silenciar no, porque se revierte con un toque.
 */
fun confirmationFor(action: ModerationAction, name: String): ModerationConfirmation? = when (action) {
    ModerationAction.KICK -> ModerationConfirmation(
        title = "¿Expulsar a $name?",
        text = "Sale de la sala ahora mismo. Si tiene el link (y la contraseña, si hay), puede volver a entrar.",
        confirmLabel = "Expulsar",
    )
    ModerationAction.MAKE_HOST -> ModerationConfirmation(
        title = "¿Pasarle el control a $name?",
        text = "Va a poder pausar, adelantar y moderar la sala, y vos dejás de poder hacerlo.",
        confirmLabel = "Hacer host",
    )
    ModerationAction.TOGGLE_MUTE -> null
}

/** Cómo se ve una fila de la lista de conectados: nombre y, si corresponde, "vos", host y silenciado. */
fun viewerLabel(viewer: Viewer, isMe: Boolean): String {
    val tags = buildList {
        if (isMe) add("vos")
        if (viewer.isHost) add("host")
        if (viewer.muted) add("silenciado")
    }
    return if (tags.isEmpty()) viewer.username else "${viewer.username} · ${tags.joinToString(", ")}"
}

/**
 * ¿El `viewer-list` nuevo ya refleja lo que se pidió? [before] es la persona tal como estaba al pedirlo.
 * Si ya no está en la lista, no queda nada que confirmar (para expulsar es justo el resultado; para el resto, se fue).
 */
fun outcomeReached(action: ModerationAction, before: Viewer, viewers: List<Viewer>): Boolean {
    val now = viewers.firstOrNull { it.id == before.id } ?: return true
    return when (action) {
        ModerationAction.MAKE_HOST -> now.isHost
        ModerationAction.TOGGLE_MUTE -> now.muted != before.muted
        ModerationAction.KICK -> false
    }
}

/** Aviso cuando el server no devolvió el cambio esperado en el tiempo de espera (no manda errores propios). */
fun moderationTimeoutMessage(action: ModerationAction, name: String): String = when (action) {
    ModerationAction.MAKE_HOST -> "El servidor no confirmó el traspaso del host a $name. Revisá la lista y probá de nuevo."
    ModerationAction.TOGGLE_MUTE -> "El servidor no confirmó el cambio de silencio de $name. Revisá la lista y probá de nuevo."
    ModerationAction.KICK -> "$name sigue en la sala: el servidor no confirmó la expulsión. Probá de nuevo."
}

/**
 * Pedidos de moderación en curso, uno por persona. Importa sobre todo para `toggle-mute`: el server ALTERNA, así
 * que dos toques seguidos antes de que llegue el `viewer-list` lo dejarían como estaba. Mientras hay un pedido
 * en curso sobre alguien, no se acepta otro sobre esa misma persona.
 */
class PendingModerations {
    private class Entry(val action: ModerationAction, val before: Viewer)

    private val entries = LinkedHashMap<String, Entry>()

    /** Ids con un pedido en curso. */
    val ids: Set<String> get() = entries.keys.toSet()

    fun isPending(id: String): Boolean = entries.containsKey(id)

    /** Registra el pedido. `false` (y no hace nada) si ya había uno en curso sobre esa persona. */
    fun begin(action: ModerationAction, before: Viewer): Boolean {
        if (entries.containsKey(before.id)) return false
        entries[before.id] = Entry(action, before)
        return true
    }

    /** Un `viewer-list` nuevo: da por cumplidos (y devuelve los ids de) los pedidos que ya se reflejan. */
    fun resolve(viewers: List<Viewer>): List<String> {
        val done = entries.filter { (_, e) -> outcomeReached(e.action, e.before, viewers) }.keys.toList()
        done.forEach { entries.remove(it) }
        return done
    }

    /** Se acabó la espera: devuelve la acción y la persona si el pedido seguía en curso (o `null` si ya se resolvió). */
    fun expire(id: String): Pair<ModerationAction, Viewer>? =
        entries.remove(id)?.let { it.action to it.before }

    fun clear() = entries.clear()
}
