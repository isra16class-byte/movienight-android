package com.isra16.movienight.ui.theme

import android.content.Context
import com.isra16.movienight.net.ThemeMode
import com.isra16.movienight.net.themeModeFor

/**
 * Recuerda el modo de color que eligió la persona (claro u oscuro). Mismo patrón que `NotificationPrefs`:
 * SharedPreferences, sin DataStore. Qué modo toca según lo guardado (y en el primer arranque) lo decide la función
 * pura [themeModeFor].
 */
class ThemePrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var mode: ThemeMode
        get() = themeModeFor(prefs.getString(KEY_MODE, null))
        set(value) {
            prefs.edit().putString(KEY_MODE, value.storageValue).apply()
        }

    private companion object {
        const val PREFS_NAME = "movienight_theme"
        const val KEY_MODE = "mode"
    }
}
