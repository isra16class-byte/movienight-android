package com.isra16.movienight

import com.isra16.movienight.auth.defaultUsername
import org.junit.Assert.assertEquals
import org.junit.Test

class UsernameTest {

    @Test
    fun usaLaParteLocalDelEmail() {
        assertEquals("isra", defaultUsername("isra@ejemplo.com"))
        assertEquals("ana.perez", defaultUsername("  ana.perez@ejemplo.com "))
    }

    @Test
    fun sinEmailQuedaAnonimo() {
        assertEquals("Anónimo", defaultUsername(""))
        assertEquals("Anónimo", defaultUsername("@ejemplo.com"))
    }

    @Test
    fun seRecortaALos40CaracteresQueAceptaElServer() {
        val largo = "a".repeat(60) + "@ejemplo.com"
        assertEquals(40, defaultUsername(largo).length)
    }
}
