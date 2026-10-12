package com.isra16.movienight.ui.room

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.isra16.movienight.net.ChatMessage
import com.isra16.movienight.net.DanmakuProfile
import com.isra16.movienight.net.DanmakuSpec
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharedFlow

private class DanmakuItem(val id: Long, val spec: DanmakuSpec)

/**
 * Comentarios flotantes del chat sobre el video, como el "danmaku" de la web (Fase 7, parte 2A). Cada mensaje que
 * llega EN VIVO ([live], ver `RoomViewModel.liveChat`) cruza la pantalla de derecha a izquierda en un carril; las
 * reglas (carriles, tope, duración, color) viven en `net/DanmakuLogic.kt` y tienen tests.
 *
 * Solo se compone dentro de la pantalla completa y de la ventana flotante (PiP, parte 2D: con [profile] =
 * `DanmakuProfile.PIP`, letra de 11 sp, 3 carriles y 4 a la vez): al salir de ahí este estado se descarta (comentarios
 * y carriles), como `clearDanmaku()` en la web, así que nada queda "congelado" para la próxima vez. Va al final del `Box` de la
 * pantalla completa para dibujarse por encima de los controles, y no recibe toques. Es solo una capa de dibujo: no
 * toca `RoomPlayer` ni la sincronización del video.
 */
@Composable
internal fun DanmakuLayer(
    live: SharedFlow<ChatMessage>,
    modifier: Modifier = Modifier,
    profile: DanmakuProfile = DanmakuProfile.FULLSCREEN,
) {
    val lanes = remember(profile) { profile.newLanes() }
    val items = remember { mutableStateListOf<DanmakuItem>() }
    val nextId = remember { longArrayOf(0L) }

    LaunchedEffect(live, lanes) {
        live.collect { message ->
            val spec = lanes.plan(message, activeCount = items.size, now = SystemClock.elapsedRealtime())
                ?: return@collect
            items.add(DanmakuItem(nextId[0]++, spec))
        }
    }

    // Como `aria-hidden="true"` en la web: son texto decorativo, que el lector de pantalla no los lea.
    BoxWithConstraints(modifier.fillMaxSize().clearAndSetSemantics { }) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight
        for (item in items) {
            key(item.id) {
                DanmakuView(
                    spec = item.spec,
                    fontSp = profile.fontSp,
                    containerWidthPx = widthPx,
                    containerHeightPx = heightPx,
                    onFinished = { items.remove(item) },
                )
            }
        }
    }
}

/**
 * Un comentario. Empieza fuera de la pantalla por la derecha y avanza a velocidad constante hasta salir por la
 * izquierda: recorre el ancho de la pantalla más el ancho propio (en la web, `translateX(-100vw - 100%)`), así que
 * sale entero sin importar cuán largo sea.
 */
@Composable
private fun DanmakuView(
    spec: DanmakuSpec,
    fontSp: Float?,
    containerWidthPx: Float,
    containerHeightPx: Int,
    onFinished: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    var selfWidthPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(spec) {
        progress.animateTo(1f, tween(durationMillis = spec.durationMs.toInt(), easing = LinearEasing))
        onFinished()
    }

    // La web baja la letra de 22 a 16 px en pantallas de hasta 820 px de ancho. Un perfil con letra propia (PiP) la fija.
    val compact = LocalConfiguration.current.screenWidthDp <= 820
    val label = remember(spec) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = Color(spec.nameColor), fontWeight = FontWeight.Bold)) { append("${spec.user}:") }
            append(" ${spec.text}")
        }
    }
    Text(
        text = label,
        color = Color.White,
        fontSize = (fontSp ?: if (compact) 16f else 22f).sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
        style = TextStyle(shadow = Shadow(color = Color.Black.copy(alpha = 0.9f), offset = Offset(0f, 2f), blurRadius = 6f)),
        modifier = Modifier
            .offset { IntOffset(0, (spec.topFraction * containerHeightPx).roundToInt()) }
            .graphicsLayer { translationX = containerWidthPx - (containerWidthPx + selfWidthPx) * progress.value }
            // Sin tope de ancho: un mensaje largo mide lo que mide en vez de recortarse al ancho de la pantalla.
            .wrapContentWidth(align = Alignment.Start, unbounded = true)
            .onSizeChanged { selfWidthPx = it.width },
    )
}
