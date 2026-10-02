package com.isra16.movienight

import com.isra16.movienight.auth.AuthValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidationTest {

    @Test
    fun emailValidoNoDaError() {
        assertNull(AuthValidation.emailError("ana@ejemplo.com"))
        assertNull(AuthValidation.emailError("ana.perez+sala@mail.ejemplo.uk"))
    }

    @Test
    fun emailSeRecortaAntesDeValidar() {
        // El server hace trim() del email, así que "  ana@ejemplo.com " es válido.
        assertNull(AuthValidation.emailError("  ana@ejemplo.com "))
    }

    @Test
    fun emailVacioPideEscribirlo() {
        assertEquals("Escribí tu email.", AuthValidation.emailError(""))
        assertEquals("Escribí tu email.", AuthValidation.emailError("   "))
    }

    @Test
    fun emailMalFormadoDaError() {
        for (malo in listOf("ana", "ana@", "@ejemplo.com", "ana@ejemplo", "ana@@ejemplo.com", "a na@ejemplo.com", "ana@ejemplo .com")) {
            assertNotNull("debería ser inválido: '$malo'", AuthValidation.emailError(malo))
        }
    }

    @Test
    fun loginSoloExigeContrasenaNoVacia() {
        assertEquals("Escribí tu contraseña.", AuthValidation.loginPasswordError(""))
        // Al iniciar sesión no se aplica el mínimo de 8: es regla del registro.
        assertNull(AuthValidation.loginPasswordError("abc"))
    }

    @Test
    fun contrasenaNuevaNecesitaOchoCaracteres() {
        assertEquals("Elegí una contraseña.", AuthValidation.newPasswordError(""))
        assertNotNull(AuthValidation.newPasswordError("1234567"))
        assertNull(AuthValidation.newPasswordError("12345678"))
    }

    @Test
    fun contrasenaNuevaNoSeRecortaComoEnElServer() {
        // 7 letras + 1 espacio = 8 caracteres: el server la acepta (no hace trim de la contraseña).
        assertNull(AuthValidation.newPasswordError("abcdefg "))
    }

    @Test
    fun confirmacionDebeCoincidir() {
        assertEquals("Repetí la contraseña.", AuthValidation.confirmPasswordError("clave-segura", ""))
        assertEquals("Las contraseñas no coinciden.", AuthValidation.confirmPasswordError("clave-segura", "clave-segurA"))
        assertNull(AuthValidation.confirmPasswordError("clave-segura", "clave-segura"))
    }
}
