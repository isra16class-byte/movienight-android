package com.isra16.movienight.auth

import android.content.Context
import java.util.UUID

/**
 * `userId` persistente de esta instalación: un UUID generado la primera vez y guardado, igual que
 * `getPersistentUserId()` de `room.html` en la web. Se manda en `join-room` y el server lo usa para
 * reconocer a la misma persona entre reconexiones (no repetir "se unió a la sala" tras un corte de
 * wifi) y para recordar si el host la silenció. Es distinto del `id` de la cuenta a propósito: es
 * por dispositivo, no por cuenta. Se excluye de los backups (ver backup_rules.xml): restaurarlo en
 * otro teléfono haría que dos dispositivos compartan identidad.
 */
class UserIdStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val userId: String by lazy {
        prefs.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_USER_ID, it).apply() }
    }

    private companion object {
        const val PREFS_NAME = "movienight_identity"
        const val KEY_USER_ID = "userId"
    }
}
