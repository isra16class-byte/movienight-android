package com.isra16.movienight.ui.room

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isra16.movienight.net.REACTION_BOTTOM_DP
import com.isra16.movienight.net.REACTION_EMOJIS
import com.isra16.movienight.net.REACTION_PANEL_COLUMNS
import com.isra16.movienight.net.REACTION_RISE_DP
import com.isra16.movienight.net.ReactionSpec
import com.isra16.movienight.net.planReaction
import kotlin.random.Random
import kotlinx.coroutines.flow.SharedFlow

private class ReactionItem(val id: Long, val spec: ReactionSpec)

/** `ease-out` de CSS (`cubic-bezier(0, 0, 0.58, 1)`), el que usa `floatUp` en la web. */
private val FloatUpEasing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/**
 * Emojis que suben y se desvanecen sobre el video, como `.float-emoji` de la web (Fase 7, parte 2B). Cada `reaction`
 * que llega EN VIVO ([live], ver `RoomViewModel.liveReactions`) nace en un punto al azar del 80 % izquierdo del
 * ancho, a 70 dp del borde de abajo, y sube 280 dp en 2,5 s. Las reglas (tope, posición, duración) viven en
 * `net/ReactionLogic.kt` y tienen tests.
 *
 * Solo se compone dentro de la pantalla completa: al salir de ella el estado se descarta, así que nada queda
 * flotando a medias para la próxima vez. No recibe toques y es solo una capa de dibujo: no toca `RoomPlayer` ni
 * la sincronización del video.
 */
@Composable
internal fun ReactionLayer(live: SharedFlow<String>, modifier: Modifier = Modifier) {
    val items = remember { mutableStateListOf<ReactionItem>() }
    val nextId = remember { longArrayOf(0L) }

    LaunchedEffect(live) {
        live.collect { emoji ->
            val spec = planReaction(emoji, activeCount = items.size, random = Random.nextFloat()) ?: return@collect
            items.add(ReactionItem(nextId[0]++, spec))
        }
    }

    // Decorativo (como los comentarios flotantes): el lector de pantalla no los lee.
    BoxWithConstraints(modifier.fillMaxSize().clearAndSetSemantics { }) {
        val widthPx = constraints.maxWidth.toFloat()
        for (item in items) {
            key(item.id) {
                ReactionView(spec = item.spec, containerWidthPx = widthPx, onFinished = { items.remove(item) })
            }
        }
    }
}

@Composable
private fun BoxScope.ReactionView(spec: ReactionSpec, containerWidthPx: Float, onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(spec) {
        progress.animateTo(1f, tween(durationMillis = spec.durationMs.toInt(), easing = FloatUpEasing))
        onFinished()
    }
    val density = LocalDensity.current
    val risePx = with(density) { REACTION_RISE_DP.dp.toPx() }
    val bottomPx = with(density) { REACTION_BOTTOM_DP.dp.toPx() }
    Text(
        text = spec.emoji,
        fontSize = 30.sp,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .graphicsLayer {
                translationX = spec.leftFraction * containerWidthPx
                translationY = -(bottomPx + risePx * progress.value)
                alpha = 1f - progress.value
            },
    )
}

/**
 * El botón 😊 y, debajo, el panel con los emojis (`.fs-emoji` en la web): 4 columnas, y no se cierra al elegir
 * uno. [open] y [onToggle] los maneja quien lo usa (`RoomFullscreen`, que además fija el grupo lateral mientras
 * está abierto). [onPick] recibe el emoji elegido; no se dibuja nada aquí, el emoji aparece cuando el server lo
 * devuelve a la sala.
 */
@Composable
internal fun ReactionPicker(open: Boolean, onToggle: () -> Unit, onPick: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Scrim)
                .semantics { contentDescription = if (open) "Cerrar reacciones" else "Reacciones" }
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Text("😊", fontSize = 22.sp, modifier = Modifier.clearAndSetSemantics { })
        }
        if (open) {
            Column(
                Modifier.clip(RoundedCornerShape(12.dp)).background(Scrim).padding(8.dp),
            ) {
                for (row in REACTION_EMOJIS.chunked(REACTION_PANEL_COLUMNS)) {
                    Row {
                        for (emoji in row) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .semantics { contentDescription = "Reaccionar con $emoji" }
                                    .clickable { onPick(emoji) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(emoji, fontSize = 24.sp, modifier = Modifier.clearAndSetSemantics { })
                            }
                        }
                    }
                }
            }
        }
    }
}
