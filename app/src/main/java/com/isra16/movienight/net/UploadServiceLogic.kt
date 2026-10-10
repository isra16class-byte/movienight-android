package com.isra16.movienight.net

/**
 * Lógica pura del servicio en primer plano de las subidas (Fase 6C, opción B): cuándo hace falta y qué
 * muestra su notificación de avance. Sin nada de Android para poder probarla en la JVM; el servicio y la
 * notificación están en `notify/`.
 *
 * Por qué existe: con la app en segundo plano y sin un servicio en primer plano, Android corta la red de la
 * subida ("Se cortó la conexión durante la subida", visto en el emulador). Mientras el servicio está activo
 * el proceso queda con prioridad de primer plano. El servicio NO sube nada: la subida sigue siendo la
 * corrutina de [UploadFlow]; el servicio solo mantiene vivo el proceso y muestra el avance.
 *
 * El servicio se activa desde que empieza la subida ([UploadState.Preparing]) hasta que termina el paso
 * siguiente ([UploadState.Finishing]). Se arranca ya en "preparando" porque Android 12+ no deja arrancar un
 * servicio en primer plano desde segundo plano: si la persona sale de la app justo después de elegir el
 * video, todavía hay que poder arrancarlo. Un archivo inválido falla en ese mismo instante; como las
 * notificaciones de servicios de vida corta se difieren unos segundos, no se ve nada.
 */

/** Texto de la notificación de avance. [percent] `null` = barra sin porcentaje (indeterminada). */
data class UploadProgressNotice(val title: String, val text: String, val percent: Int?)

/** Título de la notificación mientras hay una subida en marcha. */
const val UPLOAD_PROGRESS_TITLE = "Subiendo video"

/** Para cuando el servicio arranca y el estado ya cambió (carrera de un instante). */
val UPLOAD_PROGRESS_FALLBACK = UploadProgressNotice(UPLOAD_PROGRESS_TITLE, "Preparando…", null)

/**
 * Notificación de avance para [state], o `null` si ese estado no necesita el servicio (sin subida,
 * terminada o fallada). Solo cambia cuando cambia el porcentaje entero, no cada byte, para no
 * llenar de actualizaciones a la notificación.
 */
fun progressNoticeFor(state: UploadState): UploadProgressNotice? = when (state) {
    is UploadState.Preparing -> UploadProgressNotice(UPLOAD_PROGRESS_TITLE, "Preparando la subida…", null)
    is UploadState.Uploading -> {
        val percent = (state.fraction * 100).toInt().coerceIn(0, 100)
        UploadProgressNotice(UPLOAD_PROGRESS_TITLE, "${state.fileName} · $percent %", percent)
    }
    is UploadState.Finishing -> UploadProgressNotice(UPLOAD_PROGRESS_TITLE, finishingLabel(state.goal), null)
    else -> null
}

/**
 * Qué subidas necesitan el servicio ahora. Puede haber más de una a la vez (la de la pantalla principal y la
 * de una sala), cada una identificada por una clave. No es seguro entre hilos: quien lo usa lo sincroniza.
 */
class UploadActivityTracker {
    private val active = LinkedHashMap<Any, UploadProgressNotice>()

    /** Registra el estado nuevo de la subida [key]; una subida que ya no necesita el servicio se quita. */
    fun update(key: Any, state: UploadState) {
        val notice = progressNoticeFor(state)
        if (notice == null) active.remove(key) else active[key] = notice
    }

    /** Hay al menos una subida que necesita el servicio. */
    val isActive: Boolean get() = active.isNotEmpty()

    /** Lo que muestra la notificación: la subida, o un resumen si hay varias; `null` si no hay ninguna. */
    fun currentNotice(): UploadProgressNotice? = when (active.size) {
        0 -> null
        1 -> active.values.first()
        else -> UploadProgressNotice(UPLOAD_PROGRESS_TITLE, "${active.size} subidas en curso", null)
    }
}
