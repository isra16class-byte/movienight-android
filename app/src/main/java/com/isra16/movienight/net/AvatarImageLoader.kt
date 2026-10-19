package com.isra16.movienight.net

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Baja y guarda los iconos de los avatares (Fase 8, «Avatares de cuenta»), sin librerías: `OkHttp` + `BitmapFactory`. Tres niveles:
 * 1) memoria (`LruCache` de unos 4 MB); 2) disco, un archivo por URL completa en [cacheDir] (nombre de [avatarCacheKey]; el `?v=` del
 * servidor cambia si se reemplaza el icono, así que un icono nuevo no se confunde con el viejo); 3) la descarga. Dos pedidos de la misma
 * URL a la vez comparten la descarga. Lo que tenga más de 30 días o pase de unos 5 MB se borra de disco (`avatarCacheFilesToDelete`).
 */
class AvatarImageLoader(
    private val http: OkHttpClient,
    private val cacheDir: File,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private companion object {
        const val TAG = "AvatarImageLoader"
        const val MEMORY_BYTES = 4 * 1024 * 1024

        /** Los iconos pesan de 60 a 110 KB; más de esto no es un icono. */
        const val MAX_DOWNLOAD_BYTES = 2 * 1024 * 1024
    }

    private val memory = object : LruCache<String, Bitmap>(MEMORY_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val lock = Any()
    private val inFlight = HashMap<String, Deferred<Bitmap?>>()

    /** Lo que ya está en memoria para [url], sin esperar nada (para dibujar sin parpadeo lo que ya se vio). */
    fun peek(url: String): Bitmap? = memory.get(url)

    /**
     * El icono de [url] listo para dibujar (~[AVATAR_DECODE_TARGET_PX] px), o `null` si no se pudo (sin red, respuesta que no es imagen,
     * PNG roto...). No lanza por errores de red o de imagen; sí propaga la cancelación de quien espera.
     */
    suspend fun load(url: String): Bitmap? {
        memory.get(url)?.let { return it }
        val (shared, created) = synchronized(lock) {
            val existing = inFlight[url]
            if (existing != null) {
                existing to false
            } else {
                // `LAZY` + `start()` después de registrarla: si la descarga terminara antes de registrarse, el `remove` de su `finally` no
                // encontraría nada y quedaría para siempre un resultado viejo (un `null` de un fallo).
                val deferred = scope.async(start = CoroutineStart.LAZY) {
                    try {
                        loadUncached(url)
                    } finally {
                        synchronized(lock) { inFlight.remove(url) }
                    }
                }
                inFlight[url] = deferred
                deferred to true
            }
        }
        if (created) shared.start()
        return shared.await()
    }

    private fun loadUncached(url: String): Bitmap? {
        try {
            memory.get(url)?.let { return it }
            val file = File(cacheDir, avatarCacheKey(url))
            readFromDisk(file)?.let { bitmap ->
                memory.put(url, bitmap)
                return bitmap
            }
            val bytes = download(url) ?: return null
            val bitmap = decode(bytes) ?: return null // lo que no es una imagen nunca se guarda
            saveToDisk(file, bytes)
            memory.put(url, bitmap)
            return bitmap
        } catch (e: Exception) {
            Log.w(TAG, "no se pudo cargar el avatar: ${e.javaClass.simpleName}: ${e.message}")
            return null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "sin memoria para decodificar el avatar")
            return null
        }
    }

    /** El icono guardado, o `null` si no hay o está roto (un archivo roto se borra y se vuelve a bajar). */
    private fun readFromDisk(file: File): Bitmap? {
        try {
            if (!file.isFile) return null
            val bitmap = decode(file.readBytes())
            if (bitmap == null) {
                file.delete()
                return null
            }
            file.setLastModified(System.currentTimeMillis()) // lo usado hace poco no se borra por viejo
            return bitmap
        } catch (e: Exception) {
            Log.w(TAG, "no se pudo leer la caché: ${e.javaClass.simpleName}: ${e.message}")
            return null
        }
    }

    private fun download(url: String): ByteArray? {
        val request = Request.Builder().url(url).get().build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "respuesta ${response.code} al bajar un avatar")
                return@use null
            }
            val body = response.body ?: return@use null
            if (body.contentLength() > MAX_DOWNLOAD_BYTES) return@use null
            // El cuerpo se cierra con la respuesta (`use`).
            val input = body.byteStream()
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_DOWNLOAD_BYTES) return@use null
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }

    /** Decodifica a ~[AVATAR_DECODE_TARGET_PX] px; `null` si los bytes no son una imagen. */
    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = avatarSampleSize(bounds.outWidth, bounds.outHeight, AVATAR_DECODE_TARGET_PX)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun saveToDisk(file: File, bytes: ByteArray) {
        try {
            cacheDir.mkdirs()
            val temp = File(cacheDir, file.name + ".tmp")
            temp.writeBytes(bytes)
            if (!temp.renameTo(file)) temp.delete()
            trimDisk()
        } catch (e: Exception) {
            // Sin caché de disco el icono se ve igual esta vez; solo se bajará de nuevo la próxima.
            Log.w(TAG, "no se pudo guardar en la caché: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun trimDisk() {
        val files = cacheDir.listFiles() ?: return
        val entries = files.filter { it.isFile }.map { AvatarCacheEntry(it.name, it.length(), it.lastModified()) }
        for (name in avatarCacheFilesToDelete(entries, System.currentTimeMillis())) File(cacheDir, name).delete()
    }
}
