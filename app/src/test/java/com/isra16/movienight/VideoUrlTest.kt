package com.isra16.movienight

import com.isra16.movienight.net.playbackErrorMessage
import com.isra16.movienight.net.resolveVideoUrl
import com.isra16.movienight.net.shouldLoadVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoUrlTest {

    private val base = "https://sala.ejemplo.uk"

    // --- resolveVideoUrl: video en disco del server (`/uploads/...`) ---

    @Test
    fun rutaDeUploadsSeAntepoeLaUrlDelServidor() {
        assertEquals(
            "https://sala.ejemplo.uk/uploads/ab12cd34__peli.mp4",
            resolveVideoUrl(base, "/uploads/ab12cd34__peli.mp4"),
        )
    }

    @Test
    fun laBarraFinalDeLaUrlDelServidorNoDuplicaBarras() {
        assertEquals(
            "https://sala.ejemplo.uk/uploads/a.mp4",
            resolveVideoUrl("https://sala.ejemplo.uk/", "/uploads/a.mp4"),
        )
    }

    @Test
    fun servidorDeDebugPorHttpConPuerto() {
        assertEquals("http://10.0.2.2:3000/uploads/a.mp4", resolveVideoUrl("http://10.0.2.2:3000", "/uploads/a.mp4"))
    }

    @Test
    fun rutaSinBarraInicialSeUneConUnaBarra() {
        assertEquals("https://sala.ejemplo.uk/uploads/a.mp4", resolveVideoUrl(base, "uploads/a.mp4"))
    }

    @Test
    fun losEspaciosDelNombreSeCodifican() {
        // El server permite espacios en los nombres de archivo ([a-zA-Z0-9 _-]).
        assertEquals(
            "https://sala.ejemplo.uk/uploads/ab12cd34__Mi%20Peli%20Favorita.mp4",
            resolveVideoUrl(base, "/uploads/ab12cd34__Mi Peli Favorita.mp4"),
        )
    }

    // --- resolveVideoUrl: video en Cloudflare R2 (URL absoluta) ---

    @Test
    fun urlAbsolutaDeR2SeUsaTalCual() {
        assertEquals(
            "https://pub-abc123.r2.dev/ab12cd34__peli.mp4",
            resolveVideoUrl(base, "https://pub-abc123.r2.dev/ab12cd34__peli.mp4"),
        )
    }

    @Test
    fun urlAbsolutaDeR2TambienCodificaEspacios() {
        assertEquals(
            "https://videos.ejemplo.uk/ab12cd34__Mi%20Peli.mp4",
            resolveVideoUrl(base, "https://videos.ejemplo.uk/ab12cd34__Mi Peli.mp4"),
        )
    }

    @Test
    fun elEsquemaSeAceptaEnMayuscula() {
        assertEquals("HTTPS://videos.ejemplo.uk/a.mp4", resolveVideoUrl(base, "HTTPS://videos.ejemplo.uk/a.mp4"))
    }

    @Test
    fun laQueryDeUnaUrlFirmadaSeConserva() {
        assertEquals(
            "https://videos.ejemplo.uk/a.mp4?X-Amz-Signature=ab12&x=1%202",
            resolveVideoUrl(base, "https://videos.ejemplo.uk/a.mp4?X-Amz-Signature=ab12&x=1 2"),
        )
    }

    // --- resolveVideoUrl: codificación ---

    @Test
    fun loYaCodificadoNoSeCodificaDeNuevo() {
        assertEquals(
            "https://sala.ejemplo.uk/uploads/Mi%20Peli.mp4",
            resolveVideoUrl(base, "/uploads/Mi%20Peli.mp4"),
        )
    }

    @Test
    fun unPorcentajeSueltoSeCodifica() {
        assertEquals("https://sala.ejemplo.uk/uploads/100%25.mp4", resolveVideoUrl(base, "/uploads/100%.mp4"))
        // "%2" al final y "%zz" no son una secuencia válida.
        assertEquals("https://sala.ejemplo.uk/uploads/a%252", resolveVideoUrl(base, "/uploads/a%2"))
        assertEquals("https://sala.ejemplo.uk/uploads/a%25zz", resolveVideoUrl(base, "/uploads/a%zz"))
    }

    @Test
    fun caracteresNoAsciiSeCodificanEnUtf8() {
        assertEquals("https://sala.ejemplo.uk/uploads/a%C3%B1o.mp4", resolveVideoUrl(base, "/uploads/año.mp4"))
        // Un emoji ocupa dos `Char` (par sustituto) y 4 bytes en UTF-8.
        assertEquals("https://sala.ejemplo.uk/uploads/%F0%9F%8E%AC.mp4", resolveVideoUrl(base, "/uploads/\uD83C\uDFAC.mp4"))
    }

    @Test
    fun guionesBajosYGuionesNoSeTocan() {
        assertEquals(
            "https://sala.ejemplo.uk/uploads/ab12cd34__mi-peli_2.mp4",
            resolveVideoUrl(base, "/uploads/ab12cd34__mi-peli_2.mp4"),
        )
    }

    // --- resolveVideoUrl: lo que no se acepta ---

    @Test
    fun sinVideoNoHayUrl() {
        assertNull(resolveVideoUrl(base, null))
        assertNull(resolveVideoUrl(base, ""))
        assertNull(resolveVideoUrl(base, "   "))
    }

    @Test
    fun esquemasQueNoSonHttpSeRechazan() {
        assertNull(resolveVideoUrl(base, "file:///sdcard/a.mp4"))
        assertNull(resolveVideoUrl(base, "content://media/external/video/1"))
        assertNull(resolveVideoUrl(base, "javascript:alert(1)"))
        assertNull(resolveVideoUrl(base, "ftp://ejemplo.uk/a.mp4"))
    }

    @Test
    fun rutaSinEsquemaNiHostSeRechaza() {
        assertNull(resolveVideoUrl(base, "//otro-host.uk/a.mp4"))
    }

    @Test
    fun rutaRelativaSinUrlDeServidorNoSePuedeResolver() {
        assertNull(resolveVideoUrl("", "/uploads/a.mp4"))
        assertNull(resolveVideoUrl("  ", "/uploads/a.mp4"))
    }

    @Test
    fun conUrlAbsolutaNoHaceFaltaLaUrlDelServidor() {
        assertEquals("https://videos.ejemplo.uk/a.mp4", resolveVideoUrl("", "https://videos.ejemplo.uk/a.mp4"))
    }

    // --- shouldLoadVideo ---

    @Test
    fun primeraCargaDelVideo() {
        assertTrue(shouldLoadVideo(loadedUrl = null, newUrl = "https://x/a.mp4", force = false))
    }

    @Test
    fun reconectarConElMismoVideoNoLoRecarga() {
        // `room-data` llega otra vez tras un corte de red: recargar mandaría el video al segundo 0.
        assertFalse(shouldLoadVideo(loadedUrl = "https://x/a.mp4", newUrl = "https://x/a.mp4", force = false))
    }

    @Test
    fun roomDataConOtroVideoSiRecarga() {
        assertTrue(shouldLoadVideo(loadedUrl = "https://x/a.mp4", newUrl = "https://x/b.mp4", force = false))
    }

    @Test
    fun cambiarLaCintaRecargaAunqueSeaLaMismaUrl() {
        // `video-changed`: el host volvió a elegir el mismo video y el server lo manda al segundo 0.
        assertTrue(shouldLoadVideo(loadedUrl = "https://x/a.mp4", newUrl = "https://x/a.mp4", force = true))
    }

    @Test
    fun quitarLaCintaSoloSiHabiaUna() {
        assertTrue(shouldLoadVideo(loadedUrl = "https://x/a.mp4", newUrl = null, force = false))
        assertTrue(shouldLoadVideo(loadedUrl = "https://x/a.mp4", newUrl = null, force = true))
        assertFalse(shouldLoadVideo(loadedUrl = null, newUrl = null, force = false))
        assertFalse(shouldLoadVideo(loadedUrl = null, newUrl = null, force = true))
    }

    // --- playbackErrorMessage ---

    @Test
    fun erroresDeRedPidenRevisarLaConexion() {
        assertTrue("conexión" in playbackErrorMessage(2001)) // IO_NETWORK_CONNECTION_FAILED
        assertTrue("conexión" in playbackErrorMessage(2002)) // IO_NETWORK_CONNECTION_TIMEOUT
    }

    @Test
    fun videoInexistenteEnElServidor() {
        assertTrue("no encontró" in playbackErrorMessage(2004)) // IO_BAD_HTTP_STATUS (ej. 404)
        assertTrue("no encontró" in playbackErrorMessage(2005)) // IO_FILE_NOT_FOUND
    }

    @Test
    fun httpSinCifrarBloqueadoPorLaApp() {
        assertTrue("http" in playbackErrorMessage(2007)) // IO_CLEARTEXT_NOT_PERMITTED
    }

    @Test
    fun archivoDanadoODeFormatoIlegible() {
        for (code in 3001..3004) assertTrue("dañado" in playbackErrorMessage(code))
    }

    @Test
    fun elTelefonoNoPuedeDecodificar() {
        for (code in 4001..4006) assertTrue("teléfono" in playbackErrorMessage(code))
    }

    @Test
    fun cualquierOtroErrorTieneMensajeGenerico() {
        val generic = playbackErrorMessage(1000)
        assertEquals("No se pudo reproducir el video.", generic)
        for (code in listOf(0, 2000, 2003, 2006, 3000, 3005, 4000, 4007, 5001, 6000, 9999, -1)) {
            assertEquals("código $code", generic, playbackErrorMessage(code))
        }
        assertNotEquals(generic, playbackErrorMessage(2001))
    }

    @Test
    fun urlDelSubtituloEnDisco() {
        assertEquals(
            "https://sala.ejemplo.uk/uploads/ab12cd34.vtt",
            com.isra16.movienight.net.resolveSubtitleUrl("https://sala.ejemplo.uk/", "/uploads/ab12cd34.vtt"),
        )
        assertNull(com.isra16.movienight.net.resolveSubtitleUrl("https://sala.ejemplo.uk", null))
        assertNull(com.isra16.movienight.net.resolveSubtitleUrl("https://sala.ejemplo.uk", "file:///x.vtt"))
    }
}
