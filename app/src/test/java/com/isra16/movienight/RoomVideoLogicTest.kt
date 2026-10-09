package com.isra16.movienight

import com.isra16.movienight.net.ChangeVideoResult
import com.isra16.movienight.net.CreateRoomResult
import com.isra16.movienight.net.FollowUpOutcome
import com.isra16.movienight.net.RoomVideoFailure
import com.isra16.movienight.net.changeVideoBody
import com.isra16.movienight.net.changeVideoPath
import com.isra16.movienight.net.createRoomBody
import com.isra16.movienight.net.interpretChangeVideo
import com.isra16.movienight.net.interpretCreateRoom
import com.isra16.movienight.net.toFollowUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fase 4B: cuerpos y respuestas de `/create-room-from-upload` y `/room/:id/change-video-from-upload`. */
class RoomVideoLogicTest {

    // Textos reales del server (server.js, rama plan-produccion).
    private val noExiste = """{"error":"Ese archivo no existe"}"""
    private val noPasoValidacion =
        """{"error":"El video no pasó la validación de contenido: el archivo no parece un video"}"""
    private val r2Caido =
        """{"error":"No se pudo consultar Cloudflare R2 (revisa credenciales/conexión)."}"""

    private fun failureOf(result: CreateRoomResult): RoomVideoFailure = (result as CreateRoomResult.Failed).failure
    private fun failureOf(result: ChangeVideoResult): RoomVideoFailure = (result as ChangeVideoResult.Failed).failure

    // --- Cuerpos --------------------------------------------------------------------------------------

    @Test
    fun crearSalaSinContrasenaNoMandaElCampo() {
        val body = createRoomBody("ab12cd34__peli.mp4", "")
        assertEquals("ab12cd34__peli.mp4", body.getString("filename"))
        assertFalse(body.has("password"))
        assertFalse(createRoomBody("x.mp4", "   ").has("password"))
    }

    @Test
    fun crearSalaConContrasenaLaMandaRecortada() {
        assertEquals("clave", createRoomBody("x.mp4", "  clave ").getString("password"))
    }

    @Test
    fun cambiarVideoManda_hostTokenSoloSiHay() {
        assertEquals("tok", changeVideoBody("x.mp4", "tok").getString("hostToken"))
        assertFalse(changeVideoBody("x.mp4", null).has("hostToken"))
        assertFalse(changeVideoBody("x.mp4", "").has("hostToken"))
        assertEquals("x.mp4", changeVideoBody("x.mp4", "tok").getString("filename"))
    }

    @Test
    fun rutaDeCambiarVideo() {
        assertEquals("/room/a1b2c3/change-video-from-upload", changeVideoPath("a1b2c3"))
    }

    // --- Crear sala -----------------------------------------------------------------------------------

    @Test
    fun crearSalaOk() {
        val result = interpretCreateRoom(200, """{"roomId":"a1b2c3","hostToken":"zzz"}""")
        assertEquals(CreateRoomResult.Ok("a1b2c3"), result)
    }

    @Test
    fun crearSalaConRespuestaRaraNoEsExito() {
        for (body in listOf("""{"hostToken":"zzz"}""", "<html>Cloudflare</html>", "")) {
            val failure = failureOf(interpretCreateRoom(200, body))
            assertTrue(failure.message, failure.message.contains("inesperado"))
            assertTrue(failure.canRetry)
            assertFalse(failure.libraryChanged)
        }
    }

    @Test
    fun crearSalaConVideoQueNoPasoLaValidacion() {
        val failure = failureOf(interpretCreateRoom(400, noPasoValidacion))
        assertTrue(failure.message, failure.message.contains("validación de contenido"))
        assertFalse("reintentar con el mismo archivo no sirve", failure.canRetry)
        assertTrue("el server lo borró de la biblioteca", failure.libraryChanged)
    }

    @Test
    fun crearSalaConArchivoQueYaNoExiste() {
        val failure = failureOf(interpretCreateRoom(400, noExiste))
        assertEquals("Ese archivo no existe", failure.message)
        assertFalse(failure.canRetry)
        assertTrue(failure.libraryChanged)
    }

