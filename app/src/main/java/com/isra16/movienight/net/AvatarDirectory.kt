package com.isra16.movienight.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** En qué va la lista de avatares del servidor. */
enum class AvatarListStatus { IDLE, LOADING, READY, ERROR }

/**
 * La lista de avatares y su estado. [avatars] conserva la última lista buena mientras se recarga o si una recarga falla; [errorMessage]
 * solo tiene texto con [AvatarListStatus.ERROR].
 */
data class AvatarDirectoryState(
    val status: AvatarListStatus = AvatarListStatus.IDLE,
    val avatars: List<Avatar> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * La lista de avatares que ofrece el servidor (`GET /api/avatars`, pública), compartida por toda la app (Fase 8, «Avatares de cuenta»).
 * Un solo pedido a la vez. [ensureLoaded] lo pide si hace falta y, tras un fallo, no lo repite antes de [AVATAR_LIST_RETRY_MS]
 * (`shouldRetryAvatarList`); [reload] es el botón «Reintentar» y no espera. Los eventos de la sala traen solo el `avatarId`: se resuelve
 * con esta lista (`avatarById`).
 */
class AvatarDirectory(
    private val api: MovieNightApi,
    private val baseUrl: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private companion object {
        const val PATH = "/api/avatars"
    }

    private val _state = MutableStateFlow(AvatarDirectoryState())
    val state: StateFlow<AvatarDirectoryState> = _state.asStateFlow()

    private val lock = Any()
    private var job: Job? = null
    private var lastFailureAtMillis: Long? = null

    /** Pide la lista si aún no está (o si falló hace al menos 30 s). No hace nada si ya está lista o hay un pedido en curso. */
    fun ensureLoaded() {
        synchronized(lock) {
            if (job?.isActive == true) return
            when (_state.value.status) {
                AvatarListStatus.READY, AvatarListStatus.LOADING -> return
                AvatarListStatus.ERROR -> if (!shouldRetryAvatarList(lastFailureAtMillis, System.currentTimeMillis())) return
                AvatarListStatus.IDLE -> Unit
            }
            start()
        }
    }

    /** Vuelve a pedir la lista ahora («Reintentar»). No hace nada si ya hay un pedido en curso. */
    fun reload() {
        synchronized(lock) {
            if (job?.isActive == true) return
            start()
        }
    }

    /** Siempre con [lock] tomado. */
    private fun start() {
        _state.update { it.copy(status = AvatarListStatus.LOADING, errorMessage = null) }
        job = scope.launch {
            var code = -1
            var list: List<Avatar>? = null
            try {
                val result = api.get(baseUrl, PATH)
                code = result.code
                if (result.code == 200) list = parseAvatarList(result.body, baseUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                code = -1
            }
            val loaded = list
            synchronized(lock) {
                if (loaded != null) {
                    lastFailureAtMillis = null
                    _state.value = AvatarDirectoryState(status = AvatarListStatus.READY, avatars = loaded)
                } else {
                    lastFailureAtMillis = System.currentTimeMillis()
                    _state.update { it.copy(status = AvatarListStatus.ERROR, errorMessage = avatarListErrorMessage(code)) }
                }
            }
        }
    }
}
