package com.isra16.movienight

import com.isra16.movienight.net.EVENT_SEEK_THRESHOLD_MS
import com.isra16.movienight.net.HARD_SEEK_COOLDOWN_MS
import com.isra16.movienight.net.HARD_SEEK_COOLDOWN_OVERRIDE_MS
import com.isra16.movienight.net.HARD_SEEK_THRESHOLD_MS
import com.isra16.movienight.net.RoomPosition
import com.isra16.movienight.net.SOFT_DRIFT_THRESHOLD_MS
import com.isra16.movienight.net.SPEED_CATCH_UP
import com.isra16.movienight.net.SPEED_SLOW_DOWN
import com.isra16.movienight.net.SyncMessage
import com.isra16.movienight.net.SyncPlan
import com.isra16.movienight.net.SyncType
import com.isra16.movienight.net.formatPlaybackTime
import com.isra16.movienight.net.parseRoomPosition
import com.isra16.movienight.net.parseSyncMessage
import com.isra16.movienight.net.planSync
import com.isra16.movienight.net.progressFraction
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
        assertEquals(1_500L, HARD_SEEK_THRESHOLD_MS)
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
        // Justo pasado el umbral chico (501 ms) ya corrige; justo en el umbral de salto (1.5 s) todavía no salta.
        assertEquals(SyncPlan(null, SPEED_CATCH_UP, true), planSync(beat(60_000), localPositionMs = 59_499))
        assertEquals(SyncPlan(null, SPEED_CATCH_UP, true), planSync(beat(60_000), localPositionMs = 58_500))
    }

    @Test
    fun heartbeatConDesfaseGrandeSalta() {
        assertEquals(SyncPlan(60_000, 1f, true), planSync(beat(60_000), localPositionMs = 58_499)) // atrasada, recién pasado el umbral
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
}
