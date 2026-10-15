package com.isra16.movienight

import android.app.PictureInPictureParams
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.isra16.movienight.net.PipRequest
import com.isra16.movienight.net.shouldEnterPip
import com.isra16.movienight.net.themeModeFromSwitch
import com.isra16.movienight.ui.AppRoot
import com.isra16.movienight.ui.room.PIP_TAG
import com.isra16.movienight.ui.room.PipHost
import com.isra16.movienight.ui.room.isPipAvailable
import com.isra16.movienight.ui.theme.MovieNightDarkColors
import com.isra16.movienight.ui.theme.MovieNightLightColors
import com.isra16.movienight.ui.theme.MovieNightTheme

class MainActivity : ComponentActivity(), PipHost {

    /** Lo último que pidió la pantalla de la sala (`OFF` fuera de la sala). Decide si se entra en PiP al dejar la app. */
    private var pipRequest = PipRequest.OFF

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Modo de color (Fase 8): lo elige la persona con el interruptor de la pantalla principal y se recuerda.
        // No sigue el modo oscuro del teléfono. El primer arranque es oscuro (ver `DEFAULT_THEME_MODE`).
        val themePrefs = (application as MovieNightApp).container.themePrefs
        val initialMode = themePrefs.mode
        applyAppearance(initialMode.isDark)
        setContent {
            var mode by remember { mutableStateOf(initialMode) }
            MovieNightTheme(darkTheme = mode.isDark) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(
                        darkTheme = mode.isDark,
                        onDarkThemeChange = { dark ->
                            val next = themeModeFromSwitch(dark)
                            mode = next
                            themePrefs.mode = next
                            applyAppearance(next.isDark)
                        },
                    )
                }
            }
        }
    }

    /**
     * Pone a juego con el modo de la app lo que no es Compose. (1) Los iconos de las barras del sistema: sin esto
     * seguirían el modo del teléfono y podrían quedar oscuros sobre fondo oscuro (o claros sobre claro). (2) El
     * fondo de la ventana, que se ve detrás de la app en el primer cuadro y mientras el teclado se mueve.
     * `enableEdgeToEdge` se puede llamar más de una vez; aquí se llama al arrancar y al cambiar el modo.
     *
     * Las dos barras son transparentes (Fase 8, paso 4k): la de navegación usaba `SystemBarStyle.auto`, que en Android 10+ deja
     * activado `isNavigationBarContrastEnforced` y el sistema pintaba su propio velo (blanco en modo claro) con la navegación de 3
     * botones. `dark` y `light` lo desactivan y fijan el color de los iconos según el modo de la app (claros en oscuro, oscuros en
     * claro). Sin el velo, nada garantiza el contraste de los botones del sistema sobre lo que haya detrás.
     */
    private fun applyAppearance(dark: Boolean) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            navigationBarStyle = if (dark) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            },
        )
        val colors = if (dark) MovieNightDarkColors else MovieNightLightColors
        window.setBackgroundDrawable(ColorDrawable(colors.background.toArgb()))
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
