package com.isra16.movienight

import com.isra16.movienight.net.ALREADY_UPLOADED_NOTE
import com.isra16.movienight.net.UploadGoal
import com.isra16.movienight.net.UploadState
import com.isra16.movienight.net.VisibilityCounter
import com.isra16.movienight.net.shouldAskNotificationPermission
import com.isra16.movienight.net.uploadNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadNotificationLogicTest {

    @Test
    fun conLaAppAlaVistaNoSeAvisaNada() {
        assertNull(uploadNotice(UploadState.Done("peli.mp4"), appVisible = true))
        assertNull(uploadNotice(UploadState.Failed("peli.mp4", "Se cortó.", true), appVisible = true))
    }

    @Test
    fun subidaALaBibliotecaTerminada() {
        val n = uploadNotice(UploadState.Done("peli.mp4", UploadGoal.LIBRARY), appVisible = false)!!
        assertEquals("Subida terminada", n.title)
        assertEquals("Listo: peli.mp4 ya está en la biblioteca.", n.text)
    }

    @Test
    fun subirYCrearSalaTerminado() {
        val n = uploadNotice(UploadState.Done("peli.mp4", UploadGoal.CREATE_ROOM, roomId = "abc"), appVisible = false)!!
        assertEquals("Sala lista", n.title)
        assertEquals("Sala creada con peli.mp4.", n.text)
    }

    @Test
    fun cambiarElVideoDeLaSalaTerminado() {
        val n = uploadNotice(UploadState.Done("peli.mp4", UploadGoal.CHANGE_ROOM_VIDEO), appVisible = false)!!
        assertEquals("Video de la sala cambiado", n.title)
        assertEquals("Listo: la sala ahora tiene peli.mp4.", n.text)
    }

    @Test
    fun fallaDeLaSubida() {
        val n = uploadNotice(UploadState.Failed("peli.mp4", "Se cortó la conexión.", true), appVisible = false)!!
        assertEquals("No se pudo subir el video", n.title)
        assertEquals("Se cortó la conexión.", n.text)
    }

    @Test
    fun fallaDelPasoSiguienteConElVideoYaSubido() {
        val failed = UploadState.Failed(
            "peli.mp4", "No se pudo crear la sala.", canRetry = true,
            alreadyUploaded = true, goal = UploadGoal.CREATE_ROOM,
        )
        val n = uploadNotice(failed, appVisible = false)!!
        assertEquals("La subida quedó a medias", n.title)
        assertEquals("No se pudo crear la sala. $ALREADY_UPLOADED_NOTE", n.text)
    }

    @Test
    fun lasSubidasEnCursoOSinEmpezarNoAvisan() {
        assertNull(uploadNotice(UploadState.Idle, appVisible = false))
        assertNull(uploadNotice(UploadState.Preparing("a.mp4"), appVisible = false))
        assertNull(uploadNotice(UploadState.Uploading("a.mp4", 10, 100), appVisible = false))
        assertNull(uploadNotice(UploadState.Finishing("a.mp4", UploadGoal.CREATE_ROOM), appVisible = false))
    }

    @Test
    fun elPermisoSoloSePideEnAndroid13YMasSiNoEstaActivoYNuncaSePregunto() {
        assertTrue(shouldAskNotificationPermission(sdkInt = 33, notificationsEnabled = false, alreadyAsked = false))
        assertTrue(shouldAskNotificationPermission(sdkInt = 36, notificationsEnabled = false, alreadyAsked = false))
    }

    @Test
    fun elPermisoNoSePideEnOtrosCasos() {
        // Android 12 o menos: no hay permiso que pedir.
        assertFalse(shouldAskNotificationPermission(sdkInt = 32, notificationsEnabled = false, alreadyAsked = false))
        assertFalse(shouldAskNotificationPermission(sdkInt = 26, notificationsEnabled = false, alreadyAsked = false))
        // Ya está activado.
        assertFalse(shouldAskNotificationPermission(sdkInt = 34, notificationsEnabled = true, alreadyAsked = false))
        // Ya se le preguntó una vez.
        assertFalse(shouldAskNotificationPermission(sdkInt = 34, notificationsEnabled = false, alreadyAsked = true))
    }

    @Test
    fun visibilidadDeLaApp() {
        val v = VisibilityCounter()
        assertFalse(v.isVisible)
        v.onStart()
        assertTrue(v.isVisible)
        v.onStop()
        assertFalse(v.isVisible)
    }

    @Test
    fun girarElTelefonoNoDejaElContadorEnNegativo() {
        val v = VisibilityCounter()
        v.onStart()
        // Girar: la pantalla vieja se detiene y la nueva arranca.
        v.onStop()
        v.onStart()
        assertTrue(v.isVisible)
        // Un onStop de más no puede dejar el contador por debajo de cero.
        v.onStop()
        v.onStop()
        assertFalse(v.isVisible)
        v.onStart()
        assertTrue(v.isVisible)
    }
}
