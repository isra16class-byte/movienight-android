package com.isra16.movienight.notify

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.isra16.movienight.MovieNightApp
import com.isra16.movienight.net.UPLOAD_PROGRESS_FALLBACK

/**
 * Servicio en primer plano (tipo `dataSync`) que mantiene vivo el proceso mientras se sube un video con la
 * app en segundo plano. No sube nada: quien arranca y para este servicio es [UploadKeepAlive], según el
 * estado de las subidas. Ver la explicación allí (Fase 6C, opción B).
 */
class UploadService : Service() {
    private val keepAlive: UploadKeepAlive
        get() = (application as MovieNightApp).container.uploadKeepAlive

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Después de startForegroundService hay que llamar a startForeground enseguida, siempre.
        val notice = keepAlive.currentNotice() ?: UPLOAD_PROGRESS_FALLBACK
        ServiceCompat.startForeground(
            this,
            PROGRESS_NOTIFICATION_ID,
            buildProgressNotification(this, notice),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        // La subida terminó antes de que el servicio arrancara del todo: no queda nada que mantener.
        if (!keepAlive.isActive) stopSelf()
        return START_NOT_STICKY // si el sistema lo mata, la subida también se perdió: no hay nada que reanudar
    }

    /** Android 15 limita los servicios `dataSync` a unas 6 h por día: al cumplirse hay que pararlo. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        keepAlive.onServiceDestroyed()
        super.onDestroy()
    }
}
