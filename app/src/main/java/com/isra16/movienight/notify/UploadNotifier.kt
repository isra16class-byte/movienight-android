package com.isra16.movienight.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.isra16.movienight.R
import com.isra16.movienight.net.UploadState
import com.isra16.movienight.net.VisibilityCounter
import com.isra16.movienight.net.uploadNotice

private const val UPLOAD_CHANNEL_ID = "uploads"
private const val UPLOAD_NOTIFICATION_ID = 1001

/** Crea los canales de las notificaciones de subida (el `minSdk` es 26, así que los canales siempre existen). */
fun createUploadChannels(context: Context) {
    createUploadProgressChannel(context)
    val channel = NotificationChannel(UPLOAD_CHANNEL_ID, "Subidas de video", NotificationManager.IMPORTANCE_DEFAULT)
        .apply { description = "Avisa cuando termina una subida de video o falla, si la app no está a la vista." }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

/**
 * Muestra el aviso cuando una subida termina o falla y la app no se está viendo (Fase 6C, opción A).
 * Qué decir y cuándo lo decide la lógica pura (`uploadNotice`); esta clase solo lo dibuja. Si las
 * notificaciones están desactivadas (o el permiso de Android 13+ no se dio) no hace nada.
 */
class UploadNotifier(
    context: Context,
    private val visibility: VisibilityCounter,
) {
    private val context = context.applicationContext

    /** Se llama cuando una subida llega a su estado final ([UploadState.Done] o [UploadState.Failed]). */
    @SuppressLint("MissingPermission") // se comprueba con areNotificationsEnabled(); y se captura SecurityException
    fun onUploadFinished(state: UploadState) {
        val notice = uploadNotice(state, appVisible = visibility.isVisible) ?: return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val notification = NotificationCompat.Builder(context, UPLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_upload)
            .setContentTitle(notice.title)
            .setContentText(notice.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.text))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(launchAppPendingIntent(context)) // tocar el aviso trae la app al frente
            .build()
        try {
            manager.notify(UPLOAD_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // El permiso se revocó justo ahora: sin aviso, la subida ya terminó igual.
        }
    }

    /** La app volvió a verse: el aviso viejo ya no sirve (la pantalla muestra el resultado). */
    fun onAppShown() {
        NotificationManagerCompat.from(context).cancel(UPLOAD_NOTIFICATION_ID)
    }
}
