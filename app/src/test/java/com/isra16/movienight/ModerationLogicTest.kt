package com.isra16.movienight

import com.isra16.movienight.net.ModerationAction
import com.isra16.movienight.net.ModerationCheck
import com.isra16.movienight.net.PendingModerations
import com.isra16.movienight.net.Viewer
import com.isra16.movienight.net.actionLabel
import com.isra16.movienight.net.availableActions
import com.isra16.movienight.net.canModerate
import com.isra16.movienight.net.checkModeration
import com.isra16.movienight.net.confirmationFor
import com.isra16.movienight.net.isModerationTarget
import com.isra16.movienight.net.moderationPayload
import com.isra16.movienight.net.moderationTimeoutMessage
import com.isra16.movienight.net.outcomeReached
import com.isra16.movienight.net.parseViewers
import com.isra16.movienight.net.viewerLabel
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fase 5: reglas de qué acciones se ofrecen, payloads, comprobaciones previas y seguimiento de pedidos. */
class ModerationLogicTest {

    private val me = Viewer(id = "sock-me", username = "isra", isHost = true, muted = false, buffering = false)
    private val ana = Viewer(id = "sock-ana", username = "ana", isHost = false, muted = false, buffering = false)
    private val beto = Viewer(id = "sock-beto", username = "beto", isHost = false, muted = true, buffering = false)
    private val room = listOf(me, ana, beto)

    private val all = listOf(ModerationAction.MAKE_HOST, ModerationAction.TOGGLE_MUTE, ModerationAction.KICK)

    // --- Eventos del server (nombres y payload) -----------------------------------------------------

    @Test
    fun losNombresDeEventoSonLosDelServer() {
        assertEquals("make-host", ModerationAction.MAKE_HOST.wire)
        assertEquals("toggle-mute", ModerationAction.TOGGLE_MUTE.wire)
        assertEquals("kick-user", ModerationAction.KICK.wire)
    }

    @Test
    fun elPayloadEsElIdDeSocketEnTextoPlano() {
        assertEquals("sock-ana", moderationPayload("sock-ana"))
    }

    @Test
    fun unIdVacioOEnBlancoNoSeEmite() {
        assertNull(moderationPayload(""))
        assertNull(moderationPayload("   "))
    }

    // --- Qué acciones se ofrecen según el rol --------------------------------------------------------

    @Test
    fun elHostConfirmadoVeLasTresAccionesSobreOtros() {
        assertEquals(all, availableActions(ana, myId = "sock-me", isHost = true, isConnected = true))
        assertEquals(all, availableActions(beto, myId = "sock-me", isHost = true, isConnected = true))
    }

    @Test
    fun unInvitadoNoVeAcciones() {
        assertTrue(availableActions(ana, myId = "sock-beto", isHost = false, isConnected = true).isEmpty())
        assertTrue(availableActions(me, myId = "sock-beto", isHost = false, isConnected = true).isEmpty())
    }

    @Test
    fun sinConexionNoSeOfreceNada() {
        assertTrue(availableActions(ana, myId = "sock-me", isHost = true, isConnected = false).isEmpty())
    }

    @Test
    fun nuncaSeOfreceNadaSobreUnoMismo() {
        assertTrue(availableActions(me, myId = "sock-me", isHost = true, isConnected = true).isEmpty())
        // Aunque la fila propia no figure como host (lista desfasada), el id propio la excluye.
        val meNotHost = me.copy(isHost = false)
        assertTrue(availableActions(meNotHost, myId = "sock-me", isHost = true, isConnected = true).isEmpty())
    }

    @Test
    fun nuncaSeOfreceNadaSobreQuienFiguraComoHost() {
        val otroHost = ana.copy(isHost = true)
        assertTrue(availableActions(otroHost, myId = "sock-me", isHost = true, isConnected = true).isEmpty())
    }

    @Test
    fun sinSaberElIdPropioNoSeOfreceNada() {
        assertTrue(availableActions(ana, myId = null, isHost = true, isConnected = true).isEmpty())
        assertTrue(availableActions(ana, myId = "", isHost = true, isConnected = true).isEmpty())
    }

    @Test
    fun unaFilaSinIdNoEsObjetivo() {
        assertFalse(isModerationTarget(ana.copy(id = ""), myId = "sock-me"))
    }

