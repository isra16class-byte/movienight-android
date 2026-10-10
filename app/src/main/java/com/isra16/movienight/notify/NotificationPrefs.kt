package com.isra16.movienight.notify

import android.content.Context

/**
 * Recuerda que ya se le preguntó a la persona por las notificaciones, para no insistir (solo la pregunta
 * propia de la app: Android además limita cuántas veces muestra su cuadro de permiso).
 */
class NotificationPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val alreadyAsked: Boolean
        get() = prefs.getBoolean(KEY_ASKED, false)

    fun markAsked() {
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
    }

    private companion object {
        const val PREFS_NAME = "movienight_notifications"
        const val KEY_ASKED = "permissionAsked"
    }
}
