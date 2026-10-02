package com.isra16.movienight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.isra16.movienight.auth.AuthViewModel
import com.isra16.movienight.auth.SessionState
import com.isra16.movienight.auth.SessionViewModel
import com.isra16.movienight.room.RoomViewModel
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
fun AppRoot() {
    val sessionVm: SessionViewModel = viewModel()
    val state by sessionVm.state.collectAsState()

    when (val s = state) {
        SessionState.Loading -> LoadingScreen()
        is SessionState.Unreachable -> ConnectionErrorScreen(s.message, sessionVm.serverUrl, sessionVm::retry)
        SessionState.LoggedOut -> AuthNavHost()
        is SessionState.LoggedIn -> MainNavHost(
            session = s,
            isLoggingOut = sessionVm.isLoggingOut,
            onLogout = sessionVm::logout,
        )
    }
}

@Composable
private fun AuthNavHost() {
    val nav = rememberNavController()
    val authVm: AuthViewModel = viewModel()
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

@Composable
private fun MainNavHost(session: SessionState.LoggedIn, isLoggingOut: Boolean, onLogout: () -> Unit) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                email = session.email,
                isLoggingOut = isLoggingOut,
                onLogout = onLogout,
                onOpenRoom = { roomId -> nav.navigate(Routes.room(roomId)) },
            )
        }
        // El id va como argumento de la ruta: RoomViewModel lo lee del SavedStateHandle.
        composable(Routes.ROOM) {
            RoomScreen(onLeave = { nav.popBackStack() })
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ConnectionErrorScreen(message: String, serverUrl: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No se pudo abrir la app", style = MaterialTheme.typography.headlineSmall)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            // La URL a la vista ayuda a detectar un movienight.baseUrl mal configurado.
            Text(
                "Servidor: $serverUrl",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry) { Text("Reintentar") }
        }
    }
}
