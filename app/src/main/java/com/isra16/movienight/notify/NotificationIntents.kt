package com.isra16.movienight.notify

import android.app.PendingIntent
import android.content.Context

/** Al tocar una notificación se trae la app al frente (o se abre), como si se tocara su ícono. */
fun launchAppPendingIntent(context: Context): PendingIntent? =
    context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
        PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
