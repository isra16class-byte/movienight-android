package com.isra16.movienight

import com.isra16.movienight.net.extractRoomId
import com.isra16.movienight.net.isRoomPasswordError
import com.isra16.movienight.net.videoDisplayName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomIdsTest {

    @Test
    fun codigoSueltoSeDevuelveTalCual() {
        assertEquals("a1b2c3", extractRoomId("a1b2c3"))
        assertEquals("a1b2c3", extractRoomId("  a1b2c3  "))
    }

    @Test
    fun codigoHexadecimalEnMayusculaSePasaAMinuscula() {
        assertEquals("a1b2c3", extractRoomId("A1B2C3"))
    }

    @Test
    fun linkCompletoDeLaSala() {
        assertEquals("a1b2c3", extractRoomId("https://sala.ejemplo.uk/room/a1b2c3"))
        assertEquals("a1b2c3", extractRoomId("sala.ejemplo.uk/room/a1b2c3"))
    }

    @Test
    fun linkConQueryOFragmentoNoSeCuelaEnElId() {
        assertEquals("a1b2c3", extractRoomId("https://sala.ejemplo.uk/room/a1b2c3?x=1"))
        assertEquals("a1b2c3", extractRoomId("https://sala.ejemplo.uk/room/a1b2c3#chat"))
        assertEquals("a1b2c3", extractRoomId("https://sala.ejemplo.uk/room/a1b2c3/"))
    }

    @Test
    fun textoQueNoEsCodigoNiLinkDaNull() {
        assertNull(extractRoomId(""))
        assertNull(extractRoomId("   "))
        assertNull(extractRoomId("hola che"))
        assertNull(extractRoomId("https://sala.ejemplo.uk/"))
        assertNull(extractRoomId("ab")) // demasiado corto
    }

    @Test
    fun erroresDeContrasenaSeReconocen() {
        assertTrue(isRoomPasswordError("Contraseña incorrecta. Te quedan 4 intento(s)."))
        assertTrue(isRoomPasswordError("Contraseña incorrecta. Se bloquearon los intentos por 15 minutos."))
        assertTrue(isRoomPasswordError("Demasiados intentos fallidos. Esperá 12 min y volvé a intentar."))
    }

    @Test
    fun otrosErroresDeSalaNoSonDeContrasena() {
        assertFalse(isRoomPasswordError("La sala no existe."))
    }

    @Test
    fun nombreDelVideoQuitaRutaYPrefijo() {
        assertEquals("mi-peli.mp4", videoDisplayName("/uploads/1727890000__mi-peli.mp4"))
        assertEquals("archivo.mp4", videoDisplayName("/uploads/archivo.mp4"))
    }

    @Test
    fun nombreDelVideoDeUnaUrlDeR2() {
        assertEquals(
            "Mi Peli.mp4",
            videoDisplayName("https://cdn.ejemplo.com/videos/17__Mi%20Peli.mp4?x=1"),
        )
        assertEquals("a+b.mp4", videoDisplayName("/uploads/a+b.mp4")) // un + literal no es un espacio
    }

    @Test
    fun nombreDelVideoVacioONulo() {
        assertEquals("", videoDisplayName(null))
        assertEquals("", videoDisplayName("  "))
    }
}
