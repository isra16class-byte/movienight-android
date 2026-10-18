package com.isra16.movienight

import com.isra16.movienight.net.apiErrorMessage
import com.isra16.movienight.net.PutOutcome
import com.isra16.movienight.net.authErrorMessage
import com.isra16.movienight.net.failureFromPut
import com.isra16.movienight.net.interpretPresign
import com.isra16.movienight.net.PresignResult
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

    private val tecnicismos = Regex(
        "(?U)\\b(R2|Cloudflare|firma|token|socket|http|https|código|permiso de subida|nube)\\b",
        RegexOption.IGNORE_CASE,
    )

    @Test
    fun losMensajesDeSubidaNoTienenVoseoNiTecnicismos() {
        val xmlExpirado = "<Error><Code>ExpiredRequest</Code></Error>"
        val mensajes = buildList {
            for (c in listOf(200, 400, 403, 404, 408, 418, 429, 500, 503)) {
                add(failureFromPut(PutOutcome.Response(c, if (c == 403) xmlExpirado else ""))?.message)
                add(failureFromPut(PutOutcome.Response(c, "<Error><Code>SignatureDoesNotMatch</Code></Error>"))?.message)
            }
            add(failureFromPut(PutOutcome.NetworkFailure("x"))?.message)
            add(failureFromPut(PutOutcome.SourceFailure("x"))?.message)
            for (c in listOf(-1, 200, 400, 401, 403, 404, 413, 418, 429, 500, 502)) {
                add((interpretPresign(c, "") as? PresignResult.Failed)?.failure?.message)
            }
        }.filterNotNull()
        assertTrue(mensajes.size > 20)
        for (m in mensajes) {
            assertFalse("voseo: $m", voseo.containsMatchIn(m))
            assertFalse("tecnicismo: $m", tecnicismos.containsMatchIn(m))
            assertFalse("código de respuesta: $m", m.any { it.isDigit() })
            assertTrue("mide ${m.length}: $m", m.length <= 100)
        }
    }

    @Test
    fun laGuardaDetectaElVoseo() {
        assertTrue(voseo.containsMatchIn("Intentá de nuevo."))
        assertTrue(voseo.containsMatchIn("Ya no sos el host."))
        assertFalse(voseo.containsMatchIn("Inténtalo de nuevo."))
    }
}
