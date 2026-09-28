package com.isra16.movienight.net

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject

/**
 * CookieJar que guarda las cookies en SharedPreferences, para que la sesión del backend
 * (`movienight.sid`, httpOnly) sobreviva al cierre de la app.
 *
 * Se comparte entre el cliente HTTP y el de Socket.IO: el server lee la sesión también en el
 * handshake de Socket.IO (`io.engine.use(sessionMiddleware)`), así que la misma cookie tiene que
 * viajar en los dos.
 *
 * Nota: SharedPreferences no está cifrado (queda en el almacenamiento privado de la app). La
 * cookie se excluye de los backups en `res/xml/backup_rules.xml` y `data_extraction_rules.xml`.
 */
class PersistentCookieJar(context: Context) : CookieJar {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val store = LinkedHashMap<String, Cookie>()

    init {
        synchronized(lock) { load() }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            for (cookie in cookies) {
                // Una cookie ya vencida es la forma en que el server pide borrarla
                // (ej. `res.clearCookie('movienight.sid')` en /auth/logout).
                if (cookie.expiresAt <= now) store.remove(keyOf(cookie)) else store[keyOf(cookie)] = cookie
            }
            persist()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val expiredKeys = store.filterValues { it.expiresAt <= now }.keys.toList()
            if (expiredKeys.isNotEmpty()) {
                expiredKeys.forEach { store.remove(it) }
                persist()
            }
            return store.values.filter { it.matches(url) }
        }
    }

    /** ¿Hay una cookie guardada con ese nombre? (para verificar el login en el spike) */
    fun hasCookie(name: String): Boolean = synchronized(lock) {
        store.values.any { it.name == name && it.expiresAt > System.currentTimeMillis() }
    }

    fun clear() {
        synchronized(lock) {
            store.clear()
            persist()
        }
    }

    private fun keyOf(cookie: Cookie) = "${cookie.name};${cookie.domain};${cookie.path}"

    private fun persist() {
        val array = JSONArray()
        for (c in store.values) {
            array.put(
                JSONObject()
                    .put("name", c.name)
                    .put("value", c.value)
                    .put("expiresAt", c.expiresAt)
                    .put("domain", c.domain)
                    .put("path", c.path)
                    .put("secure", c.secure)
                    .put("httpOnly", c.httpOnly)
                    .put("hostOnly", c.hostOnly)
            )
        }
        prefs.edit().putString(KEY_COOKIES, array.toString()).apply()
    }

    private fun load() {
        val raw = prefs.getString(KEY_COOKIES, null) ?: return
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val builder = Cookie.Builder()
                    .name(o.getString("name"))
                    .value(o.getString("value"))
                    .expiresAt(o.getLong("expiresAt"))
                    .path(o.getString("path"))
                if (o.getBoolean("hostOnly")) {
                    builder.hostOnlyDomain(o.getString("domain"))
                } else {
                    builder.domain(o.getString("domain"))
                }
                if (o.getBoolean("secure")) builder.secure()
                if (o.getBoolean("httpOnly")) builder.httpOnly()
                val cookie = builder.build()
                store[keyOf(cookie)] = cookie
            }
        } catch (e: Exception) {
            // Datos corruptos: se descartan y el usuario simplemente vuelve a iniciar sesión.
            store.clear()
            prefs.edit().remove(KEY_COOKIES).apply()
        }
    }

    private companion object {
        const val PREFS_NAME = "movienight_cookies"
        const val KEY_COOKIES = "cookies"
    }
}
