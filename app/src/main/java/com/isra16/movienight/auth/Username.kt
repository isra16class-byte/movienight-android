package com.isra16.movienight.auth

private const val MAX_USERNAME_LENGTH = 40 // el server recorta `username` a 40 en join-room

/**
 * Nombre que se muestra en el chat de una sala: la parte del email antes de la `@`. La app exige
 * cuenta y no pregunta un nombre aparte. Sin dependencias de Android, para poder testearla como JVM puro.
 */
fun defaultUsername(email: String): String {
    val local = email.trim().substringBefore('@').trim()
    return local.take(MAX_USERNAME_LENGTH).ifEmpty { "Anónimo" }
}
