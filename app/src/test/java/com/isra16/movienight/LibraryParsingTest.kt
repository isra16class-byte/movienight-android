package com.isra16.movienight

import com.isra16.movienight.net.formatFileSize
import com.isra16.movienight.net.parseCreatedRoomId
import com.isra16.movienight.net.parseLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryParsingTest {

    @Test
    fun parseaLaListaConLaFormaRealDelServer() {
        // Forma verificada en server.js (GET /api/uploads): mtime viene de fs.statSync().mtimeMs, un número con decimales.
        val body = """
            [
              {"filename":"1727890000__peli-nueva.mp4","displayName":"peli-nueva.mp4","size":1572864000,"mtime":1727890000123.456},
              {"filename":"1727800000__otra.mkv","displayName":"otra.mkv","size":734003200,"mtime":1727800000000}
            ]
        """.trimIndent()
        val items = parseLibrary(body)!!
        assertEquals(2, items.size)
        assertEquals("1727890000__peli-nueva.mp4", items[0].filename)
        assertEquals("peli-nueva.mp4", items[0].displayName)
        assertEquals(1572864000L, items[0].sizeBytes)
        assertEquals(1727890000123L, items[0].modifiedMillis)
        assertEquals("otra.mkv", items[1].displayName) // se respeta el orden del server
    }

    @Test
    fun bibliotecaVaciaEsUnaListaVacia() {
        assertEquals(emptyList<Any>(), parseLibrary("[]"))
    }

    @Test
    fun siFaltaElNombreParaMostrarSeUsaElFilename() {
        val items = parseLibrary("""[{"filename":"x.mp4","size":10}]""")!!
        assertEquals("x.mp4", items[0].displayName)
        assertEquals(0L, items[0].modifiedMillis)
    }

    @Test
    fun descartaElementosSinFilename() {
        val items = parseLibrary("""[{"displayName":"huérfano"},{"filename":"ok.mp4"},"basura",{"filename":"  "}]""")!!
        assertEquals(listOf("ok.mp4"), items.map { it.filename })
    }

    @Test
    fun siNoEsUnArrayJsonDevuelveNull() {
        assertNull(parseLibrary("<html>Bad gateway</html>"))
        assertNull(parseLibrary("""{"error":"x"}"""))
        assertNull(parseLibrary(""))
    }

    @Test
    fun parseaElRoomIdDeLaSalaCreada() {
        assertEquals("a1b2c3", parseCreatedRoomId("""{"roomId":"a1b2c3","hostToken":"zzz"}"""))
        assertNull(parseCreatedRoomId("""{"hostToken":"zzz"}"""))
        assertNull(parseCreatedRoomId("<html>"))
    }

    @Test
    fun tamanoLegible() {
        assertEquals("0 B", formatFileSize(0))
        assertEquals("850 B", formatFileSize(850))
        assertEquals("1 KB", formatFileSize(1024))
        assertEquals("512 KB", formatFileSize(512L * 1024))
        assertEquals("1,5 MB", formatFileSize(1_572_864))
        assertEquals("700,0 MB", formatFileSize(734_003_200))
        assertEquals("1,5 GB", formatFileSize(1_610_612_736))
        assertTrue(formatFileSize(-5).startsWith("0"))
    }
}
