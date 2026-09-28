package com.isra16.movienight

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.isra16.movienight.net.JoinParams
import com.isra16.movienight.net.MovieNightApi
import com.isra16.movienight.net.PersistentCookieJar
import com.isra16.movienight.net.RoomSocket
import com.isra16.movienight.net.normalizeBaseUrl
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * ViewModel del spike de la Fase 1 (docs/PLAN-PRODUCCION.md): concentra el estado de la pantalla
 * de pruebas y orquesta la capa de red. Es descartable, pero la separación (net/ sin nada de UI)
 * está pensada para reusarse en la Fase 2.
 */
class SpikeViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("movienight_spike", Context.MODE_PRIVATE)
    private val cookieJar = PersistentCookieJar(app)

    // Cliente para requests normales.
    private val httpClient = OkHttpClient.Builder().cookieJar(cookieJar).build()

    // Cliente para Socket.IO: comparte CookieJar y pool con el de arriba, pero con readTimeout largo.
    // El long-polling de Engine.IO deja la request colgada hasta ~25s esperando datos; con el timeout
    // por defecto de OkHttp (10s) se cortaría solo.
    private val socketClient = httpClient.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()

    private val api = MovieNightApi(httpClient)
    private val roomSocket = RoomSocket(
        client = socketClient,
        onLog = { message -> log(message) },
        onConnectionChanged = { connected -> viewModelScope.launch { socketConnected = connected } },
    )

    // Igual que getPersistentUserId() de room.html: un UUID generado una vez por instalación.
    private val userId: String = prefs.getString("userId", null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString("userId", it).apply() }

    private var lastTypingSentAt = 0L

    // --- Estado de la pantalla -------------------------------------------------------------
    var serverUrl by mutableStateOf(prefs.getString("serverUrl", "") ?: "")
    var email by mutableStateOf(prefs.getString("email", "") ?: "")
    var password by mutableStateOf("") // a propósito no se persiste
    var roomId by mutableStateOf(prefs.getString("roomId", "") ?: "")
    var username by mutableStateOf(prefs.getString("username", "Android") ?: "Android")
    var roomPassword by mutableStateOf("") // a propósito no se persiste
    var chatText by mutableStateOf("")
    var socketConnected by mutableStateOf(false)
        private set

    /** Más nuevo arriba. Se recorta a [MAX_LOG_LINES]. */
    val logLines = mutableStateListOf<String>()

    // --- HTTP ---------------------------------------------------------------------------------
    fun health() = call("GET /health") { api.get(baseUrl(), "/health") }

    fun login() = call("POST /auth/login") {
        api.postJson(baseUrl(), "/auth/login", JSONObject().put("email", email.trim()).put("password", password))
    }

    fun me() = call("GET /auth/me") { api.get(baseUrl(), "/auth/me") }

    fun logout() = call("POST /auth/logout") { api.postJson(baseUrl(), "/auth/logout") }

    // --- Socket.IO ----------------------------------------------------------------------------
    fun connectSocket() {
        if (baseUrl().isEmpty()) {
            log("✗ Falta la URL del servidor")
            return
        }
        if (roomId.isBlank()) {
            log("✗ Falta el ID de sala")
            return
        }
        saveFields()
        roomSocket.connect(
            baseUrl(),
            JoinParams(
                roomId = roomId.trim(),
                username = username.trim().ifEmpty { "Android" },
                userId = userId,
                password = roomPassword.ifEmpty { null },
            ),
        )
    }

    fun disconnectSocket() = roomSocket.disconnect()

    fun sendChat() {
        val text = chatText.trim()
        if (text.isEmpty() || !roomSocket.isConnected) return
        log("→ chat-message: $text")
        roomSocket.sendChat(text)
        chatText = ""
    }

    /** El server retransmite cada `typing`; se limita a uno cada 2s para no inundar la sala. */
    fun sendTyping() {
        val now = System.currentTimeMillis()
        if (!roomSocket.isConnected || now - lastTypingSentAt < TYPING_THROTTLE_MS) return
        lastTypingSentAt = now
        roomSocket.sendTyping()
    }

    fun sendReaction(emoji: String) {
        if (!roomSocket.isConnected) return
        log("→ reaction: $emoji")
        roomSocket.sendReaction(emoji)
    }

    fun clearLog() = logLines.clear()

    // --- Internos -----------------------------------------------------------------------------
    private fun baseUrl() = normalizeBaseUrl(serverUrl)

    private fun call(label: String, block: suspend () -> MovieNightApi.Result) {
        saveFields()
        viewModelScope.launch {
            log("→ $label")
            val result = block()
            val code = if (result.isNetworkError) "ERROR" else result.code.toString()
            log("← $code ${result.body.take(MAX_BODY_CHARS)}")
            // Verifica el criterio de la Fase 1: que el CookieJar guardó (o borró) la sesión.
            if (label.contains("/auth/")) {
                log("🍪 movienight.sid en el CookieJar: ${if (cookieJar.hasCookie(SESSION_COOKIE)) "sí" else "no"}")
            }
        }
    }

    /** Se puede llamar desde cualquier hilo (los callbacks de Socket.IO no son el principal). */
    private fun log(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        viewModelScope.launch {
            logLines.add(0, "$time $message")
            while (logLines.size > MAX_LOG_LINES) logLines.removeAt(logLines.lastIndex)
        }
    }

    private fun saveFields() {
        prefs.edit()
            .putString("serverUrl", serverUrl)
            .putString("email", email)
            .putString("roomId", roomId)
            .putString("username", username)
            .apply()
    }

    override fun onCleared() {
        roomSocket.disconnect()
        super.onCleared()
    }

    private companion object {
        const val SESSION_COOKIE = "movienight.sid"
        const val MAX_LOG_LINES = 300
        const val MAX_BODY_CHARS = 400
        const val TYPING_THROTTLE_MS = 2_000L
    }
}
