package com.isra16.movienight.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Home provisional de la Sesión A: confirma la sesión. La biblioteca y las salas llegan en la Sesión B. */
@Composable
fun HomeScreen(email: String, isLoggingOut: Boolean, onLogout: () -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("MovieNight", style = MaterialTheme.typography.headlineMedium)
            Text(
                if (email.isNotBlank()) "Sesión iniciada como $email" else "Sesión iniciada",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Próximamente: tu biblioteca de videos y tus salas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onLogout, enabled = !isLoggingOut) {
                Text(if (isLoggingOut) "Cerrando sesión…" else "Cerrar sesión")
            }
        }
    }
}
