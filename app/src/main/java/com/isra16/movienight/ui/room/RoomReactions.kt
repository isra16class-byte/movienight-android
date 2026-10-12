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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isra16.movienight.net.REACTION_BAR_EMOJIS
import com.isra16.movienight.net.REACTION_PANEL_COLUMNS
import com.isra16.movienight.net.REACTION_PANEL_EMOJIS
import com.isra16.movienight.net.ReactionSpec
import com.isra16.movienight.net.ReactionSurface
import com.isra16.movienight.net.ReactionTravel
import com.isra16.movienight.net.planReaction
import com.isra16.movienight.net.reactionLookFor
import com.isra16.movienight.net.reactionTravel
import kotlin.random.Random
import kotlinx.coroutines.flow.SharedFlow

private class ReactionItem(val id: Long, val spec: ReactionSpec)

/** `ease-out` de CSS (`cubic-bezier(0, 0, 0.58, 1)`), el que usa `floatUp` en la web. */
private val FloatUpEasing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/**
 * Emojis que suben y se desvanecen sobre el video, como `.float-emoji` de la web (Fase 7, partes 2B y 2D). Cada
 * `reaction` que llega EN VIVO ([live], ver `RoomViewModel.liveReactions`) nace en un punto al azar del 80 %
 * izquierdo del ancho y sube en 2,5 s desvaneciéndose. Las reglas (tope, posición, duración, tamaño y recorrido por
 * [surface]) viven en `net/ReactionLogic.kt` y tienen tests.
 *
 * - [ReactionSurface.FULLSCREEN]: 70 dp de margen y 280 dp de subida, como la web.
 * - [ReactionSurface.PORTRAIT] (caja del video en vertical) y [ReactionSurface.PIP] (ventana flotante): el recorrido
 *   se adapta al alto de la caja y la capa recorta lo que se salga.
 *
 * Solo existe mientras se compone: al salir de esa vista (o de la ventana flotante) el estado se descarta, así que
 * nada queda flotando a medias. No recibe toques y es solo una capa de dibujo: no toca `RoomPlayer` ni la
 * sincronización del video.
 */
@Composable
internal fun ReactionLayer(
    live: SharedFlow<String>,
    modifier: Modifier = Modifier,
    surface: ReactionSurface = ReactionSurface.FULLSCREEN,
) {
    val look = reactionLookFor(surface)
    val items = remember { mutableStateListOf<ReactionItem>() }
    val nextId = remember { longArrayOf(0L) }

    LaunchedEffect(live, surface) {
        live.collect { emoji ->
            val spec = planReaction(emoji, items.size, Random.nextFloat(), look.maxActive) ?: return@collect
            items.add(ReactionItem(nextId[0]++, spec))
        }
    }

    // Decorativo (como los comentarios flotantes): el lector de pantalla no los lee.
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .then(if (surface == ReactionSurface.FULLSCREEN) Modifier else Modifier.clipToBounds())
            .clearAndSetSemantics { },
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val travel = reactionTravel(surface, maxHeight.value, look.fontSp)
        for (item in items) {
            key(item.id) {
                ReactionView(
                    spec = item.spec,
                    containerWidthPx = widthPx,
                    fontSp = look.fontSp,
                    travel = travel,
                    onFinished = { items.remove(item) },
                )
            }
        }
    }
}

@Composable
private fun BoxScope.ReactionView(
    spec: ReactionSpec,
    containerWidthPx: Float,
    fontSp: Float,
    travel: ReactionTravel,
    onFinished: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(spec) {
        progress.animateTo(1f, tween(durationMillis = spec.durationMs.toInt(), easing = FloatUpEasing))
        onFinished()
    }
    val density = LocalDensity.current
    val risePx = with(density) { travel.riseDp.dp.toPx() }
    val bottomPx = with(density) { travel.bottomDp.dp.toPx() }
    Text(
        text = spec.emoji,
        fontSize = fontSp.sp,
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
 * La fila de reacciones de la vista vertical, justo encima del campo de chat (`.reactions` en la web móvil): los 14
 * emojis en una fila que se desplaza de lado. [onPick] recibe el emoji elegido; no se dibuja nada aquí, el emoji
 * aparece sobre el video cuando el server lo devuelve a la sala. Quien la usa la esconde con el teclado abierto,
 * como la web.
 */
@Composable
internal fun ReactionBar(onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (emoji in REACTION_BAR_EMOJIS) {
                Box(
                    Modifier
                        .size(width = 46.dp, height = 40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .semantics { contentDescription = "Reaccionar con $emoji" }
                        .clickable { onPick(emoji) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emoji, fontSize = 20.sp, modifier = Modifier.clearAndSetSemantics { })
                }
            }
        }
    }
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
                for (row in REACTION_PANEL_EMOJIS.chunked(REACTION_PANEL_COLUMNS)) {
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
