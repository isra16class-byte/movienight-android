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
fun ForgotPasswordScreen(vm: AuthViewModel, onBack: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.clearMessages() }

    val emailError = if (submitted) AuthValidation.emailError(email) else null

    fun submit() {
        submitted = true
        if (AuthValidation.emailError(email) == null) vm.forgotPassword(email)
    }

    AuthScreenLayout(
        title = "Recuperar contraseña",
        subtitle = "Escribí tu email y te mandamos un link para elegir una contraseña nueva.",
    ) {
        EmailField(
            value = email,
            onValueChange = { email = it },
            error = emailError,
            enabled = !vm.isBusy,
            imeAction = ImeAction.Done,
            onDone = ::submit,
        )
        ErrorText(vm.errorMessage)
        InfoText(vm.infoMessage)
        PrimaryButton("Enviar link", busy = vm.isBusy, onClick = ::submit)
        TextButton(onClick = onBack, enabled = !vm.isBusy) { Text("Volver a iniciar sesión") }
    }
}
