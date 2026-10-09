package com.isra16.movienight

import com.isra16.movienight.net.ADMIN_PANEL_PATH
import com.isra16.movienight.net.ADMIN_PROBE_PATH
import com.isra16.movienight.net.AdminAccess
import com.isra16.movienight.net.adminAccessFrom
import com.isra16.movienight.net.adminPanelUrl
import com.isra16.movienight.net.mergeAdminAccess
import com.isra16.movienight.net.shouldShowAdminAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminAccessLogicTest {

    // Forma verificada en server.js (GET /admin/stats).
    private val statsBody =
        """{"activeRooms":2,"connectedUsers":5,"uploadsInProgress":0,"r2ErrorCount":0}"""

    @Test
    fun rutasVerificadasEnElServer() {
        assertEquals("/admin/stats", ADMIN_PROBE_PATH)
        assertEquals("/admin.html", ADMIN_PANEL_PATH)
    }

    @Test
    fun admin200ConElJsonEsperado() {
        assertEquals(AdminAccess.ADMIN, adminAccessFrom(200, statsBody))
    }

    @Test
    fun cuentaNormal403() {
        assertEquals(AdminAccess.NOT_ADMIN, adminAccessFrom(403, """{"error":"Requiere rol de administrador."}"""))
    }

    @Test
    fun sinSesion401YSinPostgres404SonNoAdmin() {
        assertEquals(AdminAccess.NOT_ADMIN, adminAccessFrom(401, """{"error":"Requiere sesión."}"""))
        assertEquals(AdminAccess.NOT_ADMIN, adminAccessFrom(404, """{"error":"No disponible."}"""))
    }

    @Test
    fun un200ConUnaPaginaHtmlNoEsAdmin() {
        assertEquals(AdminAccess.UNKNOWN, adminAccessFrom(200, "<html><body>Bad gateway</body></html>"))
    }

    @Test
    fun un200ConJsonQueNoEsElDeStatsNoEsAdmin() {
        assertEquals(AdminAccess.UNKNOWN, adminAccessFrom(200, """{"loggedIn":true}"""))
        assertEquals(AdminAccess.UNKNOWN, adminAccessFrom(200, ""))
    }

    @Test
    fun errorDeRedYDelServerNoSeSabe() {
        assertEquals(AdminAccess.UNKNOWN, adminAccessFrom(-1, "UnknownHostException: x"))
        assertEquals(AdminAccess.UNKNOWN, adminAccessFrom(500, """{"error":"Error interno verificando permisos."}"""))
        assertEquals(AdminAccess.UNKNOWN, adminAccessFrom(502, ""))
        assertEquals(AdminAccess.UNKNOWN, adminAccessFrom(429, ""))
    }

    @Test
    fun soloElBotonConAdminConfirmado() {
        assertTrue(shouldShowAdminAccess(AdminAccess.ADMIN))
        assertFalse(shouldShowAdminAccess(AdminAccess.NOT_ADMIN))
        assertFalse(shouldShowAdminAccess(AdminAccess.UNKNOWN))
    }

    @Test
    fun unaRespuestaDudosaNoBorraLoYaSabido() {
        assertEquals(AdminAccess.ADMIN, mergeAdminAccess(AdminAccess.ADMIN, AdminAccess.UNKNOWN))
        assertEquals(AdminAccess.NOT_ADMIN, mergeAdminAccess(AdminAccess.NOT_ADMIN, AdminAccess.UNKNOWN))
        assertEquals(AdminAccess.UNKNOWN, mergeAdminAccess(AdminAccess.UNKNOWN, AdminAccess.UNKNOWN))
    }

    @Test
    fun unaRespuestaClaraReemplazaLoAnterior() {
        // Le quitaron el rol: el botón tiene que desaparecer.
        assertEquals(AdminAccess.NOT_ADMIN, mergeAdminAccess(AdminAccess.ADMIN, AdminAccess.NOT_ADMIN))
        // Le dieron el rol.
        assertEquals(AdminAccess.ADMIN, mergeAdminAccess(AdminAccess.NOT_ADMIN, AdminAccess.ADMIN))
        assertEquals(AdminAccess.ADMIN, mergeAdminAccess(AdminAccess.UNKNOWN, AdminAccess.ADMIN))
    }

    @Test
    fun urlDelPanel() {
        assertEquals("https://sala.ejemplo.uk/admin.html", adminPanelUrl("https://sala.ejemplo.uk"))
        assertEquals("http://192.168.1.10:3000/admin.html", adminPanelUrl("http://192.168.1.10:3000"))
        assertEquals("https://sala.ejemplo.uk/admin.html", adminPanelUrl("https://sala.ejemplo.uk/"))
    }
}
