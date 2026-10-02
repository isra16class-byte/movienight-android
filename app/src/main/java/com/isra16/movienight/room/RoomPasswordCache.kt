package com.isra16.movienight.room

import java.util.concurrent.ConcurrentHashMap

/**
 * Pasa la contraseña de una sala recién creada desde el diálogo de "Crear sala" a la pantalla de la
 * sala, para no volver a pedírsela a su propio dueño (el server la exige en `join-room` aunque la
 * persona sea la dueña). Solo vive en memoria: nunca va en una ruta de navegación (quedaría en la
 * pila) ni en disco. Cada contraseña se entrega una sola vez ([take]).
 */
class RoomPasswordCache {
    private val passwords = ConcurrentHashMap<String, String>()

    fun put(roomId: String, password: String) {
        passwords[roomId] = password
    }

    fun take(roomId: String): String? = passwords.remove(roomId)
}
