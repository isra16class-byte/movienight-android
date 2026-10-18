package com.isra16.movienight.auth

import android.content.Context
import com.isra16.movienight.net.RecentRoom
import com.isra16.movienight.net.decodeRecentRooms
import com.isra16.movienight.net.encodeRecentRooms
import com.isra16.movienight.net.normalizeRecentRoomId
import com.isra16.movienight.net.removeRecentRoom
import com.isra16.movienight.net.upsertRecentRoom

/**
 * Las salas a las que cada cuenta entró en este teléfono ("Mis salas", Home > Salas). SharedPreferences privadas, mismo patrón que
 * `ThemePrefs`; una clave por cuenta (`rooms_<id de la cuenta>`), así otra cuenta en el mismo teléfono no ve la lista de la primera.
 * No se borra al cerrar sesión. El formato y las reglas (sin duplicados, tope, orden) son funciones puras de `net/RecentRoomsLogic.kt`.
 */
class RecentRoomsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(accountId: String) = KEY_PREFIX + accountId

    fun load(accountId: String): List<RecentRoom> = decodeRecentRooms(prefs.getString(key(accountId), null))

    /** Registra (o actualiza) una sala: va al frente con la hora dada. El id se guarda normalizado. */
    fun record(accountId: String, roomId: String, videoName: String, nowMillis: Long) {
        val id = normalizeRecentRoomId(roomId)
        if (accountId.isBlank() || id.isBlank()) return
        save(accountId, upsertRecentRoom(load(accountId), id, videoName, nowMillis))
    }

    fun remove(accountId: String, roomId: String) {
        if (accountId.isBlank()) return
        save(accountId, removeRecentRoom(load(accountId), normalizeRecentRoomId(roomId)))
    }

    private fun save(accountId: String, list: List<RecentRoom>) {
        prefs.edit().putString(key(accountId), encodeRecentRooms(list)).apply()
    }

    private companion object {
        const val PREFS_NAME = "movienight_recent_rooms"
        const val KEY_PREFIX = "rooms_"
    }
}
