package com.isra16.movienight

import com.isra16.movienight.net.normalizeBaseUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class UrlUtilsTest {
    @Test
    fun agregaHttpsSiFaltaElEsquema() {
        assertEquals("https://sala.ejemplo.uk", normalizeBaseUrl("sala.ejemplo.uk"))
    }

    @Test
    fun respetaHttpYHttps() {
        assertEquals("http://192.168.1.10:3000", normalizeBaseUrl("http://192.168.1.10:3000"))
        assertEquals("https://sala.ejemplo.uk", normalizeBaseUrl("https://sala.ejemplo.uk"))
    }

    @Test
    fun quitaEspaciosYBarrasFinales() {
        assertEquals("https://sala.ejemplo.uk", normalizeBaseUrl("  https://sala.ejemplo.uk//  "))
    }

    @Test
    fun vacioQuedaVacio() {
        assertEquals("", normalizeBaseUrl("   "))
    }
}