    @Test
    fun soloSePuedeModerarConElRolConfirmadoYConexion() {
        assertTrue(canModerate(isHost = true, isConnected = true))
        assertFalse(canModerate(isHost = false, isConnected = true))
        assertFalse(canModerate(isHost = true, isConnected = false))
        assertFalse(canModerate(isHost = false, isConnected = false))
    }

    // --- Comprobación justo antes de emitir ---------------------------------------------------------

    @Test
    fun siTodoEstaBienSePermite() {
        val check = checkModeration("sock-ana", room, myId = "sock-me", isHost = true, isConnected = true)
        assertEquals(ModerationCheck.Allowed(ana), check)
    }

    @Test
    fun sinConexionSeRechazaConUnMensaje() {
        val check = checkModeration("sock-ana", room, "sock-me", isHost = true, isConnected = false)
        assertTrue(check is ModerationCheck.Rejected)
        assertTrue((check as ModerationCheck.Rejected).message.contains("conexión"))
    }

    @Test
    fun siYaNoEsHostSeRechaza() {
        val check = checkModeration("sock-ana", room, "sock-me", isHost = false, isConnected = true)
        assertEquals(ModerationCheck.Rejected("Ya no sos el host de la sala."), check)
    }

    @Test
    fun siLaPersonaYaSeFueSeRechaza() {
        val check = checkModeration("sock-zeta", room, "sock-me", isHost = true, isConnected = true)
        assertEquals(ModerationCheck.Rejected("Esa persona ya no está en la sala."), check)
    }

    @Test
    fun noSePermiteSobreUnoMismo() {
        val check = checkModeration("sock-me", room, "sock-me", isHost = true, isConnected = true)
        assertTrue(check is ModerationCheck.Rejected)
    }

    // --- Textos --------------------------------------------------------------------------------------

    @Test
    fun silenciarYQuitarSilencioCambianSegunElEstado() {
        assertEquals("Silenciar", actionLabel(ModerationAction.TOGGLE_MUTE, ana))
        assertEquals("Quitar silencio", actionLabel(ModerationAction.TOGGLE_MUTE, beto))
        assertEquals("Hacer host", actionLabel(ModerationAction.MAKE_HOST, ana))
        assertEquals("Expulsar", actionLabel(ModerationAction.KICK, ana))
    }

    @Test
    fun expulsarYPasarElHostPidenConfirmacionYSilenciarNo() {
        val kick = confirmationFor(ModerationAction.KICK, "ana")
        assertNotNull(kick)
        assertEquals("¿Expulsar a ana?", kick!!.title)
        assertEquals("Expulsar", kick.confirmLabel)
        val host = confirmationFor(ModerationAction.MAKE_HOST, "ana")
        assertNotNull(host)
        assertEquals("¿Pasarle el control a ana?", host!!.title)
        assertNull(confirmationFor(ModerationAction.TOGGLE_MUTE, "ana"))
    }

    @Test
    fun laFilaMuestraVosHostYSilenciado() {
        assertEquals("ana", viewerLabel(ana, isMe = false))
        assertEquals("isra · vos, host", viewerLabel(me, isMe = true))
        assertEquals("beto · silenciado", viewerLabel(beto, isMe = false))
        assertEquals("beto · vos, silenciado", viewerLabel(beto, isMe = true))
    }

    // --- Se ve el resultado en viewer-list -----------------------------------------------------------

    @Test
    fun expulsarSeCumpleCuandoLaPersonaDejaDeEstar() {
        assertFalse(outcomeReached(ModerationAction.KICK, ana, room))
        assertTrue(outcomeReached(ModerationAction.KICK, ana, listOf(me, beto)))
    }

    @Test
    fun silenciarSeCumpleCuandoCambiaElEstadoDeSilencio() {
        assertFalse(outcomeReached(ModerationAction.TOGGLE_MUTE, ana, room))
        assertTrue(outcomeReached(ModerationAction.TOGGLE_MUTE, ana, listOf(me, ana.copy(muted = true), beto)))
        // Quitar silencio: antes estaba silenciado.
        assertFalse(outcomeReached(ModerationAction.TOGGLE_MUTE, beto, room))
        assertTrue(outcomeReached(ModerationAction.TOGGLE_MUTE, beto, listOf(me, ana, beto.copy(muted = false))))
    }

