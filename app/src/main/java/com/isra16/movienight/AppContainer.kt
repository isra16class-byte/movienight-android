package com.isra16.movienight

import android.content.Context
import com.isra16.movienight.auth.SessionManager
import com.isra16.movienight.net.MovieNightApi
import com.isra16.movienight.net.PersistentCookieJar
import com.isra16.movienight.net.normalizeBaseUrl
import okhttp3.OkHttpClient

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
    val api = MovieNightApi(httpClient)
    val session = SessionManager(api, cookieJar, baseUrl)
}
