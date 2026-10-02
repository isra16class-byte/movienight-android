package com.isra16.movienight

import com.isra16.movienight.net.authErrorMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthErrorsTest {

    @Test
    fun sinRedMuestraMensajeDeConexion() {
        assertTrue(authErrorMessage(-1, null).startsWith("No se pudo conectar"))
        // El texto "de afuera" (excepción de red) nunca se le muestra a la persona.
        assertTrue(authErrorMessage(-1, "UnknownHostException: x").startsWith("No se pudo conectar"))
    }

    @Test
    fun error400UsaElMensajeEspecificoDelServer() {
        assertEquals(
            "La contraseña necesita al menos 8 caracteres.",
            authErrorMessage(400, "La contraseña necesita al menos 8 caracteres."),
        )
    }

    @Test
    fun error400SinMensajeUsaUnoGenerico() {
        assertEquals("Los datos no son válidos. Revisá el email y la contraseña.", authErrorMessage(400, null))
        assertEquals("Los datos no son válidos. Revisá el email y la contraseña.", authErrorMessage(400, "   "))
    }

    @Test
    fun error401EsSiempreElMismoMensajeGenerico() {
        assertEquals("Email o contraseña incorrectos.", authErrorMessage(401, "lo que sea"))
    }

    @Test
    fun error404ExplicaQueFaltanLasCuentas() {
        assertTrue(authErrorMessage(404, null).contains("cuentas habilitadas"))
    }

    @Test
    fun error409DiceQueElEmailYaExiste() {
        assertEquals("Ya existe una cuenta con ese email.", authErrorMessage(409, null))
    }

    @Test
    fun error429PrefiereElMensajeConLosMinutos() {
        val server = "Demasiados intentos fallidos. Esperá 12 min y volvé a intentar."
        assertEquals(server, authErrorMessage(429, server))
        assertTrue(authErrorMessage(429, null).startsWith("Demasiados intentos"))
    }

    @Test
    fun errores502a504SonServidorNoDisponibleAunqueElCuerpoSeaHtml() {
        for (code in listOf(502, 503, 504)) {
            assertTrue(authErrorMessage(code, "<html>error de cloudflare</html>").contains("no está disponible"))
        }
    }

    @Test
    fun otros5xxUsanElMensajeDelServerOUnoGenerico() {
        assertEquals("No se pudo iniciar sesión. Intentá de nuevo en un momento.",
            authErrorMessage(500, "No se pudo iniciar sesión. Intentá de nuevo en un momento."))
        assertTrue(authErrorMessage(500, null).startsWith("El servidor tuvo un problema"))
    }

    @Test
    fun codigoDesconocidoIncluyeElCodigo() {
        assertEquals("Respuesta inesperada del servidor (código 418).", authErrorMessage(418, null))
    }
}
