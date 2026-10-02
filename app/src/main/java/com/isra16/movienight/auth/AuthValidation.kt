package com.isra16.movienight.auth

/**
 * Validación local de los formularios de cuenta. Replica las reglas reales del backend
 * (`server.js`, rama plan-produccion: `EMAIL_REGEX` y `MIN_PASSWORD_LENGTH = 8`) para avisar antes de
 * hacer la request, pero el server sigue siendo la fuente de verdad: si algún día cambia una regla,
 * su mensaje de error (400) se muestra tal cual.
 *
 * Sin dependencias de Android, para poder testearla como JVM puro.
 */
object AuthValidation {

    const val MIN_PASSWORD_LENGTH = 8

    // Misma expresión que el server: algo@algo.algo, sin espacios ni más de una arroba.
    private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    /** `null` si el email es válido; si no, el mensaje a mostrar. El server hace `trim()` antes de validar. */
    fun emailError(email: String): String? {
        val trimmed = email.trim()
        return when {
            trimmed.isEmpty() -> "Escribí tu email."
            !EMAIL_REGEX.matches(trimmed) -> "El email no tiene un formato válido."
            else -> null
        }
    }

    /** Al iniciar sesión solo se exige que no esté vacía: la regla de largo es del registro. */
    fun loginPasswordError(password: String): String? =
        if (password.isEmpty()) "Escribí tu contraseña." else null

    /** El server no recorta la contraseña (los espacios cuentan), así que acá tampoco. */
    fun newPasswordError(password: String): String? = when {
        password.isEmpty() -> "Elegí una contraseña."
        password.length < MIN_PASSWORD_LENGTH -> "La contraseña necesita al menos $MIN_PASSWORD_LENGTH caracteres."
        else -> null
    }

    fun confirmPasswordError(password: String, confirmation: String): String? = when {
        confirmation.isEmpty() -> "Repetí la contraseña."
        confirmation != password -> "Las contraseñas no coinciden."
        else -> null
    }
}
