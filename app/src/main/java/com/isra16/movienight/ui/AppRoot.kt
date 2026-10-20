package com.isra16.movienight.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.isra16.movienight.auth.AuthViewModel
import com.isra16.movienight.auth.SessionState
import com.isra16.movienight.auth.SessionViewModel
import com.isra16.movienight.net.StartupScreen
import com.isra16.movienight.net.startupScreenFor
import com.isra16.movienight.room.RoomViewModel
import com.isra16.movienight.ui.auth.AuthBackdrop
import com.isra16.movienight.ui.auth.AuthScreenLayout
import com.isra16.movienight.ui.auth.GlassButton
import com.isra16.movienight.ui.auth.HomeBackdrop
import com.isra16.movienight.ui.auth.ForgotPasswordScreen
import com.isra16.movienight.ui.auth.LoginScreen
import com.isra16.movienight.ui.auth.RegisterScreen
import com.isra16.movienight.ui.home.HomeScreen
import com.isra16.movienight.ui.room.RoomScreen

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT_PASSWORD = "forgot-password"
    const val HOME = "home"
    const val ROOM = "room/{${RoomViewModel.ROOM_ID_ARG}}"

    fun room(roomId: String) = "room/$roomId"
}

/**
 * Raíz de la app: la app exige cuenta, así que lo que se ve depende del estado de sesión. Cada
 * estado tiene su propio NavHost, de modo que al iniciar o cerrar sesión la pila de navegación se
 * reinicia sola (no se puede volver atrás a login estando adentro, ni al revés).
 */
@Composable
fun AppRoot(darkTheme: Boolean, onDarkThemeChange: (Boolean) -> Unit) {
    val sessionVm: SessionViewModel = viewModel()
    val introVm: IntroViewModel = viewModel()
    val state by sessionVm.state.collectAsState()

    // La intro y la comprobación de sesión corren a la vez; `startupScreenFor` decide cuál se ve (ver `net/StartupLogic.kt`).
    val s = state
    when (startupScreenFor(introVm.finished, s)) {
        StartupScreen.INTRO -> IntroScreen()
        StartupScreen.LOADING -> LoadingScreen()
        StartupScreen.CONNECTION_ERROR ->
            if (s is SessionState.Unreachable) ConnectionErrorScreen(s.message, sessionVm.serverUrl, sessionVm::retry)
        StartupScreen.AUTH -> AuthNavHost()
        StartupScreen.MAIN ->
            if (s is SessionState.LoggedIn) {
                MainNavHost(
                    session = s,
                    isLoggingOut = sessionVm.isLoggingOut,
                    onLogout = sessionVm::logout,
                    darkTheme = darkTheme,
                    onDarkThemeChange = onDarkThemeChange,
                )
            }
    }
}

@Composable
private fun AuthNavHost() {
    val nav = rememberNavController()
    val authVm: AuthViewModel = viewModel()
    // La hoja de vidrio (fondo borroso quieto con su velo) vive por encima del NavHost (Fase 8, paso 3): así no
    // parpadea al pasar de una pantalla a otra.
    AuthBackdrop {
        NavHost(navController = nav, startDestination = Routes.LOGIN) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    vm = authVm,
                    onGoRegister = { nav.navigate(Routes.REGISTER) },
                    onGoForgot = { nav.navigate(Routes.FORGOT_PASSWORD) },
                )
            }
            composable(Routes.REGISTER) {
                RegisterScreen(vm = authVm, onBack = { nav.popBackStack() })
            }
            composable(Routes.FORGOT_PASSWORD) {
                ForgotPasswordScreen(vm = authVm, onBack = { nav.popBackStack() })
            }
        }
    }
}

@Composable
private fun MainNavHost(
    session: SessionState.LoggedIn,
    isLoggingOut: Boolean,
    onLogout: () -> Unit,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            // El fondo de vidrio de Home (Fase 8, paso 4) vive solo aquí: la sala no lo lleva.
            HomeBackdrop {
                HomeScreen(
                    email = session.email,
                    isLoggingOut = isLoggingOut,
                    onLogout = onLogout,
                    darkTheme = darkTheme,
                    onDarkThemeChange = onDarkThemeChange,
                    onOpenRoom = { roomId -> nav.navigate(Routes.room(roomId)) },
                )
            }
        }
        // El id va como argumento de la ruta: RoomViewModel lo lee del SavedStateHandle.
        composable(Routes.ROOM) {
            // Mismo fondo de vidrio que Home (Fase 8, paso 5a). Va aquí, fuera de RoomScreen, para que no se recargue al entrar o
            // salir de la pantalla completa ni se mueva con el teclado. La caja del video sigue negra y sin vidrio.
            HomeBackdrop {
                RoomScreen(onLeave = { nav.popBackStack() })
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * Pantalla de «no se pudo abrir la app» (servidor inalcanzable o con un problema), con el mismo estilo que el login
 * (Fase 8): la hoja de vidrio de [AuthBackdrop], el título en la tipografía de las pantallas de entrada y el botón de
 * vidrio. El texto que explica el problema va de subtítulo; la dirección del servidor, debajo.
 */
@Composable
private fun ConnectionErrorScreen(message: String, serverUrl: String, onRetry: () -> Unit) {
    AuthBackdrop {
        AuthScreenLayout(title = "No se pudo abrir la app", subtitle = message) {
            // La URL a la vista ayuda a detectar un movienight.baseUrl mal configurado.
            Text(
                "Servidor: $serverUrl",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            GlassButton("Reintentar", busy = false, onClick = onRetry)
        }
    }
}
