package com.isra16.movienight.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import com.isra16.movienight.R
import com.isra16.movienight.net.INTRO_ANIMATION_MS

/**
 * Intro de la app (Fase 8, paso 2). MARCADOR: el nombre con un fundido y una escala suave, solo con colores y
 * tipografía del tema. El logo y las tipografías finales llegan en otro paso: para reemplazarla basta con cambiar
 * el contenido de esta función (el tiempo total lo manda `INTRO_DURATION_MS`, no esta pantalla).
 */
@Composable
fun IntroScreen() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(INTRO_ANIMATION_MS, easing = FastOutSlowInEasing))
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.graphicsLayer {
                val p = progress.value
                alpha = p
                val scale = 0.85f + 0.15f * p
                scaleX = scale
                scaleY = scale
            },
        )
    }
}
