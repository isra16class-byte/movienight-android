package com.isra16.movienight.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * Llamadas HTTP al backend de `movienight` (ver docs/API-CONTRATO.md). En esta etapa (spike) no
 * interpreta las respuestas: devuelve código + cuerpo tal cual, para poder verlos en el log.
 */
class MovieNightApi(private val http: OkHttpClient) {

    /** `code == -1` significa que la request ni siquiera llegó a tener respuesta (red, TLS, URL mala). */
    data class Result(val code: Int, val body: String) {
        val isNetworkError: Boolean get() = code == -1
    }

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun get(baseUrl: String, path: String): Result =
        execute { Request.Builder().url(baseUrl + path).get().build() }

    suspend fun postJson(baseUrl: String, path: String, body: JSONObject = JSONObject()): Result =
        execute {
            Request.Builder()
                .url(baseUrl + path)
                .post(body.toString().toRequestBody(jsonType))
                .build()
        }

    private suspend fun execute(buildRequest: () -> Request): Result = withContext(Dispatchers.IO) {
        try {
            http.newCall(buildRequest()).execute().use { response ->
                Result(response.code, response.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            Result(-1, "${e.javaClass.simpleName}: ${e.message}")
        } catch (e: IllegalArgumentException) {
            // OkHttp lanza esto si la URL no se puede parsear.
            Result(-1, "URL inválida: ${e.message}")
        }
    }
}
