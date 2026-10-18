package com.isra16.movienight

import com.isra16.movienight.net.apiErrorMessage
import com.isra16.movienight.net.authErrorMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guarda de la guía de estilo (Fase 8, paso 5p): los mensajes propios de la app (sin texto del servidor)
 * no usan voseo, no muestran códigos de respuesta y son breves.
 */
class UserMessagesGuardTest {

    private val codes = listOf(-1, 400, 401, 403, 404, 409, 429, 500, 502, 503, 504, 418)

    private val voseo = Regex(
        "(?U)\\b(vos|sos|tenés|podés|querés|sabés|salís|dejás|intentá|reintentá|revisá|iniciá|esperá|elegí|elegilo|" +
            "escribí|repetí|probá|volvé|andá|subí|borrá|avisá|pedile|registrate|tocá|pegá|mirá|dale|acá|" +
            "confirmá|seleccioná|abrí|cerrá|buscá|usá|creá|cambiá|agregá|quitá|guardá|enviá|mandá)\\b",
        RegexOption.IGNORE_CASE,
    )

    private fun check(origin: String, code: Int, message: String) {
        assertFalse("$origin($code) tiene voseo: $message", voseo.containsMatchIn(message))
        assertFalse("$origin($code) muestra un código: $message", message.any { it.isDigit() })
        assertTrue("$origin($code) mide ${message.length}: $message", message.length <= 100)
    }

    @Test
    fun apiErrorMessageSinTextoDelServerNoTieneVoseoNiCodigosYEsBreve() {
        for (code in codes) check("apiErrorMessage", code, apiErrorMessage(code, null))
    }

    @Test
    fun authErrorMessageSinTextoDelServerNoTieneVoseoNiCodigosYEsBreve() {
        for (code in codes) check("authErrorMessage", code, authErrorMessage(code, null))
    }

    @Test
    fun laGuardaDetectaElVoseo() {
        assertTrue(voseo.containsMatchIn("Intentá de nuevo."))
        assertTrue(voseo.containsMatchIn("Ya no sos el host."))
        assertFalse(voseo.containsMatchIn("Inténtalo de nuevo."))
    }
}
