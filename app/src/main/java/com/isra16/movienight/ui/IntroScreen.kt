package com.isra16.movienight.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isra16.movienight.R
import com.isra16.movienight.net.INTRO_ANIMATION_MS
import com.isra16.movienight.net.INTRO_DURATION_MS
import com.isra16.movienight.net.INTRO_EXIT_ZOOM
import com.isra16.movienight.net.INTRO_LOGO_UNITS
import com.isra16.movienight.net.INTRO_NAME_MAX_FONT_SCALE
import com.isra16.movienight.net.INTRO_NAME_TOP_UNITS
import com.isra16.movienight.net.introAdvanceFor
import com.isra16.movienight.net.introLogoSize
import com.isra16.movienight.net.introLogoTop
import com.isra16.movienight.net.introTextReserve
import com.isra16.movienight.ui.theme.AuthTitleStyle

/**
 * Intro de la app (Fase 8, paso 6): «el despeje» a pantalla completa. La pantalla arranca llena de nubes con las
 * formas del logo; se abren hacia los lados y dejan ver el logo de branding/logo.svg; el nombre sube y aparece bajo el
 * reflejo y al final aparece el eslogan. En lo que queda hasta `INTRO_DURATION_MS` la escena se acerca un poco.
 * El fondo es el verde del logo en los dos modos (no sale del tema). Los tiempos salen de `INTRO_ANIMATION_MS` e
 * `INTRO_DURATION_MS`; el tiempo total lo manda `IntroViewModel`, no esta pantalla.
 *
 * Con las animaciones del sistema en 0 se muestra el estado final, quieto. Se anuncia una sola vez como «MovieNight»;
 * el dibujo y los dos textos no se leen aparte.
 */
@Composable
fun IntroScreen() {
    val context = LocalContext.current
    val reducedMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val progress = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val zoom = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            progress.animateTo(1f, tween(INTRO_ANIMATION_MS, easing = FastOutSlowInEasing))
            val restMs = (INTRO_DURATION_MS - INTRO_ANIMATION_MS).toInt()
            if (restMs > 0) zoom.animateTo(INTRO_EXIT_ZOOM, tween(restMs, easing = FastOutSlowInEasing))
        }
    }

    val appName = stringResource(R.string.app_name)
    val tagline = stringResource(R.string.home_tagline)
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MovieNightGreen)
            .clearAndSetSemantics { contentDescription = appName },
    ) {
        val logoSize = introLogoSize(maxWidth.value, maxHeight.value)
        val logoTop = introLogoTop(maxHeight.value, logoSize, introTextReserve(density.fontScale))
        val textTop = logoTop + logoSize * (INTRO_NAME_TOP_UNITS / INTRO_LOGO_UNITS)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom.value
                    scaleY = zoom.value
                },
        ) {
            MovieNightLogo(
                progress = { progress.value },
                logoSize = logoSize.dp,
                logoTop = logoTop.dp,
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = textTop.dp)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // El nombre no crece con la letra más allá de un tope: es de una sola palabra y debe caber en una línea.
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, minOf(density.fontScale, INTRO_NAME_MAX_FONT_SCALE)),
                ) {
                    Text(
                        text = appName,
                        style = AuthTitleStyle.copy(fontSize = 36.sp, lineHeight = 44.sp),
                        color = MovieNightCream,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.graphicsLayer {
                            val a = introAdvanceFor(progress.value).name
                            alpha = a
                            translationY = (1f - a) * 12.dp.toPx()
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MovieNightCream,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { alpha = introAdvanceFor(progress.value).tagline },
                )
            }
        }
    }
}
