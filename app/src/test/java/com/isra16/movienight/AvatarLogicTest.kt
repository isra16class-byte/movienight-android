package com.isra16.movienight

import com.isra16.movienight.net.AVATAR_CACHE_MAX_AGE_MS
import com.isra16.movienight.net.AVATAR_LIST_RETRY_MS
import com.isra16.movienight.net.Avatar
import com.isra16.movienight.net.AvatarCacheEntry
import com.isra16.movienight.net.MSG_NO_CONNECTION
import com.isra16.movienight.net.MSG_SERVER_PROBLEM
import com.isra16.movienight.net.MSG_TOO_MANY_ATTEMPTS
import com.isra16.movienight.net.MSG_UNEXPECTED_RESPONSE
import com.isra16.movienight.net.avatarAbsoluteUrl
import com.isra16.movienight.net.avatarById
import com.isra16.movienight.net.avatarCacheFilesToDelete
import com.isra16.movienight.net.avatarCacheKey
import com.isra16.movienight.net.avatarChangeMessage
import com.isra16.movienight.net.avatarInitial
import com.isra16.movienight.net.avatarListErrorMessage
import com.isra16.movienight.net.avatarSampleSize
import com.isra16.movienight.net.shouldRetryAvatarList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarLogicTest {

    // --- avatarAbsoluteUrl ----------------------------------------------------------------------------

    @Test
    fun laUrlRelativaSeJuntaConLaDireccionSinDuplicarLaBarra() {
        val expected = "https://srv.example/avatars/koala.png?v=ab12"
        assertEquals(expected, avatarAbsoluteUrl("https://srv.example", "/avatars/koala.png?v=ab12"))
        assertEquals(expected, avatarAbsoluteUrl("https://srv.example/", "/avatars/koala.png?v=ab12"))
        assertEquals(expected, avatarAbsoluteUrl("https://srv.example", "avatars/koala.png?v=ab12"))
        assertEquals(expected, avatarAbsoluteUrl("https://srv.example/", "avatars/koala.png?v=ab12"))
        assertEquals(expected, avatarAbsoluteUrl("https://srv.example//", "//avatars/koala.png?v=ab12"))
    }

    @Test
    fun laUrlAbsolutaSeDejaIgual() {
        assertEquals("https://cdn.example/a.png?v=1", avatarAbsoluteUrl("https://srv.example", "https://cdn.example/a.png?v=1"))
        assertEquals("http://cdn.example/a.png", avatarAbsoluteUrl("https://srv.example", "http://cdn.example/a.png"))
        assertEquals("HTTPS://cdn.example/a.png", avatarAbsoluteUrl("https://srv.example", "HTTPS://cdn.example/a.png"))
        assertEquals("https://cdn.example/a.png", avatarAbsoluteUrl("", "https://cdn.example/a.png"))
    }

    @Test
    fun laUrlVaciaOEnBlancoDaNull() {
        assertNull(avatarAbsoluteUrl("https://srv.example", ""))
        assertNull(avatarAbsoluteUrl("https://srv.example", "   "))
        assertNull(avatarAbsoluteUrl("https://srv.example", "\n"))
    }

    @Test
    fun sinDireccionDeServidorUnaUrlRelativaDaNull() {
        assertNull(avatarAbsoluteUrl("", "/avatars/a.png"))
        assertNull(avatarAbsoluteUrl("  ", "/avatars/a.png"))
    }

    @Test
    fun laUrlConservaElParametroDeVersion() {
        val url = avatarAbsoluteUrl("https://srv.example", "/avatars/a.png?v=0f1e2d3c")
        assertTrue(url!!.endsWith("?v=0f1e2d3c"))
    }

    @Test
    fun laUrlRelativaSeRecortaAntesDeJuntarla() {
        assertEquals("https://srv.example/avatars/a.png", avatarAbsoluteUrl("https://srv.example", "  /avatars/a.png \n"))
    }

    // --- avatarCacheKey -------------------------------------------------------------------------------

    @Test
    fun laClaveDeCacheCoincideConLosVectoresConocidosDeFnv1a64() {
        // Vectores de prueba publicados de FNV-1a de 64 bits.
        assertEquals("cbf29ce484222325.png", avatarCacheKey(""))
        assertEquals("af63dc4c8601ec8c.png", avatarCacheKey("a"))
        assertEquals("85944171f73967e8.png", avatarCacheKey("foobar"))
    }

    @Test
    fun laClaveDeCacheEsEstableYDistintaParaOtroVersion() {
        val a = "https://srv.example/avatars/koala.png?v=aaaa"
        val b = "https://srv.example/avatars/koala.png?v=bbbb"
        assertEquals(avatarCacheKey(a), avatarCacheKey(a))
        assertNotEquals(avatarCacheKey(a), avatarCacheKey(b))
        assertNotEquals(avatarCacheKey(a), avatarCacheKey("https://srv.example/avatars/caballero.png?v=aaaa"))
    }

    @Test
    fun laClaveDeCacheEsUnNombreDeArchivoSeguro() {
        val shape = Regex("^[0-9a-f]{16}\\.png$")
        val urls = listOf(
            "",
            "https://srv.example/avatars/koala.png?v=1",
            "../../etc/passwd?v=1",
            "/avatars/a b/ñ😀.png",
            "https://srv.example/\u0000\n\\..\\x",
        )
        for (url in urls) assertTrue("clave insegura para '$url': ${avatarCacheKey(url)}", shape.matches(avatarCacheKey(url)))
    }

    // --- avatarById -----------------------------------------------------------------------------------

    private val list = listOf(
        Avatar("koala-ninja", "Koala ninja", "https://srv.example/avatars/koala.png?v=1"),
        Avatar("trex", "T-Rex", "https://srv.example/avatars/trex.png?v=1"),
    )

    @Test
    fun avatarByIdEncuentraElIdValido() {
        assertEquals(list[1], avatarById(list, "trex"))
        assertEquals(list[0], avatarById(list, "koala-ninja"))
    }

    @Test
    fun avatarByIdDaNullConIdNuloVacioODesconocido() {
        assertNull(avatarById(list, null))
        assertNull(avatarById(list, ""))
        assertNull(avatarById(list, "   "))
        assertNull(avatarById(list, "no-existe"))
        assertNull(avatarById(list, "TREX"))
        assertNull(avatarById(emptyList(), "trex"))
    }

    // --- avatarInitial --------------------------------------------------------------------------------

    @Test
    fun laInicialEsLaPrimeraLetraEnMayuscula() {
        assertEquals("A", avatarInitial("ana"))
        assertEquals("B", avatarInitial("Beto"))
        assertEquals("Ñ", avatarInitial("ñandú"))
    }

    @Test
    fun laInicialSaltaEspaciosYSimbolos() {
        assertEquals("A", avatarInitial("   ana"))
        assertEquals("A", avatarInitial("-_ *ana"))
        assertEquals("7", avatarInitial("!!!7up"))
        assertEquals("C", avatarInitial("\t\n c"))
    }

    @Test
    fun laInicialSaltaElEmojiEnteroYNoLoPartePorLaMitad() {
        assertEquals("B", avatarInitial("😀 beto"))
        assertEquals("B", avatarInitial("😀beto"))
        assertEquals("?", avatarInitial("😀"))
        assertEquals("?", avatarInitial("😀😀"))
    }

    @Test
    fun laInicialRespetaUnaLetraFueraDelPlanoBasico() {
        val script = "𝒜" // letra A en cursiva matemática: un par sustituto que es letra
        assertEquals(script, avatarInitial(script + "lice"))
        assertEquals(2, avatarInitial(script + "lice").length)
        assertEquals(script, avatarInitial("😀 " + script))
    }

    @Test
    fun laInicialDeUnNombreVacioOSinLetrasEsUnSignoDePregunta() {
        assertEquals("?", avatarInitial(""))
        assertEquals("?", avatarInitial("   "))
        assertEquals("?", avatarInitial("- - -"))
        assertEquals("?", avatarInitial("\u0000"))
    }

    // --- avatarSampleSize -----------------------------------------------------------------------------

    @Test
    fun elSampleSizeEsLaPotenciaDeDosMasGrandeQueNoBajaDelDestino() {
        assertEquals(2, avatarSampleSize(256, 256, 96))
        assertEquals(2, avatarSampleSize(256, 256, 128))
        assertEquals(1, avatarSampleSize(256, 256, 129))
        assertEquals(1, avatarSampleSize(256, 256, 256))
        assertEquals(4, avatarSampleSize(256, 256, 64))
        assertEquals(256, avatarSampleSize(256, 256, 1))
    }

    @Test
    fun elSampleSizeMiraElLadoMasChico() {
        assertEquals(2, avatarSampleSize(512, 256, 128))
        assertEquals(2, avatarSampleSize(256, 512, 128))
        assertEquals(4, avatarSampleSize(1024, 512, 100))
    }

    @Test
    fun elSampleSizeEsUnoConMedidasODestinoInvalidos() {
        assertEquals(1, avatarSampleSize(0, 256, 128))
        assertEquals(1, avatarSampleSize(256, 0, 128))
        assertEquals(1, avatarSampleSize(0, 0, 0))
        assertEquals(1, avatarSampleSize(256, 256, 0))
        assertEquals(1, avatarSampleSize(256, 256, -5))
        assertEquals(1, avatarSampleSize(-10, 256, 128))
        assertEquals(1, avatarSampleSize(100, 100, 128))
    }

    // --- Mensajes ---------------------------------------------------------------------------------------

    private val voseo = Regex(
        "(?U)\\b(vos|sos|tenés|podés|querés|intentá|reintentá|revisá|iniciá|esperá|elegí|volvé|probá|andá|avisá|tocá|cerrá|usá)\\b",
        RegexOption.IGNORE_CASE,
    )

    private fun assertNeutral(origin: String, code: Int, message: String) {
        assertFalse("$origin($code) lleva dígitos: $message", message.any { it.isDigit() })
        assertFalse("$origin($code) tiene voseo: $message", voseo.containsMatchIn(message))
        assertTrue("$origin($code) mide ${message.length}: $message", message.length in 1..100)
    }

    @Test
    fun avatarChangeMessageDaElTextoDeCadaCodigo() {
        assertEquals(MSG_NO_CONNECTION, avatarChangeMessage(-1))
        assertEquals("Tu sesión terminó. Inicia sesión de nuevo.", avatarChangeMessage(401))
        assertEquals("Ese avatar ya no está disponible. Elige otro.", avatarChangeMessage(400))
        assertEquals("No se pudo guardar el avatar. Inténtalo más tarde.", avatarChangeMessage(404))
        assertEquals(MSG_TOO_MANY_ATTEMPTS, avatarChangeMessage(429))
        assertEquals(MSG_SERVER_PROBLEM, avatarChangeMessage(500))
        assertEquals(MSG_SERVER_PROBLEM, avatarChangeMessage(503))
        assertEquals(MSG_SERVER_PROBLEM, avatarChangeMessage(599))
        assertEquals(MSG_UNEXPECTED_RESPONSE, avatarChangeMessage(418))
        assertEquals(MSG_UNEXPECTED_RESPONSE, avatarChangeMessage(403))
        assertEquals(MSG_UNEXPECTED_RESPONSE, avatarChangeMessage(200))
    }

    @Test
    fun ningunMensajeDeAvatarLlevaCodigosNiVoseo() {
        for (code in listOf(-1, 0, 200, 400, 401, 403, 404, 409, 418, 429, 500, 502, 503, 599)) {
            assertNeutral("avatarChangeMessage", code, avatarChangeMessage(code))
            assertNeutral("avatarListErrorMessage", code, avatarListErrorMessage(code))
        }
    }

    @Test
    fun avatarListErrorMessageDistingueRedServidorYElResto() {
        assertEquals(MSG_NO_CONNECTION, avatarListErrorMessage(-1))
        assertEquals(MSG_TOO_MANY_ATTEMPTS, avatarListErrorMessage(429))
        assertEquals(MSG_SERVER_PROBLEM, avatarListErrorMessage(502))
        assertEquals(MSG_UNEXPECTED_RESPONSE, avatarListErrorMessage(404))
        assertEquals(MSG_UNEXPECTED_RESPONSE, avatarListErrorMessage(200))
    }

    // --- Reintento de la lista --------------------------------------------------------------------------

    @Test
    fun laListaSePuedePedirDeNuevoSoloPasadosTreintaSegundosDelFallo() {
        val failedAt = 1_000_000L
        assertTrue(shouldRetryAvatarList(null, failedAt))
        assertFalse(shouldRetryAvatarList(failedAt, failedAt))
        assertFalse(shouldRetryAvatarList(failedAt, failedAt + 10_000))
        assertFalse(shouldRetryAvatarList(failedAt, failedAt + AVATAR_LIST_RETRY_MS - 1))
        assertTrue(shouldRetryAvatarList(failedAt, failedAt + AVATAR_LIST_RETRY_MS))
        assertTrue(shouldRetryAvatarList(failedAt, failedAt + 120_000))
    }

    @Test
    fun conElRelojRetrocedidoLaListaSePuedePedirDeNuevo() {
        assertTrue(shouldRetryAvatarList(1_000_000L, 500_000L))
    }

    // --- Limpieza de la caché de disco ------------------------------------------------------------------

    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private fun entry(name: String, size: Long, ageDays: Double) =
        AvatarCacheEntry(name, size, now - (ageDays * day).toLong())

    @Test
    fun laCacheBorraLoQueTieneMasDeTreintaDias() {
        val entries = listOf(entry("viejo.png", 1_000, 31.0), entry("nuevo.png", 1_000, 1.0), entry("medio.png", 1_000, 29.0))
        assertEquals(listOf("viejo.png"), avatarCacheFilesToDelete(entries, now))
    }

    @Test
    fun laCacheNoBorraLoQueTieneExactamenteTreintaDias() {
        val entries = listOf(AvatarCacheEntry("justo.png", 1_000, now - AVATAR_CACHE_MAX_AGE_MS))
        assertEquals(emptyList<String>(), avatarCacheFilesToDelete(entries, now))
    }

    @Test
    fun laCacheQuitaPrimeroLosMasViejosSiPasaDelTope() {
        val entries = listOf(
            entry("c.png", 400, 1.0),
            entry("a.png", 400, 20.0),
            entry("b.png", 400, 10.0),
        )
        // Tope de 1000: suman 1200, sobra el más viejo (a.png) y quedan 800.
        assertEquals(listOf("a.png"), avatarCacheFilesToDelete(entries, now, maxTotalBytes = 1_000))
        // Tope de 500: se van a.png y b.png y queda c.png con 400.
        assertEquals(listOf("a.png", "b.png"), avatarCacheFilesToDelete(entries, now, maxTotalBytes = 500))
    }

    @Test
    fun laCacheNoBorraNadaSiCabeJustoEnElTope() {
        val entries = listOf(entry("a.png", 500, 5.0), entry("b.png", 500, 2.0))
        assertEquals(emptyList<String>(), avatarCacheFilesToDelete(entries, now, maxTotalBytes = 1_000))
    }

    @Test
    fun laCacheNoCuentaParaElTopeLoQueYaSeBorraPorViejo() {
        val entries = listOf(entry("viejo.png", 900, 40.0), entry("a.png", 600, 3.0), entry("b.png", 600, 2.0))
        // Sin el viejo quedan 1200 > 1000: se va también a.png (el más viejo de los vivos).
        assertEquals(listOf("viejo.png", "a.png"), avatarCacheFilesToDelete(entries, now, maxTotalBytes = 1_000))
    }

    @Test
    fun laCacheVaciaNoBorraNada() {
        assertEquals(emptyList<String>(), avatarCacheFilesToDelete(emptyList(), now))
    }
}