    @Test
    fun crearSalaConSesionVencida() {
        val failure = failureOf(interpretCreateRoom(401, """{"error":"Contraseña incorrecta."}"""))
        assertTrue(failure.message, failure.message.contains("sesión venció"))
        assertFalse(failure.canRetry)
        assertFalse(failure.libraryChanged)
    }

    @Test
    fun crearSalaConFallosDeRedOServidorSePuedeReintentar() {
        assertTrue(failureOf(interpretCreateRoom(-1, "UnknownHostException: x")).canRetry)
        assertTrue(failureOf(interpretCreateRoom(429, """{"error":"Demasiados intentos"}""")).canRetry)
        val r2 = failureOf(interpretCreateRoom(502, r2Caido))
        assertTrue(r2.canRetry)
        assertTrue(r2.message, r2.message.contains("Cloudflare R2"))
        assertTrue(failureOf(interpretCreateRoom(503, "<html>mantenimiento</html>")).canRetry)
    }

    // --- Cambiar el video de una sala -----------------------------------------------------------------

    @Test
    fun cambiarVideoOk() {
        assertEquals(ChangeVideoResult.Ok, interpretChangeVideo(200, """{"ok":true}"""))
        assertEquals(ChangeVideoResult.Ok, interpretChangeVideo(200, ""))
    }

    @Test
    fun cambiarVideoSinSerElDuenoExplicaPorQue() {
        val failure = failureOf(interpretChangeVideo(403, """{"error":"No autorizado"}"""))
        assertTrue(failure.message, failure.message.contains("creó la sala"))
        assertTrue("explica que ser host no alcanza", failure.message.contains("host"))
        assertFalse(failure.canRetry)
        assertFalse(failure.libraryChanged)
    }

    @Test
    fun cambiarVideoDeUnaSalaQueSeCerro() {
        val failure = failureOf(interpretChangeVideo(404, """{"error":"Sala no existe"}"""))
        assertTrue(failure.message, failure.message.contains("ya no existe"))
        assertFalse(failure.canRetry)
    }

    @Test
    fun cambiarVideoConArchivoInvalidoRecargaLaBiblioteca() {
        val noValido = failureOf(interpretChangeVideo(400, noPasoValidacion))
        assertTrue(noValido.message, noValido.message.contains("validación de contenido"))
        assertTrue(noValido.libraryChanged)
        assertFalse(noValido.canRetry)
        val noExiste = failureOf(interpretChangeVideo(400, noExiste))
        assertEquals("Ese archivo no existe", noExiste.message)
        assertTrue(noExiste.libraryChanged)
    }

    @Test
    fun cambiarVideoConSesionVencidaOFallosTransitorios() {
        assertFalse(failureOf(interpretChangeVideo(401, "{}")).canRetry)
        assertTrue(failureOf(interpretChangeVideo(-1, "SocketTimeoutException")).canRetry)
        assertTrue(failureOf(interpretChangeVideo(502, r2Caido)).canRetry)
        assertTrue(failureOf(interpretChangeVideo(429, "")).canRetry)
    }

    // --- Puente hacia el paso siguiente de la subida --------------------------------------------------

    @Test
    fun puenteDeCrearSala() {
        assertEquals(FollowUpOutcome.Done("a1b2c3"), CreateRoomResult.Ok("a1b2c3").toFollowUp())
        val failure = RoomVideoFailure("x", canRetry = true)
        assertEquals(FollowUpOutcome.Failed(failure), CreateRoomResult.Failed(failure).toFollowUp())
    }

    @Test
    fun puenteDeCambiarVideo() {
        assertEquals(FollowUpOutcome.Done(null), ChangeVideoResult.Ok.toFollowUp())
        val failure = RoomVideoFailure("x", canRetry = false, libraryChanged = true)
        assertEquals(FollowUpOutcome.Failed(failure), ChangeVideoResult.Failed(failure).toFollowUp())
    }
}
