package com.isra16.movienight.notify

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.isra16.movienight.R
import com.isra16.movienight.net.UploadActivityTracker
import com.isra16.movienight.net.UploadProgressNotice
import com.isra16.movienight.net.UploadState

private const val PROGRESS_CHANNEL_ID = "upload_progress"

/** Id de la notificación de avance (la del servicio en primer plano). */
const val PROGRESS_NOTIFICATION_ID = 1002

/** Cuánto se espera antes de parar el servicio cuando ya no hay subidas, para no pararlo antes de que termine de arrancar. */
private const val STOP_DELAY_MS = 2_000L

/** Crea el canal de la notificación de avance (silenciosa: es un estado, no un aviso). */
fun createUploadProgressChannel(context: Context) {
    val channel = NotificationChannel(PROGRESS_CHANNEL_ID, "Subida en curso", NotificationManager.IMPORTANCE_LOW)
        .apply { description = "Muestra el avance de una subida de video mientras la app está en segundo plano." }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

/** Arma la notificación de avance. Con [UploadProgressNotice.percent] `null` la barra es indeterminada. */
fun buildProgressNotification(context: Context, notice: UploadProgressNotice): Notification {
    val builder = NotificationCompat.Builder(context, PROGRESS_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_uploading)
        .setContentTitle(notice.title)
        .setContentText(notice.text)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(launchAppPendingIntent(context))
    val percent = notice.percent
    if (percent != null) builder.setProgress(100, percent, false) else builder.setProgress(0, 0, true)
    return builder.build()
}

/**
 * Mantiene vivo el proceso mientras hay una subida en marcha (Fase 6C, opción B). Sin un servicio en
 * primer plano, Android corta la red de la app en segundo plano y la subida falla con "Se cortó la
 * conexión". El servicio ([UploadService]) NO sube nada: la subida sigue siendo la corrutina de
 * `UploadFlow`; esto solo lo arranca mientras haya alguna, lo para cuando todas terminan y mantiene al
 * día la notificación de avance. Lo ve `UploadFlow` a través de `onStateChanged`.
 *
 * Si Android no deja arrancar el servicio (por ejemplo desde segundo plano en Android 12+), la subida
 * sigue como antes, sin protección. Se puede llamar desde cualquier hilo.
 */
class UploadKeepAlive(context: Context) {
    private val app = context.applicationContext
    private val lock = Any()
    private val tracker = UploadActivityTracker()
    private val main = Handler(Looper.getMainLooper())
    private val stopWhenIdle = Runnable { stopIfIdle() }

    private var serviceRequested = false
    private var startRefused = false
    private var lastNotice: UploadProgressNotice? = null

    /** Hay alguna subida que necesita el servicio. */
    val isActive: Boolean
        get() = synchronized(lock) { tracker.isActive }

    /** Lo que muestra la notificación de avance ahora, o `null` si no hay subidas. */
    fun currentNotice(): UploadProgressNotice? = synchronized(lock) { tracker.currentNotice() }

    /** Nuevo estado de la subida [key]. */
    fun onState(key: Any, state: UploadState) {
        synchronized(lock) {
            tracker.update(key, state)
            val notice = tracker.currentNotice()
            if (notice == null) {
                startRefused = false
                if (serviceRequested) {
                    // Se espera un poco antes de parar: el servicio puede estar todavía arrancando.
                    main.removeCallbacks(stopWhenIdle)
                    main.postDelayed(stopWhenIdle, STOP_DELAY_MS)
                }
            } else {
                main.removeCallbacks(stopWhenIdle)
                if (!serviceRequested) {
                    if (!startRefused) startService()
                } else if (notice != lastNotice) {
                    updateNotification(notice)
                }
                lastNotice = notice
            }
        }
    }

    /** El servicio se destruyó (lo paramos nosotros o el sistema, por ejemplo por el límite de 6 h de Android 15). */
    fun onServiceDestroyed() {
        synchronized(lock) {
            serviceRequested = false
            lastNotice = null
        }
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(app, Intent(app, UploadService::class.java))
            serviceRequested = true
        } catch (e: RuntimeException) {
            // Android no deja arrancarlo (segundo plano en Android 12+, permiso): la subida sigue sin servicio.
            startRefused = true
        }
    }

    private fun stopIfIdle() {
        synchronized(lock) {
            if (tracker.isActive || !serviceRequested) return
            app.stopService(Intent(app, UploadService::class.java))
            serviceRequested = false
            lastNotice = null
        }
    }

    @SuppressLint("MissingPermission") // sin permiso la notificación simplemente no se actualiza; el servicio sigue
    private fun updateNotification(notice: UploadProgressNotice) {
        try {
            NotificationManagerCompat.from(app).notify(PROGRESS_NOTIFICATION_ID, buildProgressNotification(app, notice))
        } catch (e: SecurityException) {
            // Permiso revocado ahora mismo: no pasa nada.
        }
    }
}
