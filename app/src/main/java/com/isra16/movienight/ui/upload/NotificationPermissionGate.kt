package com.isra16.movienight.ui.upload

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import com.isra16.movienight.MovieNightApp
import com.isra16.movienight.net.shouldAskNotificationPermission

/**
 * Pide el permiso de notificaciones (Android 13+) en el momento en que tiene sentido: justo antes de abrir
 * el selector de video para subir. Devuelve una función `gate(proceed)`: si hace falta preguntar, muestra
 * un cuadro que explica para qué es y, pase lo que pase, después llama a `proceed` (abrir el selector).
 *
 *  - "Activar avisos" abre el cuadro del sistema; se continúa con cualquier respuesta.
 *  - "Ahora no" continúa y no se vuelve a preguntar.
 *  - Cerrar el cuadro sin elegir continúa, pero sin marcarlo: la próxima subida vuelve a preguntar.
 *
 * Nunca se pide al abrir la app, y si las notificaciones ya están activadas o es Android 12 o menos no
 * muestra nada. Negarlo no cambia nada de la subida: solo no hay aviso al terminar.
 */
@Composable
fun rememberNotificationGate(): (proceed: () -> Unit) -> Unit {
    val context = LocalContext.current
    val prefs = (context.applicationContext as MovieNightApp).container.notificationPrefs
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showExplanation by remember { mutableStateOf(false) }

    fun continueUpload() {
        val next = pending
        pending = null
        showExplanation = false
        next?.invoke()
    }

    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Cualquiera sea la respuesta, ya se preguntó.
        prefs.markAsked()
        continueUpload()
    }

    if (showExplanation) {
        AlertDialog(
            onDismissRequest = { continueUpload() },
            title = { Text("¿Avisarte cuando termine la subida?") },
            text = {
                Text(
                    "Subir un video grande puede tardar. Si salís de la app mientras sube, te mandamos una " +
                        "notificación cuando termine o si falla. Solo la usamos para eso.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    showExplanation = false
                    requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Activar avisos") }
            },
            dismissButton = {
                TextButton(onClick = {
                    prefs.markAsked()
                    continueUpload()
                }) { Text("Ahora no") }
            },
        )
    }

    return { proceed ->
        val ask = shouldAskNotificationPermission(
            sdkInt = Build.VERSION.SDK_INT,
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            alreadyAsked = prefs.alreadyAsked,
        )
        if (ask) {
            pending = proceed
            showExplanation = true
        } else {
            proceed()
        }
    }
}
