package com.isra16.movienight.net

/**
 * Lógica pura del aviso "terminó tu subida" (Fase 6C, opción A): qué decir y cuándo pedir el permiso.
 * Sin nada de Android para poder probarla en la JVM; el que muestra la notificación es
 * `notify/UploadNotifier.kt`.
 *
 * Alcance: solo avisa cuando una subida termina o falla y la app NO se está viendo. Si Android mata el
 * proceso a mitad de la subida no hay aviso (la subida también se pierde; ver la Fase 4A).
 */

/** Texto de una notificación. */
data class UploadNotice(val title: String, val text: String)

/**
 * Notificación para el estado final de una subida, o `null` si no hay nada que avisar: la app está a la
 * vista (la pantalla ya lo muestra), la subida sigue en curso o no empezó.
 */
fun uploadNotice(state: UploadState, appVisible: Boolean): UploadNotice? {
    if (appVisible) return null
    return when (state) {
        is UploadState.Done -> UploadNotice(
            title = when (state.goal) {
                UploadGoal.LIBRARY -> "Subida terminada"
                UploadGoal.CREATE_ROOM -> "Sala lista"
                UploadGoal.CHANGE_ROOM_VIDEO -> "Video de la sala cambiado"
            },
            text = doneMessage(state),
        )
        is UploadState.Failed -> UploadNotice(
            title = if (state.alreadyUploaded) "La subida quedó a medias" else "No se pudo subir el video",
            text = if (state.alreadyUploaded) "${state.message} $ALREADY_UPLOADED_NOTE" else state.message,
        )
        else -> null
    }
}

/** Primera versión de Android que exige pedir el permiso `POST_NOTIFICATIONS` (Android 13). */
const val NOTIFICATION_PERMISSION_MIN_SDK = 33

/**
 * Si hay que mostrar el cuadro que explica y pide el permiso de notificaciones: solo en Android 13+,
 * si las notificaciones no están activadas y nunca se le preguntó a la persona. Se llama justo antes
 * de abrir el selector de video, o sea en el momento en que el aviso tiene sentido, y una sola vez.
 */
fun shouldAskNotificationPermission(sdkInt: Int, notificationsEnabled: Boolean, alreadyAsked: Boolean): Boolean =
    sdkInt >= NOTIFICATION_PERMISSION_MIN_SDK && !notificationsEnabled && !alreadyAsked

/**
 * Cuenta las pantallas de la app que están "iniciadas" (entre `onStart` y `onStop`) para saber si la app
 * se está viendo, sin depender de librerías. Se usa solo desde el hilo principal.
 */
class VisibilityCounter {
    private var started = 0

    val isVisible: Boolean get() = started > 0

    fun onStart() {
        started++
    }

    fun onStop() {
        if (started > 0) started--
    }
}
