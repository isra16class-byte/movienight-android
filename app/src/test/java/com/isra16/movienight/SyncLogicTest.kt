package com.isra16.movienight

import com.isra16.movienight.net.EVENT_SEEK_THRESHOLD_MS
import com.isra16.movienight.net.HARD_SEEK_COOLDOWN_MS
import com.isra16.movienight.net.HARD_SEEK_COOLDOWN_OVERRIDE_MS
import com.isra16.movienight.net.HARD_SEEK_THRESHOLD_MS
import com.isra16.movienight.net.HostReference
import com.isra16.movienight.net.READY_RESYNC_THRESHOLD_MS
import com.isra16.movienight.net.RoomPosition
import com.isra16.movienight.net.SOFT_DRIFT_THRESHOLD_MS
import com.isra16.movienight.net.SPEED_CATCH_UP
import com.isra16.movienight.net.SPEED_SLOW_DOWN
import com.isra16.movienight.net.SyncMessage
import com.isra16.movienight.net.SyncPlan
import com.isra16.movienight.net.SyncType
import com.isra16.movienight.net.estimateHostPosition
import com.isra16.movienight.net.formatPlaybackTime
import com.isra16.movienight.net.hostReferenceFrom
import com.isra16.movienight.net.planReadyResync
import com.isra16.movienight.net.updateHostReference
import com.isra16.movienight.net.parseRoomPosition
import com.isra16.movienight.net.parseSyncMessage
import com.isra16.movienight.net.planSync
import com.isra16.movienight.net.progressFraction
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.isra16.movienight.net.HEARTBEAT_INTERVAL_MS
import com.isra16.movienight.net.toSyncPayload
import com.isra16.movienight.net.canEmitSync
import com.isra16.movienight.net.planRejoin
import com.isra16.movienight.net.seekTargetMs
import com.isra16.movienight.net.shouldReportBuffering
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class SyncLogicTest {

    private fun sync(json: String) = parseSyncMessage(JSONObject(json))

    // --- parseSyncMessage: la forma que el server retransmite tal cual ---

    @Test
    fun playPauseYSeekTraenSoloTipoYTiempoEnSegundos() {
        assertEquals(SyncMessage(SyncType.PLAY, 90_250, null), sync("""{"type":"play","time":90.25}"""))
        assertEquals(SyncMessage(SyncType.PAUSE, 3_000, null), sync("""{"type":"pause","time":3}"""))
        assertEquals(SyncMessage(SyncType.SEEK, 600_000, null), sync("""{"type":"seek","time":600}"""))
    }

    @Test
    fun heartbeatTraePaused() {
        assertEquals(SyncMessage(SyncType.HEARTBEAT, 10_000, false), sync("""{"type":"heartbeat","time":10,"paused":false}"""))
        assertEquals(SyncMessage(SyncType.HEARTBEAT, 10_000, true), sync("""{"type":"heartbeat","time":10,"paused":true}"""))
    }

    @Test
    fun heartbeatSinPausedONoBooleanoDejaPausedEnNull() {
        // La web lo toma como "reproduciendo"; la app prefiere no tocar el play/pause ante la duda.
        assertNull(sync("""{"type":"heartbeat","time":10}""")!!.paused)
        assertNull(sync("""{"type":"heartbeat","time":10,"paused":"true"}""")!!.paused)
        assertNull(sync("""{"type":"heartbeat","time":10,"paused":null}""")!!.paused)
    }

    @Test
    fun elTiempoSeRedondeaAMilisegundos() {
        assertEquals(1_235, sync("""{"type":"seek","time":1.2346}""")!!.timeMs)
        assertEquals(0, sync("""{"type":"seek","time":0}""")!!.timeMs)
    }

    @Test
    fun tiempoNegativoSeLlevaACeroYUnoAbsurdoSeAcota() {
        assertEquals(0, sync("""{"type":"seek","time":-5}""")!!.timeMs)
        assertEquals(360_000_000L, sync("""{"type":"seek","time":1e12}""")!!.timeMs)
    }

    @Test
    fun syncMalformadoDaNull() {
        assertNull(sync("""{"time":5}"""))                          // sin tipo
        assertNull(sync("""{"type":"salto","time":5}"""))            // tipo desconocido
        assertNull(sync("""{"type":"PLAY","time":5}"""))             // el server manda minúsculas
        assertNull(sync("""{"type":"play"}"""))                      // sin tiempo
        assertNull(sync("""{"type":"play","time":"5"}"""))           // texto, no número (como el server)
        assertNull(sync("""{"type":"play","time":null}"""))
        assertNull(sync("""{"type":"play","time":true}"""))
        assertNull(sync("""{"type":5,"time":5}"""))
    }

    // --- hostPaused ---

    @Test
    fun hostPausedSegunElTipo() {
        assertEquals(false, SyncMessage(SyncType.PLAY, 0, null).hostPaused)
        assertEquals(true, SyncMessage(SyncType.PAUSE, 0, null).hostPaused)
        assertNull(SyncMessage(SyncType.SEEK, 0, null).hostPaused)
        assertEquals(true, SyncMessage(SyncType.HEARTBEAT, 0, true).hostPaused)
        assertNull(SyncMessage(SyncType.HEARTBEAT, 0, null).hostPaused)
    }

    // --- parseRoomPosition ---

    @Test
    fun posicionDeLaSalaAlEntrar() {
        assertEquals(RoomPosition(125_400, false), parseRoomPosition(JSONObject("""{"time":125.4,"paused":false}""")))
        assertEquals(RoomPosition(0, true), parseRoomPosition(JSONObject("""{"time":0,"paused":true}""")))
    }

    @Test
    fun posicionSinPausedSeAsumeEnPausa() {
        assertEquals(RoomPosition(5_000, true), parseRoomPosition(JSONObject("""{"time":5}""")))
    }

    @Test
    fun posicionMalformadaDaNull() {
        assertNull(parseRoomPosition(null))
        assertNull(parseRoomPosition(JSONObject("""{"paused":false}""")))
        assertNull(parseRoomPosition(JSONObject("""{"time":"5","paused":false}""")))
    }

    // --- planSync: corrección de desfase ---

    private fun beat(hostMs: Long, paused: Boolean? = false) = SyncMessage(SyncType.HEARTBEAT, hostMs, paused)

    @Test
    fun umbralesDocumentados() {
        assertEquals(1_000L, HARD_SEEK_THRESHOLD_MS)
        assertEquals(8_000L, HARD_SEEK_COOLDOWN_MS)
        assertEquals(10_000L, HARD_SEEK_COOLDOWN_OVERRIDE_MS)
        assertEquals(500L, SOFT_DRIFT_THRESHOLD_MS)
        assertEquals(1_000L, EVENT_SEEK_THRESHOLD_MS)
    }

    @Test
    fun heartbeatSincronizadoNoTocaNada() {
        assertEquals(SyncPlan(null, 1f, true), planSync(beat(60_000), localPositionMs = 60_000))
        assertEquals(SyncPlan(null, 1f, true), planSync(beat(60_000), localPositionMs = 60_300)) // 0.3 s adelante
        assertEquals(SyncPlan(null, 1f, true), planSync(beat(60_000), localPositionMs = 59_500)) // justo en el umbral
    }

    @Test
    fun heartbeatConDesfaseChicoAjustaVelocidadSinSaltar() {
        // La app va atrasada 1 s -> acelera.
        assertEquals(SyncPlan(null, SPEED_CATCH_UP, true), planSync(beat(60_000), localPositionMs = 59_000))
        // La app va adelantada 1 s -> frena.
        assertEquals(SyncPlan(null, SPEED_SLOW_DOWN, true), planSync(beat(60_000), localPositionMs = 61_000))
        // Justo pasado el umbral chico (501 ms) ya corrige; justo en el umbral de salto (1 s) todavía no salta.
        assertEquals(SyncPlan(null, SPEED_CATCH_UP, true), planSync(beat(60_000), localPositionMs = 59_499))
        assertEquals(SyncPlan(null, SPEED_CATCH_UP, true), planSync(beat(60_000), localPositionMs = 59_000))
    }

    @Test
    fun heartbeatConDesfaseGrandeSalta() {
        assertEquals(SyncPlan(60_000, 1f, true), planSync(beat(60_000), localPositionMs = 58_999)) // atrasada, recién pasado el umbral
        assertEquals(SyncPlan(60_000, 1f, true), planSync(beat(60_000), localPositionMs = 70_000)) // adelantada
        assertEquals(SyncPlan(60_000, 1f, true), planSync(beat(60_000), localPositionMs = 0))
    }

    @Test
    fun elCasoDeLaPruebaEnElEmuladorSaltaEnVezDeTardar40Segundos() {
        // Tras un seek del host, o al entrar con el video en marcha, la app queda 2-3 s atrasada.
        assertEquals(SyncPlan(60_000, 1f, true), planSync(beat(60_000), localPositionMs = 57_500))
        assertEquals(SyncPlan(60_000, 1f, true), planSync(beat(60_000), localPositionMs = 58_000))
    }

    @Test
    fun conElReproductorCargandoElHeartbeatNoCorrigeElDesfase() {
        // Si está en buffering, el desfase medido no es real: no se salta ni se toca la velocidad.
        assertEquals(SyncPlan(null, 1f, true), planSync(beat(60_000), localPositionMs = 50_000, playerReady = false))
        assertEquals(SyncPlan(null, 1f, true), planSync(beat(60_000), localPositionMs = 58_000, playerReady = false))
    }

    @Test
    fun conElReproductorCargandoIgualSigueElPlayPauseDelHeartbeat() {
        assertEquals(SyncPlan(null, 1f, false), planSync(beat(60_000, paused = true), localPositionMs = 60_100, playerReady = false))
        assertEquals(SyncPlan(60_000, 1f, false), planSync(beat(60_000, paused = true), localPositionMs = 50_000, playerReady = false))
    }

    @Test
    fun trasUnSaltoPorHeartbeatHayUnaPausaSinNuevosSaltos() {
        // Dentro de los 8 s: con 3 s de desfase no vuelve a saltar, solo acelera (evita el ciclo salto -> recarga -> salto).
        assertEquals(SyncPlan(null, SPEED_CATCH_UP, true), planSync(beat(60_000), 57_000, msSinceLastHardSeek = 3_000))
        assertEquals(SyncPlan(null, SPEED_CATCH_UP, true), planSync(beat(60_000), 57_000, msSinceLastHardSeek = HARD_SEEK_COOLDOWN_MS - 1))
        // Cumplidos los 8 s vuelve a poder saltar.
        assertEquals(SyncPlan(60_000, 1f, true), planSync(beat(60_000), 57_000, msSinceLastHardSeek = HARD_SEEK_COOLDOWN_MS))
    }

    @Test
    fun unDesfaseEnormeSaltaAunqueHayaPausaDeSaltos() {
        assertEquals(SyncPlan(60_000, 1f, true), planSync(beat(60_000), 49_999, msSinceLastHardSeek = 1_000))
        assertEquals(SyncPlan(null, SPEED_CATCH_UP, true), planSync(beat(60_000), 50_000, msSinceLastHardSeek = 1_000)) // justo en 10 s: todavía no
    }

    @Test
    fun laPausaDeSaltosNoAfectaALosEventosPuntualesNiAlHostEnPausa() {
        assertEquals(SyncPlan(30_000, 1f, true), planSync(SyncMessage(SyncType.PLAY, 30_000, null), 20_000, msSinceLastHardSeek = 100))
        assertEquals(SyncPlan(60_000, 1f, false), planSync(beat(60_000, paused = true), 57_000, msSinceLastHardSeek = 100))
    }

    @Test
    fun heartbeatConElHostEnPausaPausaYSoloSaltaSiHayDesfase() {
        assertEquals(SyncPlan(null, 1f, false), planSync(beat(60_000, paused = true), localPositionMs = 60_200))
        // Con el host quieto no hay nada que alcanzar con la velocidad: pasado 0.5 s se salta.
        assertEquals(SyncPlan(60_000, 1f, false), planSync(beat(60_000, paused = true), localPositionMs = 58_000))
        assertEquals(SyncPlan(60_000, 1f, false), planSync(beat(60_000, paused = true), localPositionMs = 60_501))
    }

    @Test
    fun heartbeatSinPausedNoTocaPlayPause() {
        assertEquals(SyncPlan(null, 1f, null), planSync(beat(60_000, paused = null), localPositionMs = 60_000))
        assertEquals(SyncPlan(null, SPEED_CATCH_UP, null), planSync(beat(60_000, paused = null), localPositionMs = 59_000))
    }

    @Test
    fun playPausaYSaltoDelHostSoloSaltanPasadoUnSegundo() {
        val play = SyncMessage(SyncType.PLAY, 30_000, null)
        assertEquals(SyncPlan(null, 1f, true), planSync(play, localPositionMs = 30_000))
        assertEquals(SyncPlan(null, 1f, true), planSync(play, localPositionMs = 29_000)) // 1 s justo: no salta
        assertEquals(SyncPlan(30_000, 1f, true), planSync(play, localPositionMs = 28_999))

        val pause = SyncMessage(SyncType.PAUSE, 30_000, null)
        assertEquals(SyncPlan(null, 1f, false), planSync(pause, localPositionMs = 30_400))
        assertEquals(SyncPlan(30_000, 1f, false), planSync(pause, localPositionMs = 45_000))
    }

    @Test
    fun seekDelHostSaltaPeroNoCambiaPlayPause() {
        val seek = SyncMessage(SyncType.SEEK, 600_000, null)
        assertEquals(SyncPlan(600_000, 1f, null), planSync(seek, localPositionMs = 30_000))
        assertEquals(SyncPlan(null, 1f, null), planSync(seek, localPositionMs = 600_300))
    }

    @Test
    fun losEventosPuntualesRestablecenLaVelocidad() {
        // Si venía acelerando por un desfase y el host pausa o salta, vuelve a 1.0.
        assertEquals(1f, planSync(SyncMessage(SyncType.PAUSE, 5_000, null), 5_000).speed)
        assertEquals(1f, planSync(SyncMessage(SyncType.PLAY, 5_000, null), 5_000).speed)
        assertEquals(1f, planSync(SyncMessage(SyncType.SEEK, 50_000, null), 5_000).speed)
    }

    // --- dónde está el host ahora / corrección al terminar de cargar ---

    @Test
    fun laReferenciaDelHostRecuerdaSiReproducia() {
        val play = updateHostReference(null, SyncMessage(SyncType.PLAY, 10_000, null), nowMs = 500)
        assertEquals(HostReference(10_000, 500, true), play)
        // Un seek no dice si el host reproduce: conserva lo anterior.
        val seek = updateHostReference(play, SyncMessage(SyncType.SEEK, 90_000, null), nowMs = 800)
        assertEquals(HostReference(90_000, 800, true), seek)
        val pause = updateHostReference(seek, SyncMessage(SyncType.PAUSE, 95_000, null), nowMs = 900)
        assertEquals(HostReference(95_000, 900, false), pause)
        assertEquals(false, updateHostReference(pause, SyncMessage(SyncType.SEEK, 5_000, null), 950).playing)
        // Sin referencia previa, un seek deja "no se sabe".
        assertNull(updateHostReference(null, SyncMessage(SyncType.SEEK, 5_000, null), 0).playing)
        // El heartbeat sí lo dice.
        assertEquals(true, updateHostReference(pause, beat(1_000, paused = false), 0).playing)
    }

    @Test
    fun referenciaInicialDesdeRoomData() {
        assertEquals(HostReference(125_400, 7, true), hostReferenceFrom(RoomPosition(125_400, false), 7))
        assertEquals(HostReference(0, 7, false), hostReferenceFrom(RoomPosition(0, true), 7))
    }

    @Test
    fun laPosicionEstimadaAvanzaSoloSiElHostReproducia() {
        assertEquals(13_000L, estimateHostPosition(HostReference(10_000, 1_000, true), nowMs = 4_000))
        assertEquals(10_000L, estimateHostPosition(HostReference(10_000, 1_000, false), nowMs = 4_000))
        assertEquals(10_000L, estimateHostPosition(HostReference(10_000, 1_000, null), nowMs = 4_000))
        assertEquals(10_000L, estimateHostPosition(HostReference(10_000, 5_000, true), nowMs = 4_000)) // reloj hacia atrás: no retrocede
    }

    @Test
    fun alQuedarListaSaltaADondeEstaElHostAhora() {
        // El host estaba en 60 s hace 1.5 s (lo que tardó en cargar la app): ahora va por 61.5 s; la app quedó en 60 s.
        val ref = HostReference(60_000, 1_000, true)
        assertEquals(61_500L, planReadyResync(ref, localPositionMs = 60_000, nowMs = 2_500))
    }

    @Test
    fun alQuedarListaNoSaltaSiYaEstaCerca() {
        val ref = HostReference(60_000, 1_000, true)
        assertNull(planReadyResync(ref, localPositionMs = 61_400, nowMs = 2_500))
        assertNull(planReadyResync(ref, localPositionMs = 61_000, nowMs = 2_500)) // justo en el umbral (500 ms)
        assertEquals(61_500L, planReadyResync(ref, localPositionMs = 60_999, nowMs = 2_500))
    }

    @Test
    fun alQuedarListaConElHostEnPausaVaALaPosicionDeLaPausa() {
        val ref = HostReference(60_000, 1_000, false)
        assertEquals(60_000L, planReadyResync(ref, localPositionMs = 58_000, nowMs = 9_000))
        assertNull(planReadyResync(ref, localPositionMs = 60_300, nowMs = 9_000))
    }

    @Test
    fun alQuedarListaSinSaberSiElHostReproduceONoHayReferenciaNoHaceNada() {
        assertNull(planReadyResync(null, 0, 1_000))
        assertNull(planReadyResync(HostReference(60_000, 0, null), 0, 1_000))
        assertEquals(500L, READY_RESYNC_THRESHOLD_MS)
    }

    // --- barra de solo lectura ---

    @Test
    fun formatoDeTiempo() {
        assertEquals("0:00", formatPlaybackTime(0))
        assertEquals("0:09", formatPlaybackTime(9_999))
        assertEquals("1:05", formatPlaybackTime(65_000))
        assertEquals("59:59", formatPlaybackTime(3_599_000))
        assertEquals("1:00:00", formatPlaybackTime(3_600_000))
        assertEquals("2:03:07", formatPlaybackTime(7_387_000))
        assertEquals("0:00", formatPlaybackTime(-1_000))
    }

    @Test
    fun fraccionDeProgreso() {
        assertEquals(0f, progressFraction(5_000, 0))
        assertEquals(0f, progressFraction(5_000, -1))
        assertEquals(0.25f, progressFraction(25_000, 100_000))
        assertEquals(1f, progressFraction(150_000, 100_000)) // posición pasada de la duración: se acota
        assertEquals(0f, progressFraction(-10, 100_000))
    }

    // --- Fase 3C: lo que emite el host ---

    @Test
    fun nombresDeTipoSonLosDeLaWeb() {
        assertEquals(listOf("play", "pause", "seek", "heartbeat"), SyncType.entries.map { it.wire })
    }

    @Test
    fun playPauseYSeekSeEmitenConTipoYSegundosSinPaused() {
        val play = toSyncPayload(SyncMessage(SyncType.PLAY, 90_250, null))
        assertEquals(setOf("type", "time"), play.keySet())
        assertEquals("play", play.getString("type"))
        assertEquals(90.25, play.getDouble("time"), 0.0)
        assertEquals("pause", toSyncPayload(SyncMessage(SyncType.PAUSE, 3_000, null)).getString("type"))
        assertEquals(600.0, toSyncPayload(SyncMessage(SyncType.SEEK, 600_000, null)).getDouble("time"), 0.0)
    }

    @Test
    fun elHeartbeatSeEmiteConPaused() {
        val reproduciendo = toSyncPayload(SyncMessage(SyncType.HEARTBEAT, 10_500, paused = false))
        assertEquals(setOf("type", "time", "paused"), reproduciendo.keySet())
        assertEquals(false, reproduciendo.getBoolean("paused"))
        assertEquals(10.5, reproduciendo.getDouble("time"), 0.0)
        assertEquals(true, toSyncPayload(SyncMessage(SyncType.HEARTBEAT, 0, paused = true)).getBoolean("paused"))
        // Sin dato de pausa no se inventa uno.
        assertFalse(toSyncPayload(SyncMessage(SyncType.HEARTBEAT, 0, paused = null)).has("paused"))
    }

    @Test
    fun paused_soloViajaEnElHeartbeat() {
        // El server solo lee `paused` del heartbeat; en los demás tipos no se manda aunque venga cargado.
        assertFalse(toSyncPayload(SyncMessage(SyncType.PLAY, 1_000, paused = false)).has("paused"))
    }

    @Test
    fun loQueEmiteLaAppLoEntiendeElParserDeLaApp() {
        // Mismo formato de ida y vuelta: lo que emite un host Android es lo que parsea un invitado Android.
        val mensajes = listOf(
            SyncMessage(SyncType.PLAY, 90_250, null),
            SyncMessage(SyncType.PAUSE, 0, null),
            SyncMessage(SyncType.SEEK, 5_400_123, null),
            SyncMessage(SyncType.HEARTBEAT, 12_345, false),
            SyncMessage(SyncType.HEARTBEAT, 12_345, true),
        )
        for (m in mensajes) assertEquals(m, parseSyncMessage(toSyncPayload(m)))
    }

    @Test
    fun tiempoNegativoSeEmiteComoCero() {
        assertEquals(0.0, toSyncPayload(SyncMessage(SyncType.SEEK, -50, null)).getDouble("time"), 0.0)
    }

    @Test
    fun elHeartbeatEsCadaCuatroSegundos() {
        assertEquals(4_000L, HEARTBEAT_INTERVAL_MS)
    }

    @Test
    fun soloSeEmiteSiEsHostYHayConexion() {
        assertTrue(canEmitSync(isHost = true, isConnected = true))
        assertFalse(canEmitSync(isHost = true, isConnected = false)) // reconectando: no se encola
        assertFalse(canEmitSync(isHost = false, isConnected = true)) // invitado, o rol sin confirmar
        assertFalse(canEmitSync(isHost = false, isConnected = false))
    }

    @Test
    fun saltoDeLaBarraDelHost() {
        assertEquals(0L, seekTargetMs(0f, 100_000))
        assertEquals(25_000L, seekTargetMs(0.25f, 100_000))
        assertEquals(100_000L, seekTargetMs(1f, 100_000))
        assertEquals(100_000L, seekTargetMs(1.7f, 100_000)) // fuera de rango: se acota
        assertEquals(0L, seekTargetMs(-0.2f, 100_000))
        assertEquals(0L, seekTargetMs(0.5f, 0)) // duración desconocida: no hay a dónde saltar
    }

    @Test
    fun bufferingSoloSiQuiereReproducirYSeQuedoSinDatos() {
        assertTrue(shouldReportBuffering(hasVideo = true, hasError = false, playWhenReady = true, isBufferingState = true))
        // Cargando con la sala en pausa: nadie está esperando.
        assertFalse(shouldReportBuffering(true, false, playWhenReady = false, isBufferingState = true))
        assertFalse(shouldReportBuffering(true, false, true, isBufferingState = false))
        assertFalse(shouldReportBuffering(hasVideo = false, hasError = false, playWhenReady = true, isBufferingState = true))
        assertFalse(shouldReportBuffering(true, hasError = true, playWhenReady = true, isBufferingState = true))
    }

    // --- Reconexión con el video ya cargado ---

    @Test
    fun hostQueVuelveSeAlineaConLaSala() {
        // Caso real: la app se fue pausada en 192 s, otro host llevó la sala a 3347 s reproduciendo.
        val plan = planRejoin(RoomPosition(timeMs = 3_347_948, paused = false), localPositionMs = 192_175)
        assertEquals(3_347_948L, plan.seekToMs)
        assertTrue(plan.playWhenReady)
    }

    @Test
    fun siYaEstaCercaNoSalta() {
        assertNull(planRejoin(RoomPosition(10_300, paused = false), localPositionMs = 10_000).seekToMs)
        assertEquals(10_501L, planRejoin(RoomPosition(10_501, paused = true), localPositionMs = 10_000).seekToMs)
    }

    @Test
    fun laSalaEnPausaDejaElVideoEnPausa() {
        val plan = planRejoin(RoomPosition(5_000, paused = true), localPositionMs = 90_000)
        assertEquals(5_000L, plan.seekToMs)
        assertFalse(plan.playWhenReady)
    }

    @Test
    fun tambienSeAlineaHaciaAtras() {
        // Si la sala retrocedió (otro host saltó atrás), la app no la empuja hacia adelante.
        assertEquals(1_000L, planRejoin(RoomPosition(1_000, paused = false), localPositionMs = 90_000).seekToMs)
    }
}
