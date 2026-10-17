package com.isra16.movienight

import com.isra16.movienight.net.VIDEO_KEYBOARD_MAX_FRACTION
import com.isra16.movienight.net.VIDEO_KEYBOARD_MIN_HEIGHT_DP
import com.isra16.movienight.net.portraitVideoHeightDp
import com.isra16.movienight.net.RoomHeaderMode
import com.isra16.movienight.net.roomHeaderModeFor
import org.junit.Assert.assertEquals
import org.junit.Test

class PortraitVideoLogicTest {

    @Test
    fun `el tope es 50 por ciento (propio) y el piso 90 (copiado de la web)`() {
        assertEquals(0.50f, VIDEO_KEYBOARD_MAX_FRACTION, 1e-6f)
        assertEquals(90f, VIDEO_KEYBOARD_MIN_HEIGHT_DP, 1e-6f)
    }

    @Test
    fun `sin teclado es el 16 9 de siempre`() {
        assertEquals(202.5f, portraitVideoHeightDp(widthDp = 360f, availableHeightDp = 700f, keyboardOpen = false), 1e-3f)
    }

    @Test
    fun `sin teclado el alto disponible no cuenta`() {
        assertEquals(202.5f, portraitVideoHeightDp(360f, availableHeightDp = 50f, keyboardOpen = false), 1e-3f)
        assertEquals(202.5f, portraitVideoHeightDp(360f, availableHeightDp = 0f, keyboardOpen = false), 1e-3f)
    }

    @Test
    fun `con teclado se achica al 50 por ciento del alto visible`() {
        // Un teléfono de 357 dp de ancho con unos 360 dp libres sobre el teclado: el 16:9 mediría 200,8 y el tope es 180.
        assertEquals(180f, portraitVideoHeightDp(357f, availableHeightDp = 360f, keyboardOpen = true), 1e-3f)
    }

    @Test
    fun `con teclado nunca es mas grande que el 16 9`() {
        // Con mucho espacio libre el tope (450) supera al 16:9 (202,5): se queda en el 16:9, no se agranda.
        assertEquals(202.5f, portraitVideoHeightDp(360f, availableHeightDp = 900f, keyboardOpen = true), 1e-3f)
    }

    @Test
    fun `con teclado y muy poco espacio rige el piso de 90`() {
        // 50 % de 150 = 75, pero el piso gana (igual que min-height sobre max-height en CSS).
        assertEquals(90f, portraitVideoHeightDp(360f, availableHeightDp = 150f, keyboardOpen = true), 1e-3f)
    }

    @Test
    fun `con teclado en el limite exacto del tope`() {
        // 16:9 de 360 = 202,5; el tope iguala a 202,5 cuando el alto visible es 405.
        assertEquals(202.5f, portraitVideoHeightDp(360f, availableHeightDp = 405f, keyboardOpen = true), 1e-2f)
        // Un poco menos de alto visible y el tope ya manda: 50 % de 400 = 200.
        assertEquals(200f, portraitVideoHeightDp(360f, availableHeightDp = 400f, keyboardOpen = true), 1e-3f)
    }

    @Test
    fun `un alto disponible inusable deja el 16 9`() {
        assertEquals(202.5f, portraitVideoHeightDp(360f, Float.POSITIVE_INFINITY, keyboardOpen = true), 1e-3f)
        assertEquals(202.5f, portraitVideoHeightDp(360f, Float.NaN, keyboardOpen = true), 1e-3f)
        assertEquals(202.5f, portraitVideoHeightDp(360f, 0f, keyboardOpen = true), 1e-3f)
        assertEquals(202.5f, portraitVideoHeightDp(360f, -10f, keyboardOpen = true), 1e-3f)
    }

    @Test
    fun `un ancho inusable da cero`() {
        assertEquals(0f, portraitVideoHeightDp(0f, 400f, keyboardOpen = true), 1e-3f)
        assertEquals(0f, portraitVideoHeightDp(Float.POSITIVE_INFINITY, 400f, keyboardOpen = false), 1e-3f)
    }

    @Test
    fun `la cabecera es completa con el teclado cerrado y compacta con el teclado abierto`() {
        assertEquals(RoomHeaderMode.FULL, roomHeaderModeFor(0))
        assertEquals(RoomHeaderMode.COMPACT, roomHeaderModeFor(900))
    }

    @Test
    fun `limites de la cabecera compacta`() {
        // 0 o menos es teclado cerrado; con 1 px ya es teclado abierto (el limite es `<= 0`, no `< 0`).
        assertEquals(RoomHeaderMode.FULL, roomHeaderModeFor(0))
        assertEquals(RoomHeaderMode.FULL, roomHeaderModeFor(-1))
        assertEquals(RoomHeaderMode.FULL, roomHeaderModeFor(Int.MIN_VALUE))
        assertEquals(RoomHeaderMode.COMPACT, roomHeaderModeFor(1))
        assertEquals(RoomHeaderMode.COMPACT, roomHeaderModeFor(Int.MAX_VALUE))
    }
}
