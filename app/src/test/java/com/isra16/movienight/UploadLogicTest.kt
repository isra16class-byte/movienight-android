package com.isra16.movienight

import com.isra16.movienight.net.MAX_SIMPLE_PUT_BYTES
import com.isra16.movienight.net.MAX_SIMPLE_PUT_LABEL
import com.isra16.movienight.net.PresignResult
import com.isra16.movienight.net.PutOutcome
import com.isra16.movienight.net.SourceChangedException
import com.isra16.movienight.net.UPLOAD_VIDEO_EXTENSIONS
import com.isra16.movienight.net.UploadState
import com.isra16.movienight.net.contentTypeFor
import com.isra16.movienight.net.copyStreamWithProgress
import com.isra16.movienight.net.failureFromPut
import com.isra16.movienight.net.interpretPresign
import com.isra16.movienight.net.isHttpsUrl
import com.isra16.movienight.net.uploadProgressFraction
import com.isra16.movienight.net.progressLabel
import com.isra16.movienight.net.r2ErrorCode
import com.isra16.movienight.net.resolveVideoFileName
import com.isra16.movienight.net.shouldReportProgress
import com.isra16.movienight.net.uploadFilename
import com.isra16.movienight.net.validateUpload
import com.isra16.movienight.net.videoExtensionOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class UploadLogicTest {

    // --- extensión, tipo y nombre --------------------------------------------------------------

    @Test
    fun extensionSoloAceptaLosFormatosQueElServerLista() {
        // Mismo conjunto que VIDEO_EXTENSIONS de server.js.
        assertEquals(listOf(".mp4", ".mkv", ".mov", ".webm", ".avi", ".m4v"), UPLOAD_VIDEO_EXTENSIONS)
        assertEquals(".mp4", videoExtensionOf("Peli.MP4"))
        assertEquals(".mkv", videoExtensionOf("  serie.s01e01.mkv "))
        assertNull(videoExtensionOf("notas.txt"))
        assertNull(videoExtensionOf("video.3gp"))
        assertNull(videoExtensionOf("sinextension"))
        // Archivo oculto: path.extname('.mp4') del server da '', la key quedaría sin extensión y no se listaría.
        assertNull(videoExtensionOf(".mp4"))
    }

    @Test
    fun contentTypeSegunExtension() {
        assertEquals("video/mp4", contentTypeFor("a.mp4"))
        assertEquals("video/x-matroska", contentTypeFor("a.MKV"))
        assertEquals("video/quicktime", contentTypeFor("a.mov"))
        assertEquals("video/webm", contentTypeFor("a.webm"))
        assertEquals("video/x-msvideo", contentTypeFor("a.avi"))
        assertEquals("video/x-m4v", contentTypeFor("a.m4v"))
        assertEquals("application/octet-stream", contentTypeFor("a.txt"))
    }

    @Test
    fun nombreConRespaldoDelTipoMime() {
        assertEquals("Peli.mkv", resolveVideoFileName("Peli.mkv", "application/octet-stream"))
        assertEquals("Video 2024.mp4", resolveVideoFileName("Video 2024", "video/mp4"))
        assertEquals("video.webm", resolveVideoFileName(null, "video/webm"))
        assertEquals("x", resolveVideoFileName("x", "application/pdf"))
        assertEquals("video", resolveVideoFileName(null, null))
    }

    /** Réplica de `makeObjectKey` de lib/r2.js (sin el prefijo random), para ver qué key quedaría en el bucket. */
    private fun serverKeySuffix(originalName: String): String {
        val dot = originalName.lastIndexOf('.')
        val ext = if (dot > 0) originalName.substring(dot) else ""
        val base = originalName.substring(0, originalName.length - ext.length)
        val safeBase = base.replace(Regex("[^a-zA-Z0-9 _-]"), "").trim().take(80).ifEmpty { "video" }
        return safeBase + ext
    }

    @Test
    fun elNombreSinTildesYConExtensionEnMinusculas() {
        assertEquals("Mi Pelicula (2024).mp4", uploadFilename("Mi Película (2024).MP4"))
        assertEquals("Nandu.mov", uploadFilename("Ñandú.mov"))
        assertEquals("a.txt", uploadFilename(" a.txt ")) // no es video: solo se recorta
        // Lo que el server genera con el nombre original (verificado con lib/r2.js: "Mi Pelcula 2024.MP4") ...
        assertEquals("Mi Pelcula 2024.MP4", serverKeySuffix("Mi Película (2024).MP4"))
        // ... y con el nombre normalizado de la app: sin perder letras, y con extensión que la biblioteca lista.
        val key = serverKeySuffix(uploadFilename("Mi Película (2024).MP4"))
        assertEquals("Mi Pelicula 2024.mp4", key)
        assertTrue(key.substring(key.lastIndexOf('.')) in UPLOAD_VIDEO_EXTENSIONS)
    }

    // --- validación antes de subir --------------------------------------------------------------

    @Test
    fun validacionAceptaUnVideoNormal() {
        assertNull(validateUpload("peli.mp4", 1_572_864_000L))
        assertNull(validateUpload("PELI.MKV", 1L))
    }

    @Test
    fun validacionRechazaTipoDeArchivo() {
        val msg = validateUpload("documento.pdf", 1000L)
        assertNotNull(msg)
        assertTrue(msg!!, msg.contains("MP4") && msg.contains("MKV") && msg.contains("M4V"))
    }

    @Test
    fun validacionRechazaVacioOTamanoDesconocido() {
        assertNotNull(validateUpload("peli.mp4", 0L))
        assertNotNull(validateUpload("peli.mp4", -1L))
    }

    @Test
    fun elLimiteEsElDelPutSimpleDeR2() {
        // 5 GiB menos 5 MiB (4,995 GiB), según la doc de límites de Cloudflare R2.
        assertEquals(5_363_466_240L, MAX_SIMPLE_PUT_BYTES)
        assertEquals("4,99 GB", MAX_SIMPLE_PUT_LABEL)
        val gib = MAX_SIMPLE_PUT_BYTES / (1024.0 * 1024 * 1024)
        assertEquals(4.99, Math.floor(gib * 100) / 100, 0.0)

        assertNull(validateUpload("peli.mp4", MAX_SIMPLE_PUT_BYTES))
        val msg = validateUpload("peli.mp4", MAX_SIMPLE_PUT_BYTES + 1)
        assertNotNull(msg)
        assertTrue(msg!!, msg.contains("4,99 GB"))
        // Un archivo de exactamente 5 GiB (entre el límite real y los "5 GB" redondos) tiene que rechazarse.
        assertNotNull(validateUpload("peli.mp4", 5L * 1024 * 1024 * 1024))
    }

    @Test
    fun laExtensionSeValidaAntesQueElTamano() {
        val msg = validateUpload("grande.iso", MAX_SIMPLE_PUT_BYTES + 1)!!
        assertTrue(msg, msg.contains("tipo de archivo"))
    }

    // --- respuesta de /api/uploads/presign -------------------------------------------------------

    // Forma real: key + uploadUrl + expiresIn (server.js) y URL generada con lib/r2.js (host virtual-hosted,
    // espacios codificados, X-Amz-SignedHeaders=host).
    private val presignBody = """
        {"key":"6ada04f9__Mi Pelicula 2024.mp4",
         "uploadUrl":"https://movienight.abc123.r2.cloudflarestorage.com/6ada04f9__Mi%20Pelicula%202024.mp4?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Content-Sha256=UNSIGNED-PAYLOAD&X-Amz-Credential=AKIAFAKE%2F20261008%2Fauto%2Fs3%2Faws4_request&X-Amz-Date=20261008T214448Z&X-Amz-Expires=21600&X-Amz-Signature=3116010309a6&X-Amz-SignedHeaders=host&x-id=PutObject",
         "expiresIn":21600}
    """.trimIndent()

    @Test
    fun presignOkConLaFormaRealDelServer() {
        val result = interpretPresign(200, presignBody)
        assertTrue(result is PresignResult.Ok)
        val upload = (result as PresignResult.Ok).upload
        assertEquals("6ada04f9__Mi Pelicula 2024.mp4", upload.key)
        assertEquals(21600L, upload.expiresInSeconds)
        // La URL se usa tal cual: sigue con los espacios y las barras codificados.
        assertTrue(upload.uploadUrl.contains("Mi%20Pelicula%202024.mp4"))
        assertTrue(upload.uploadUrl.contains("AKIAFAKE%2F20261008"))
    }

    @Test
    fun presignInesperadoSePuedeReintentar() {
        for (body in listOf("<html>502 Bad Gateway</html>", "{}", """{"key":"x"}""", """{"key":"","uploadUrl":"https://a.b/c"}""")) {
            val r = interpretPresign(200, body)
            assertTrue(body, r is PresignResult.Failed)
            assertTrue(body, (r as PresignResult.Failed).failure.canRetry)
        }
    }

    @Test
    fun presignRechazaUrlQueNoEsHttps() {
        val http = """{"key":"k.mp4","uploadUrl":"http://bucket.example.com/k.mp4?X-Amz-Signature=1"}"""
        assertTrue(interpretPresign(200, http) is PresignResult.Failed)
        assertTrue(isHttpsUrl("https://a.r2.cloudflarestorage.com/k%20x.mp4?X-Amz-Expires=60"))
        assertFalse(isHttpsUrl("https://"))
        assertFalse(isHttpsUrl("ftp://a.b/c"))
        assertFalse(isHttpsUrl("no es una url"))
        assertFalse(isHttpsUrl(""))
    }

    @Test
    fun presignSinR2NoOfreceReintento() {
        // 404: "La subida directa no está disponible: este servidor no tiene Cloudflare R2 configurado."
        val f = (interpretPresign(404, """{"error":"La subida directa no está disponible"}""") as PresignResult.Failed).failure
        assertFalse(f.canRetry)
        assertTrue(f.message, f.message.contains("R2"))
    }

    @Test
    fun presignConLimiteDeBibliotecaMuestraElMensajeDelServer() {
        val body = """{"error":"La biblioteca llegó al límite de 10 video(s). Borrá alguno desde /library.html antes de subir uno nuevo."}"""
        val f = (interpretPresign(413, body) as PresignResult.Failed).failure
        assertTrue(f.message, f.message.startsWith("La biblioteca llegó al límite de 10 video(s)"))
        assertFalse(f.canRetry)
        val sinMensaje = (interpretPresign(413, "") as PresignResult.Failed).failure
        assertTrue(sinMensaje.message.contains("límite"))
    }

    @Test
    fun presignSesionVencidaYErroresTransitorios() {
        val s401 = (interpretPresign(401, """{"error":"Contraseña incorrecta."}""") as PresignResult.Failed).failure
        assertTrue(s401.message, s401.message.contains("sesión venció"))
        assertFalse(s401.canRetry)
        assertTrue((interpretPresign(-1, "SocketTimeoutException") as PresignResult.Failed).failure.canRetry)
        assertTrue((interpretPresign(502, """{"error":"No se pudo preparar la subida directa a Cloudflare R2"}""") as PresignResult.Failed).failure.canRetry)
        assertTrue((interpretPresign(429, "") as PresignResult.Failed).failure.canRetry)
        assertFalse((interpretPresign(400, """{"error":"Falta el nombre del archivo."}""") as PresignResult.Failed).failure.canRetry)
    }

    // --- resultado del PUT al bucket -------------------------------------------------------------

    @Test
    fun putExitosoNoEsFallo() {
        assertNull(failureFromPut(PutOutcome.Response(200, "")))
        assertNull(failureFromPut(PutOutcome.Response(204, "")))
    }

    @Test
    fun putConUrlVencida() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><Error><Code>AccessDenied</Code><Message>Request has expired</Message></Error>"""
        val f = failureFromPut(PutOutcome.Response(403, xml))!!
        assertTrue(f.message, f.message.contains("venció"))
        assertTrue(f.canRetry)
        val other = """<Error><Code>ExpiredRequest</Code><Message>x</Message></Error>"""
        assertTrue(failureFromPut(PutOutcome.Response(400, other))!!.message.contains("venció"))
    }

    @Test
    fun putConFirmaInvalida() {
        val xml = """<Error><Code>SignatureDoesNotMatch</Code><Message>The request signature we calculated does not match</Message></Error>"""
        val f = failureFromPut(PutOutcome.Response(403, xml))!!
        assertTrue(f.message, f.message.contains("firma"))
        assertFalse(f.message.contains("venció"))
        assertTrue(f.canRetry)
    }

    @Test
    fun putConArchivoDemasiadoGrande() {
        val xml = """<Error><Code>EntityTooLarge</Code><Message>Your proposed upload exceeds the maximum allowed size</Message></Error>"""
        val f = failureFromPut(PutOutcome.Response(400, xml))!!
        assertTrue(f.message, f.message.contains("4,99 GB"))
        assertFalse(f.canRetry)
    }

    @Test
    fun putConErrorDelBucketOSinRed() {
        assertTrue(failureFromPut(PutOutcome.Response(503, "<html>"))!!.canRetry)
        assertTrue(failureFromPut(PutOutcome.Response(500, ""))!!.message.contains("500"))
        assertTrue(failureFromPut(PutOutcome.NetworkFailure("UnknownHostException"))!!.canRetry)
        assertTrue(failureFromPut(PutOutcome.NetworkFailure("x"))!!.message.contains("desde el principio"))
        val source = failureFromPut(PutOutcome.SourceFailure("El archivo creció"))!!
        assertFalse(source.canRetry)
    }

    @Test
    fun codigoDeErrorDelXml() {
        assertEquals("NoSuchBucket", r2ErrorCode("<Error><Code>NoSuchBucket</Code></Error>"))
        assertNull(r2ErrorCode("<html>nada</html>"))
        assertNull(r2ErrorCode(""))
    }

    // --- progreso ---------------------------------------------------------------------------------

    @Test
    fun fraccionYEtiquetaDeProgreso() {
        assertEquals(0f, uploadProgressFraction(0, 100), 0f)
        assertEquals(0.5f, uploadProgressFraction(50, 100), 0f)
        assertEquals(1f, uploadProgressFraction(150, 100), 0f)
        assertEquals(0f, uploadProgressFraction(10, 0), 0f)
        assertEquals("512,0 MB de 1,0 GB · 50%", progressLabel(512L * 1024 * 1024, 1024L * 1024 * 1024))
        assertEquals("0 B de 0 B · 0%", progressLabel(0, 0))
        assertEquals(0.25f, UploadState.Uploading("a.mp4", 25, 100).fraction, 0f)
    }

    @Test
    fun elProgresoSeReportaEspaciado() {
        val total = 1_000_000_000L // paso = total/200 = 5 MB
        assertFalse(shouldReportProgress(0, 4_900_000, total))
        assertTrue(shouldReportProgress(0, 5_000_000, total))
        assertFalse(shouldReportProgress(5_000_000, 9_999_999, total))
        assertTrue(shouldReportProgress(5_000_000, 10_000_000, total))
        // Al final se avisa una sola vez.
        assertTrue(shouldReportProgress(999_000_000, total, total))
        assertFalse(shouldReportProgress(total, total, total))
        // Archivo chico: el paso mínimo es 512 KB, no se reporta cada bloque.
        assertFalse(shouldReportProgress(0, 65_536, 100_000))
        assertTrue(shouldReportProgress(0, 100_000, 100_000))
    }

    // --- lectura en streaming ----------------------------------------------------------------------

    private fun pattern(size: Int): ByteArray = ByteArray(size) { (it % 251).toByte() }

    @Test
    fun copiaExactaYEnBloquesDelTamanoDelBufer() {
        val data = pattern(300_000)
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var largestChunk = 0
        val reports = ArrayList<Long>()
        val copied = copyStreamWithProgress(
            input = ByteArrayInputStream(data),
            totalBytes = data.size.toLong(),
            buffer = buffer,
            write = { b, o, l ->
                largestChunk = maxOf(largestChunk, l)
                out.write(b, o, l)
            },
            onProgress = { reports += it },
        )
        assertEquals(300_000L, copied)
        assertTrue(data.contentEquals(out.toByteArray()))
        assertTrue("ningún bloque pasa del búfer", largestChunk <= buffer.size)
        // 300 KB < 512 KB de paso mínimo: un único aviso, al final.
        assertEquals(listOf(300_000L), reports)
    }

    /** Un stream que NO guarda nada: genera bytes al leer. Sirve para "subir" más de lo que entra en memoria. */
    private class VirtualStream(private val size: Long) : InputStream() {
        private var position = 0L
        override fun read(): Int = if (position >= size) -1 else { position++; 7 }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (position >= size) return -1
            val n = minOf(len.toLong(), size - position).toInt()
            position += n
            return n
        }
    }

    @Test
    fun unArchivoMasGrandeQueIntMaxYQueLaMemoriaPasaEnStreaming() {
        // 3 GiB con un búfer de 64 KB. Los tests se corrieron con la JVM limitada (-Xmx64m): si la
        // implementación acumulara el archivo, esto no cabría. Y 3 GiB > Int.MAX_VALUE prueba la aritmética en Long.
        val total = 3L * 1024 * 1024 * 1024
        var written = 0L
        var reports = 0
        var lastReport = 0L
        val copied = copyStreamWithProgress(
            input = VirtualStream(total),
            totalBytes = total,
            buffer = ByteArray(64 * 1024),
            write = { _, _, l -> written += l },
            onProgress = {
                reports++
                assertTrue("el avance nunca retrocede", it > lastReport)
                lastReport = it
            },
        )
        assertEquals(total, copied)
        assertEquals(total, written)
        assertEquals(total, lastReport)
        assertTrue("unos ~200 avisos, no uno por bloque: $reports", reports in 150..260)
    }

    /** Entrega de a poco, como hacen algunos proveedores de contenido. */
    private class TrickleStream(private val data: ByteArray, private val chunk: Int) : InputStream() {
        private var pos = 0
        override fun read(): Int = if (pos >= data.size) -1 else data[pos++].toInt() and 0xFF
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos >= data.size) return -1
            val n = minOf(len, chunk, data.size - pos)
            System.arraycopy(data, pos, b, off, n)
            pos += n
            return n
        }
    }

    @Test
    fun aguantaLecturasParciales() {
        val data = pattern(100_000)
        val out = java.io.ByteArrayOutputStream()
        val copied = copyStreamWithProgress(TrickleStream(data, 777), data.size.toLong(), ByteArray(4096), { b, o, l -> out.write(b, o, l) }, {})
        assertEquals(100_000L, copied)
        assertTrue(data.contentEquals(out.toByteArray()))
    }

    @Test
    fun siElArchivoSeAchicaFalla() {
        try {
            copyStreamWithProgress(ByteArrayInputStream(pattern(1000)), 2000L, ByteArray(512), { _, _, _ -> }, {})
            fail("tenía que fallar")
        } catch (e: SourceChangedException) {
            assertTrue(e.message!!, e.message!!.contains("antes de lo esperado"))
        }
    }

    @Test
    fun siElArchivoCreceFallaEnVezDeSubirloCortado() {
        try {
            copyStreamWithProgress(ByteArrayInputStream(pattern(2000)), 1000L, ByteArray(512), { _, _, _ -> }, {})
            fail("tenía que fallar")
        } catch (e: SourceChangedException) {
            assertTrue(e.message!!, e.message!!.contains("creció"))
        }
    }
}
