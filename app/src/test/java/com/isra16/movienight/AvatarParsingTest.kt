package com.isra16.movienight

import com.isra16.movienight.net.Avatar
import com.isra16.movienight.net.avatarIdFromMe
import com.isra16.movienight.net.parseAvatarList
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AvatarParsingTest {

    private val base = "https://srv.example"

    @Test
    fun laListaSeLeConLasUrlsAbsolutasYElOrdenDelServidor() {
        val body = """{"avatars":[
            {"id":"koala-ninja","name":"Koala ninja","url":"/avatars/koala-ninja.png?v=aa11"},
            {"id":"trex","name":"T-Rex","url":"/avatars/trex.png?v=bb22"}]}"""
        assertEquals(
            listOf(
                Avatar("koala-ninja", "Koala ninja", "https://srv.example/avatars/koala-ninja.png?v=aa11"),
                Avatar("trex", "T-Rex", "https://srv.example/avatars/trex.png?v=bb22"),
            ),
            parseAvatarList(body, base),
        )
    }

    @Test
    fun laDireccionConBarraFinalNoDuplicaLaBarra() {
        val body = """{"avatars":[{"id":"a","name":"A","url":"/avatars/a.png?v=1"}]}"""
        assertEquals("https://srv.example/avatars/a.png?v=1", parseAvatarList(body, "https://srv.example/")!![0].url)
    }

    @Test
    fun unaUrlAbsolutaSeDejaIgual() {
        val body = """{"avatars":[{"id":"a","name":"A","url":"https://cdn.example/a.png?v=1"}]}"""
        assertEquals("https://cdn.example/a.png?v=1", parseAvatarList(body, base)!![0].url)
    }

    @Test
    fun unaListaVaciaEsUnaListaVaciaYNoUnError() {
        assertEquals(emptyList<Avatar>(), parseAvatarList("""{"avatars":[]}""", base))
    }

    @Test
    fun lasEntradasMalFormadasSeDescartanYLasBuenasSeQuedan() {
        val body = """{"avatars":[
            {"name":"sin id","url":"/avatars/x.png"},
            {"id":"sin-url","name":"Sin url"},
            {"id":"url-vacia","name":"Url vacia","url":""},
            "no soy un objeto",
            {"id":"ok","name":"Bueno","url":"/avatars/ok.png?v=9"}]}"""
        assertEquals(listOf(Avatar("ok", "Bueno", "https://srv.example/avatars/ok.png?v=9")), parseAvatarList(body, base))
    }

    @Test
    fun sinNombreSeUsaElId() {
        val body = """{"avatars":[{"id":"pinguino","url":"/avatars/p.png"},{"id":"viajero","name":null,"url":"/avatars/v.png"}]}"""
        assertEquals(listOf("pinguino", "viajero"), parseAvatarList(body, base)!!.map { it.name })
    }

    @Test
    fun unaRespuestaQueNoEsLaEsperadaDaNull() {
        assertNull(parseAvatarList("<html>Bad gateway</html>", base))
        assertNull(parseAvatarList("", base))
        assertNull(parseAvatarList("{}", base))
        assertNull(parseAvatarList("""{"avatars":"nada"}""", base))
        assertNull(parseAvatarList("""{"avatars":null}""", base))
        assertNull(parseAvatarList("[]", base))
    }

    @Test
    fun elAvatarDeAuthMeSeLee() {
        assertEquals("trex", avatarIdFromMe(JSONObject("""{"loggedIn":true,"id":"u1","email":"a@b.c","avatar":{"id":"trex","name":"T-Rex","url":"/avatars/trex.png?v=1"}}""")))
    }

    @Test
    fun sinAvatarEnAuthMeDaNull() {
        assertNull(avatarIdFromMe(JSONObject("""{"loggedIn":true,"id":"u1","avatar":null}""")))
        assertNull(avatarIdFromMe(JSONObject("""{"loggedIn":true,"id":"u1"}""")))
        assertNull(avatarIdFromMe(JSONObject("""{"loggedIn":true,"id":"u1","avatar":{"name":"sin id"}}""")))
        assertNull(avatarIdFromMe(JSONObject("""{"loggedIn":true,"id":"u1","avatar":{"id":""}}""")))
        assertNull(avatarIdFromMe(JSONObject("""{"loggedIn":true,"id":"u1","avatar":"texto"}""")))
        assertNotNull(avatarIdFromMe(JSONObject("""{"avatar":{"id":"x"}}""")))
    }
}
