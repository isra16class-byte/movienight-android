package com.isra16.movienight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pantalla de pruebas del spike (Fase 1). Arriba el formulario (con scroll), abajo el log en vivo.
 * No es la UI final: solo sirve para comprobar login + sesión + Socket.IO contra el backend real.
 */
@Composable
fun SpikeScreen(vm: SpikeViewModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("MovieNight · spike Fase 1", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = vm.serverUrl,
                onValueChange = { vm.serverUrl = it },
                label = { Text("URL del servidor (https://…)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::health) { Text("Health") }
                OutlinedButton(onClick = vm::me) { Text("Me") }
                OutlinedButton(onClick = vm::logout) { Text("Logout") }
            }

            OutlinedTextField(
                value = vm.email,
                onValueChange = { vm.email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.password,
                onValueChange = { vm.password = it },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = vm::login) { Text("Login") }

            HorizontalDivider()

            OutlinedTextField(
                value = vm.roomId,
                onValueChange = { vm.roomId = it },
                label = { Text("ID de sala") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.username,
                onValueChange = { vm.username = it },
                label = { Text("Nombre en la sala") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.roomPassword,
                onValueChange = { vm.roomPassword = it },
                label = { Text("Contraseña de la sala (si tiene)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::connectSocket, enabled = !vm.socketConnected) { Text("Unirse") }
                OutlinedButton(onClick = vm::disconnectSocket, enabled = vm.socketConnected) { Text("Salir") }
            }
            Text(
                text = if (vm.socketConnected) "Socket: conectado" else "Socket: desconectado",
                style = MaterialTheme.typography.labelMedium,
            )

            OutlinedTextField(
                value = vm.chatText,
                onValueChange = {
                    if (it.isNotEmpty() && it != vm.chatText) vm.sendTyping()
                    vm.chatText = it
                },
                label = { Text("Mensaje") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::sendChat, enabled = vm.socketConnected) { Text("Enviar") }
                OutlinedButton(onClick = { vm.sendReaction("👍") }, enabled = vm.socketConnected) { Text("👍") }
                OutlinedButton(onClick = vm::clearLog) { Text("Limpiar log") }
            }
        }

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            items(vm.logLines) { line ->
                Text(text = line, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    }
}
