package com.isra16.movienight.auth

import android.content.Context

/**
 * El nombre que cada cuenta eligió para mostrarse en las salas. SharedPreferences privadas, mismo patrón que `RecentRoomsStore`; una
 * clave por cuenta (`name_<id de la cuenta>`), así otra cuenta en el mismo teléfono no ve el nombre de la primera. Vive solo en este
 * teléfono (no viaja a otro dispositivo) y no se borra al cerrar sesión. Se guarda ya limpio ([normalizeDisplayName]).
 */
class ProfilePrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(accountId: String) = KEY_PREFIX + accountId

    /** El nombre guardado de la cuenta, o null si no eligió uno (o lo guardado no vale). */
    fun getName(accountId: String): String? {
        if (accountId.isBlank()) return null
        return prefs.getString(key(accountId), null)?.let(::normalizeDisplayName)
    }

    /** Guarda el nombre limpio. Un nombre que no vale ni cuenta vacía no cambian nada. */
    fun setName(accountId: String, name: String) {
        if (accountId.isBlank()) return
        val clean = normalizeDisplayName(name) ?: return
        prefs.edit().putString(key(accountId), clean).apply()
    }

    fun clearName(accountId: String) {
        if (accountId.isBlank()) return
        prefs.edit().remove(key(accountId)).apply()
    }

    private companion object {
        const val PREFS_NAME = "movienight_profile"
        const val KEY_PREFIX = "name_"
    }
}
