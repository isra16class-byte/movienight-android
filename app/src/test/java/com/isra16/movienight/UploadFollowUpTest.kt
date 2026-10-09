package com.isra16.movienight

import com.isra16.movienight.net.ALREADY_UPLOADED_NOTE
import com.isra16.movienight.net.FollowUpOutcome
import com.isra16.movienight.net.RoomVideoFailure
import com.isra16.movienight.net.UploadGoal
import com.isra16.movienight.net.UploadState
import com.isra16.movienight.net.canCancel
import com.isra16.movienight.net.doneMessage
import com.isra16.movienight.net.finishingLabel
import com.isra16.movienight.net.isBusy
import com.isra16.movienight.net.stateAfterFollowUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fase 4B: estados de la subida con un paso siguiente (crear sala / cambiar el video de la sala). */
class UploadFollowUpTest {

    private val all: List<UploadState> = listOf(
        UploadState.Idle,
        UploadState.Preparing("a.mp4"),
        UploadState.Uploading("a.mp4", 1, 10),
        UploadState.Finishing("a.mp4", UploadGoal.CREATE_ROOM),
        UploadState.Done("a.mp4"),
        UploadState.Failed("a.mp4", "x", true),
    )

    @Test
    fun ocupadoMientrasSePreparaSubeOTermina() {
        assertEquals(
            listOf(false, true, true, true, false, false),
            all.map { it.isBusy() },
        )
    }

    @Test
    fun soloSePuedeCancelarAntesDelPasoSiguiente() {
        // Con Finishing no: el POST que crea la sala dura un instante y cortarlo dejaría en duda si se creó.
        assertEquals(
            listOf(false, true, true, false, false, false),
            all.map { it.canCancel() },
        )
    }

    @Test
    fun loDeLa4ASigueIgualPorDefecto() {
        assertEquals(UploadGoal.LIBRARY, UploadState.Done("a.mp4").goal)
        assertNull(UploadState.Done("a.mp4").roomId)
        val failed = UploadState.Failed("a.mp4", "x", false)
        assertEquals(UploadGoal.LIBRARY, failed.goal)
        assertFalse(failed.alreadyUploaded)
    }

    @Test
    fun textosDeCadaObjetivo() {
        assertEquals("Creando la sala…", finishingLabel(UploadGoal.CREATE_ROOM))
        assertEquals("Cambiando el video de la sala…", finishingLabel(UploadGoal.CHANGE_ROOM_VIDEO))
        assertEquals("Listo: a.mp4 ya está en la biblioteca.", doneMessage(UploadState.Done("a.mp4")))
        assertEquals("Sala creada con a.mp4.", doneMessage(UploadState.Done("a.mp4", UploadGoal.CREATE_ROOM, "r1")))
        assertEquals(
            "Listo: la sala ahora tiene a.mp4.",
            doneMessage(UploadState.Done("a.mp4", UploadGoal.CHANGE_ROOM_VIDEO)),
        )
    }

    @Test
    fun alTerminarElPasoSiguienteBienQuedaListoConLaSala() {
        val state = stateAfterFollowUp("a.mp4", UploadGoal.CREATE_ROOM, FollowUpOutcome.Done("a1b2c3"))
        assertEquals(UploadState.Done("a.mp4", UploadGoal.CREATE_ROOM, "a1b2c3"), state)
    }

    @Test
    fun siFallaElPasoSiguienteElVideoYaEstaSubido() {
        val failure = RoomVideoFailure("No autorizado", canRetry = false)
        val state = stateAfterFollowUp("a.mp4", UploadGoal.CHANGE_ROOM_VIDEO, FollowUpOutcome.Failed(failure))
        state as UploadState.Failed
        assertTrue("reintentar repite solo el paso, no la subida", state.alreadyUploaded)
        assertFalse(state.canRetry)
        assertEquals("No autorizado", state.message)
        assertEquals(UploadGoal.CHANGE_ROOM_VIDEO, state.goal)
        assertTrue(ALREADY_UPLOADED_NOTE.contains("biblioteca"))
    }

    @Test
    fun elReintentoDelPasoSiguienteConservaSiSePuedeReintentar() {
        val retryable = stateAfterFollowUp(
            "a.mp4",
            UploadGoal.CREATE_ROOM,
            FollowUpOutcome.Failed(RoomVideoFailure("sin conexión", canRetry = true)),
        ) as UploadState.Failed
        assertTrue(retryable.canRetry)
        assertTrue(retryable.alreadyUploaded)
    }
}
