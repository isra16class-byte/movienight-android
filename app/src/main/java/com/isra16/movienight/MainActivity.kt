package com.isra16.movienight

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleEventObserver
import com.isra16.movienight.net.PipRequest
import com.isra16.movienight.net.shouldEnterPip
import com.isra16.movienight.ui.AppRoot
import com.isra16.movienight.ui.room.PIP_TAG
import com.isra16.movienight.ui.room.PipHost
import com.isra16.movienight.ui.room.isPipAvailable
import com.isra16.movienight.ui.theme.MovieNightTheme

class MainActivity : ComponentActivity(), PipHost {

    /** Lo último que pidió la pantalla de la sala (`OFF` fuera de la sala). Decide si se entra en PiP al dejar la app. */
    private var pipRequest = PipRequest.OFF

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // [DEBUG-X] Temporal: ciclo de vida y cambio de modo PiP de la actividad, para ver qué pasa al cerrar la ventana con la X.
        val debugId = System.identityHashCode(this)
        Log.d(PIP_TAG, "[DEBUG-X] ACTIVITY id=$debugId onCreate recreada=${savedInstanceState != null}")
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            Log.d(
                PIP_TAG,
                "[DEBUG-X] ACTIVITY id=$debugId $event pip=$isInPictureInPictureMode finishing=$isFinishing cambioConfig=$isChangingConfigurations",
            )
        })
        addOnPictureInPictureModeChangedListener(
            Consumer<PictureInPictureModeChangedInfo> { info ->
                Log.d(PIP_TAG, "[DEBUG-X] ACTIVITY id=$debugId MODO_PIP=${info.isInPictureInPictureMode} lifecycle=${lifecycle.currentState}")
            },
        )
        enableEdgeToEdge()
        setContent {
            MovieNightTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }

    // --- Ventana flotante (Picture-in-Picture, Fase 7A) --------------------------------------------

    /**
     * Android 12+ entra solo en PiP al dejar la app si la entrada automática está activada, así que los parámetros
     * se actualizan cada vez que cambia la condición (en la sala, video cargado y reproduciéndose) y se desactivan
     * al salir de la sala: si no, la app entraría en PiP desde la pantalla principal. Antes de Android 12 se entra a
     * mano en [onUserLeaveHint]. Los dos caminos usan la misma función, [shouldEnterPip].
     */
    override fun updatePip(request: PipRequest) {
        if (request == pipRequest) return
        pipRequest = request
        val enter = canEnterPip()
        Log.d(PIP_TAG, "parámetros PiP: entrada=$enter aspecto=${request.aspect} condiciones=${request.conditions}")
        try {
            setPictureInPictureParams(buildPipParams(autoEnter = enter))
        } catch (e: Exception) {
            // Sin PiP la app se comporta como siempre: al dejar de verse, el video se pausa.
            Log.w(PIP_TAG, "no se pudieron fijar los parámetros de PiP: $e")
        }
    }

    /** Antes de Android 12: la persona dejó la app (Inicio o cambio de app). */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val enter = canEnterPip()
        Log.d(PIP_TAG, "onUserLeaveHint sdk=${Build.VERSION.SDK_INT} entrar=$enter condiciones=${pipRequest.conditions}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S || !enter) return
        try {
            val entered = enterPictureInPictureMode(buildPipParams(autoEnter = false))
            Log.d(PIP_TAG, "enterPictureInPictureMode -> $entered")
        } catch (e: Exception) {
            // IllegalStateException (PiP no disponible o desactivado) o IllegalArgumentException: sigue como hoy.
            Log.w(PIP_TAG, "no se pudo entrar en PiP: $e")
        }
    }

    private fun canEnterPip(): Boolean = shouldEnterPip(pipRequest.conditions, available = isPipAvailable())

    private fun buildPipParams(autoEnter: Boolean): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(pipRequest.aspect.width, pipRequest.aspect.height))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAutoEnterEnabled(autoEnter)
        return builder.build()
    }
}
