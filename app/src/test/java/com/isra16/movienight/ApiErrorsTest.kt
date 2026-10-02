package com.isra16.movienight

import com.isra16.movienight.net.apiErrorMessage
import com.isra16.movienight.net.isSessionExpired
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorsTest {

    @Test
    fun sinRedMuestraMensajeDeConexionSinFiltrarLaExcepcion() {
        assertTrue(apiErrorMessage(-1, null).startsWith("No se pudo conectar"))
        assertTrue(apiErrorMessage(-1, "UnknownHostException: x").startsWith("No se pudo conectar"))
    }

    @Test
    fun error401DiceQueLaSesionVencio() {
        assertEquals("Tu sesión venció. Iniciá sesión de nuevo.", apiErrorMessage(401, "lo que sea"))
    }

    @Test
    fun error400UsaElMensajeDelServerSiHay() {
        assertEquals("Ese archivo no existe", apiErrorMessage(400, "Ese archivo no existe"))
        assertEquals("La solicitud no es válida.", apiErrorMessage(400, null))
        assertEquals("La solicitud no es válida.", apiErrorMessage(400, "  "))
    }

    @Test
    fun errorDelServerDeR2UsaSuMensaje() {
        assertEquals(
            "No se pudo consultar Cloudflare R2 (revisa credenciales/conexión).",
            apiErrorMessage(502, "No se pudo consultar Cloudflare R2 (revisa credenciales/conexión)."),
        )
        assertTrue(apiErrorMessage(502, null).startsWith("El servidor no está disponible"))
    }

    @Test
    fun otros5xxYCodigosRaros() {
        assertEquals("El servidor tuvo un problema. Intentá de nuevo en un momento.", apiErrorMessage(500, null))
        assertEquals("Respuesta inesperada del servidor (código 418).", apiErrorMessage(418, null))
    }

    @Test
    fun soloElCodigo401EsSesionVencida() {
        assertTrue(isSessionExpired(401))
        assertFalse(isSessionExpired(403))
        assertFalse(isSessionExpired(-1))
        assertFalse(isSessionExpired(200))
    }
}
