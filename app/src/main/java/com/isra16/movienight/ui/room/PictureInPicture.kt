package com.isra16.movienight.ui.room

import android.app.AppOpsManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import com.isra16.movienight.net.PipRequest

/** Tag de Logcat para el orden de eventos de la ventana flotante (`adb logcat -s MovieNightPip`). */
internal const val PIP_TAG = "MovieNightPip"

/**
 * Lo que implementa la actividad para que la pantalla de la sala le diga si una ventana flotante tiene sentido
 * ahora ([PipRequest]). La actividad lo traduce a los parámetros de PiP del sistema (ver `MainActivity`).
 */
interface PipHost {
    fun updatePip(request: PipRequest)
}

/** Valor de `AppOpsManager.OPSTR_PICTURE_IN_PICTURE`, escrito a mano para no depender de que la constante sea pública. */
private const val OPSTR_PICTURE_IN_PICTURE = "android:picture_in_picture"

/**
 * `true` si el teléfono tiene la función de PiP y la persona no la desactivó para esta app en Ajustes. Si algo
 * falla al consultar, se asume que está disponible: entrar es lo que de verdad lo confirma, y si falla la app
 * sigue como siempre (pausa al salir).
 */
fun Context.isPipAvailable(): Boolean {
    if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
    val appOps = getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return true
    return try {
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(OPSTR_PICTURE_IN_PICTURE, Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(OPSTR_PICTURE_IN_PICTURE, Process.myUid(), packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        Log.w(PIP_TAG, "no se pudo consultar el permiso de PiP: $e")
        true
    }
}

/** `true` si la pantalla está encendida (no en reposo). */
fun Context.isScreenInteractive(): Boolean =
    (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive != false

/** La actividad que contiene este contexto, si hay una. */
tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * `true` mientras la actividad está dentro de la ventana flotante. Arranca con el valor real de la actividad
 * (puede haberse recreado estando ya en PiP) y sigue los cambios de modo.
 */
@Composable
fun rememberIsInPictureInPicture(activity: ComponentActivity?): Boolean {
    var inPip by remember(activity) { mutableStateOf(activity?.isInPictureInPictureMode == true) }
    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { info -> inPip = info.isInPictureInPictureMode }
        activity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity?.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return inPip
}
