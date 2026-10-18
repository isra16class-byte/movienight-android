package com.isra16.movienight.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.ImeAction
import com.isra16.movienight.auth.AuthValidation
import com.isra16.movienight.auth.AuthViewModel

@Composable
fun LoginScreen(vm: AuthViewModel, onGoRegister: () -> Unit, onGoForgot: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.clearMessages() }

    // Los errores de campo aparecen recién después del primer intento de enviar.
    val emailError = if (submitted) AuthValidation.emailError(email) else null
    val passwordError = if (submitted) AuthValidation.loginPasswordError(password) else null

    fun submit() {
        submitted = true
        if (AuthValidation.emailError(email) == null && AuthValidation.loginPasswordError(password) == null) {
            vm.login(email, password)
        }
    }

    AuthScreenLayout(title = "MovieNight", subtitle = "Inicia sesión para entrar a tus salas.") {
        GlassFieldGroup {
            GlassEmailField(email, { email = it }, emailError, enabled = !vm.isBusy)
            GlassFieldDivider()
            GlassPasswordField(
                label = "Contraseña",
                value = password,
                onValueChange = { password = it },
                error = passwordError,
                enabled = !vm.isBusy,
                showPassword = showPassword,
                onToggleShow = { showPassword = !showPassword },
                imeAction = ImeAction.Done,
                onDone = ::submit,
            )
        }
        ErrorText(vm.errorMessage)
        GlassButton("Iniciar sesión", busy = vm.isBusy, onClick = ::submit)
        // Los dos enlaces van juntos en su propia columna: el panel separa 12 dp cada hijo, y entre enlaces sobraba.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlassLinkButton("¿Olvidaste tu contraseña?", enabled = !vm.isBusy, onClick = onGoForgot)
            GlassLinkButton("¿No tienes cuenta? Regístrate", enabled = !vm.isBusy, onClick = onGoRegister, emphasis = "Regístrate")
        }
    }
}
