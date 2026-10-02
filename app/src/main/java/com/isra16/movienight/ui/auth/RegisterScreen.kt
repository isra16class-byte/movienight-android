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
fun RegisterScreen(vm: AuthViewModel, onBack: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.clearMessages() }

    val emailError = if (submitted) AuthValidation.emailError(email) else null
    val passwordError = if (submitted) AuthValidation.newPasswordError(password) else null
    val confirmationError = if (submitted) AuthValidation.confirmPasswordError(password, confirmation) else null

    fun submit() {
        submitted = true
        if (AuthValidation.emailError(email) == null &&
            AuthValidation.newPasswordError(password) == null &&
            AuthValidation.confirmPasswordError(password, confirmation) == null
        ) {
            vm.register(email, password)
        }
    }

    AuthScreenLayout(title = "Crear cuenta", subtitle = "Con tu cuenta podés crear salas y entrar a las de tus amigos.") {
        EmailField(email, { email = it }, emailError, enabled = !vm.isBusy)
        PasswordField(
            label = "Contraseña",
            value = password,
            onValueChange = { password = it },
            error = passwordError,
            enabled = !vm.isBusy,
            showPassword = showPassword,
            onToggleShow = { showPassword = !showPassword },
            hint = "Mínimo ${AuthValidation.MIN_PASSWORD_LENGTH} caracteres.",
        )
        PasswordField(
            label = "Repetir contraseña",
            value = confirmation,
            onValueChange = { confirmation = it },
            error = confirmationError,
            enabled = !vm.isBusy,
            showPassword = showPassword,
            onToggleShow = { showPassword = !showPassword },
            imeAction = ImeAction.Done,
            onDone = ::submit,
        )
        ErrorText(vm.errorMessage)
        PrimaryButton("Crear cuenta", busy = vm.isBusy, onClick = ::submit)
        TextButton(onClick = onBack, enabled = !vm.isBusy) { Text("Ya tengo cuenta") }
    }
}
