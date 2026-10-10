package com.isra16.movienight

import android.content.Context
import com.isra16.movienight.auth.SessionManager
import com.isra16.movienight.auth.UserIdStore
import com.isra16.movienight.net.MovieNightApi
import com.isra16.movienight.net.PersistentCookieJar
import com.isra16.movienight.net.VisibilityCounter
import com.isra16.movienight.net.VideoUploader
import com.isra16.movienight.net.normalizeBaseUrl
import com.isra16.movienight.notify.NotificationPrefs
import com.isra16.movienight.notify.UploadNotifier
import com.isra16.movienight.room.RoomPasswordCache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Objetos compartidos de toda la app (una sola instancia por proceso): el CookieJar y el cliente HTTP
 * tienen que ser únicos para que la sesión sea la misma en todas las pantallas. Sin framework de
 * inyección a propósito: por ahora son pocas piezas.
 */
class AppContainer(context: Context) {

    /** Fija por build (`movienight.baseUrl` en local.properties, ver app/build.gradle.kts). */
    val baseUrl: String = normalizeBaseUrl(BuildConfig.BASE_URL)

    val cookieJar = PersistentCookieJar(context)
    val httpClient: OkHttpClient = OkHttpClient.Builder().cookieJar(cookieJar).build()

    /**
     * Cliente para Socket.IO: comparte CookieJar y pool con [httpClient], pero con `readTimeout` largo.
     * El long-polling de Engine.IO deja la request colgada ~25 s esperando datos; con el timeout por
     * defecto de OkHttp (10 s) se cortaría solo.
     */
    val socketClient: OkHttpClient = httpClient.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()

    /**
     * Cliente para el PUT de subida de video directo al bucket (R2). Va SIN CookieJar a propósito: la
     * sesión del servidor no tiene nada que hacer en otro dominio. `callTimeout` queda en 0 (sin tope):
     * subir varios GB puede tardar horas. `writeTimeout` es por operación de escritura (si la red no
     * deja avanzar ni un bloque en 60 s, se da por cortada) y `readTimeout` es la espera de la respuesta
     * del bucket después del último byte.
     */
    val uploadClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    val api = MovieNightApi(httpClient)
    val uploader = VideoUploader(uploadClient, context.applicationContext.contentResolver)
    val session = SessionManager(api, cookieJar, baseUrl)
    val userIds = UserIdStore(context)
    val roomPasswords = RoomPasswordCache()

    /** Cuántas pantallas de la app se ven ahora (lo actualiza [MovieNightApp]); el aviso de subida lo consulta. */
    val visibility = VisibilityCounter()
    val notificationPrefs = NotificationPrefs(context)
    val uploadNotifier = UploadNotifier(context, visibility)
}
