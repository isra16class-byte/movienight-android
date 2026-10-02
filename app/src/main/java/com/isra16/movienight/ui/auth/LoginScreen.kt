package com.isra16.movienight.ui.auth

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

    AuthScreenLayout(title = "MovieNight", subtitle = "Iniciá sesión para entrar a tus salas.") {
        EmailField(email, { email = it }, emailError, enabled = !vm.isBusy)
        PasswordField(
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
        ErrorText(vm.errorMessage)
        PrimaryButton("Iniciar sesión", busy = vm.isBusy, onClick = ::submit)
        TextButton(onClick = onGoForgot, enabled = !vm.isBusy) { Text("¿Olvidaste tu contraseña?") }
        TextButton(onClick = onGoRegister, enabled = !vm.isBusy) { Text("¿No tenés cuenta? Registrate") }
    }
}
