package com.isra16.movienight

import com.isra16.movienight.net.chatNameColorIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatNameColorLogicTest {

    @Test
    fun `el mismo nombre da siempre el mismo indice`() {
        assertEquals(chatNameColorIndex("Isra", 8), chatNameColorIndex("Isra", 8))
    }

    @Test
    fun `valores fijos de FNV-1a (estable entre sesiones y dispositivos)`() {
        // FNV-1a 32 bits de "a" = 0xE40C292C; de "isra" calculado aparte con la definicion publicada.
        assertEquals((0xE40C292CL % 8).toInt(), chatNameColorIndex("a", 8))
        assertEquals((0xE40C292CL % 5).toInt(), chatNameColorIndex("A", 5))
    }

    @Test
    fun `trim y mayusculas no cambian el indice`() {
        // Tambien con tamanos que no son potencia de 2: con 8 el modulo solo mira los 3 bits bajos de cada byte y la diferencia entre
        // mayuscula y minuscula (bit 5) nunca se notaria.
        for (size in listOf(5, 7, 8)) {
            for (n in listOf("maria", "Luis", "ANA", "Pedro Pablo", "Lucía", "x")) {
                val base = chatNameColorIndex(n.lowercase(), size)
                assertEquals("$n/$size", base, chatNameColorIndex("  $n  ", size))
                assertEquals("$n/$size", base, chatNameColorIndex(n.uppercase(), size))
                assertEquals("$n/$size", base, chatNameColorIndex(" ${n.lowercase()}\t", size))
            }
        }
    }

    @Test
    fun `el indice siempre cae en rango`() {
        val nombres = listOf("", " ", "a", "Zoë", "😀", "x".repeat(500), "Ana María", "日本語")
        for (size in listOf(1, 2, 8, 16, 97)) {
            for (n in nombres) {
                val i = chatNameColorIndex(n, size)
                assertTrue("$n/$size -> $i", i in 0 until size)
            }
        }
    }

    @Test
    fun `con size 1 siempre es 0`() {
        assertEquals(0, chatNameColorIndex("cualquiera", 1))
    }

    @Test
    fun `size cero o negativo devuelve 0`() {
        assertEquals(0, chatNameColorIndex("ana", 0))
        assertEquals(0, chatNameColorIndex("ana", -3))
    }

    @Test
    fun `un nombre vacio no falla y es estable`() {
        assertEquals(chatNameColorIndex("", 8), chatNameColorIndex("   ", 8))
        assertTrue(chatNameColorIndex("", 8) in 0 until 8)
    }

    @Test
    fun `veinte nombres distintos usan varios indices`() {
        val nombres = listOf(
            "Ana", "Luis", "Marta", "Pedro", "Lucia", "Jorge", "Sofia", "Diego", "Carla", "Mateo",
            "Valeria", "Andres", "Camila", "Nicolas", "Paula", "Sergio", "Elena", "Pablo", "Irene", "Hugo",
        )
        val usados = nombres.map { chatNameColorIndex(it, 8) }.toSet()
        assertTrue("indices usados: $usados", usados.size >= 5)
    }
}