    @Test
    fun hacerHostSeCumpleCuandoLaPersonaPasaAFigurarComoHost() {
        assertFalse(outcomeReached(ModerationAction.MAKE_HOST, ana, room))
        assertTrue(
            outcomeReached(
                ModerationAction.MAKE_HOST,
                ana,
                listOf(me.copy(isHost = false), ana.copy(isHost = true), beto),
            ),
        )
    }

    @Test
    fun siLaPersonaSeVaMientrasTantoNoQuedaNadaPorConfirmar() {
        assertTrue(outcomeReached(ModerationAction.TOGGLE_MUTE, ana, listOf(me, beto)))
        assertTrue(outcomeReached(ModerationAction.MAKE_HOST, ana, listOf(me, beto)))
    }

    @Test
    fun elAvisoDeTiempoAgotadoNombraALaPersona() {
        for (action in all) assertTrue(moderationTimeoutMessage(action, "ana").contains("ana"))
    }

    // --- Pedidos en curso ---------------------------------------------------------------------------

    @Test
    fun unSegundoPedidoSobreLaMismaPersonaSeIgnora() {
        val pending = PendingModerations()
        assertTrue(pending.begin(ModerationAction.TOGGLE_MUTE, ana))
        // Un segundo toque antes de ver el resultado deshacería el silencio (el server alterna).
        assertFalse(pending.begin(ModerationAction.TOGGLE_MUTE, ana))
        assertTrue(pending.isPending("sock-ana"))
        // Otra persona no se ve afectada.
        assertTrue(pending.begin(ModerationAction.KICK, beto))
        assertEquals(setOf("sock-ana", "sock-beto"), pending.ids)
    }

    @Test
    fun laListaNuevaCierraLosPedidosCumplidosYDejaLosDemas() {
        val pending = PendingModerations()
        pending.begin(ModerationAction.TOGGLE_MUTE, ana)
        pending.begin(ModerationAction.KICK, beto)
        // Llega una lista donde ana ya está silenciada pero beto sigue conectado.
        val done = pending.resolve(listOf(me, ana.copy(muted = true), beto))
        assertEquals(listOf("sock-ana"), done)
        assertFalse(pending.isPending("sock-ana"))
        assertTrue(pending.isPending("sock-beto"))
        // Cuando la persona ya puede recibir otro pedido, se acepta.
        assertTrue(pending.begin(ModerationAction.TOGGLE_MUTE, ana.copy(muted = true)))
    }

    @Test
    fun alAgotarseLaEsperaDevuelveLoPendienteUnaSolaVez() {
        val pending = PendingModerations()
        pending.begin(ModerationAction.KICK, ana)
        val expired = pending.expire("sock-ana")
        assertEquals(ModerationAction.KICK to ana, expired)
        assertNull(pending.expire("sock-ana"))
        assertTrue(pending.ids.isEmpty())
    }

    @Test
    fun unPedidoYaResueltoNoDaAvisoDeTiempoAgotado() {
        val pending = PendingModerations()
        pending.begin(ModerationAction.KICK, ana)
        pending.resolve(listOf(me, beto))
        assertNull(pending.expire("sock-ana"))
    }

    @Test
    fun clearVaciaTodo() {
        val pending = PendingModerations()
        pending.begin(ModerationAction.KICK, ana)
        pending.begin(ModerationAction.MAKE_HOST, beto)
        pending.clear()
        assertTrue(pending.ids.isEmpty())
    }

    // --- Parser del viewer-list tal como lo manda el server (server.js, broadcastViewerList) -----------

    @Test
    fun elViewerListDelServerAlimentaLasReglas() {
        val json = JSONArray(
            """[
              {"id":"sock-me","username":"isra","isHost":true,"muted":false,"buffering":false},
              {"id":"sock-ana","username":"ana","isHost":false,"muted":true,"buffering":true}
            ]""",
        )
        val viewers = parseViewers(json)
        assertEquals(2, viewers.size)
        assertEquals("sock-ana", viewers[1].id)
        assertTrue(viewers[1].muted)
        assertEquals(all, availableActions(viewers[1], "sock-me", isHost = true, isConnected = true))
        assertTrue(availableActions(viewers[0], "sock-me", isHost = true, isConnected = true).isEmpty())
        assertEquals("ana · silenciado", viewerLabel(viewers[1], isMe = false))
    }
}
