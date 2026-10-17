package com.isra16.movienight.net

private const val FNV_OFFSET_BASIS_32 = 0x811C9DC5L
private const val FNV_PRIME_32 = 0x01000193L
private const val MASK_32 = 0xFFFFFFFFL

/**
 * Cuál de los [size] colores de nombre le toca a [name] en el chat (Fase 8, paso 5n). FNV-1a de 32 bits sobre los bytes UTF-8 del nombre
 * con `trim` y en minúsculas: no depende de la sesión, del teléfono ni del orden de llegada, así que una persona conserva su color y
 * en todos los teléfonos se ve igual. La clave es el NOMBRE y no el `userId` porque la cita de una respuesta solo trae el nombre: así el
 * encabezado y la cita coinciden. Dos personas con el mismo nombre comparten color (limitación aceptada).
 *
 * Siempre devuelve un valor en `0 until size`; con `size <= 0` devuelve 0.
 */
fun chatNameColorIndex(name: String, size: Int): Int {
    if (size <= 0) return 0
    var hash = FNV_OFFSET_BASIS_32
    for (byte in name.trim().lowercase().toByteArray(Charsets.UTF_8)) {
        hash = hash xor (byte.toLong() and 0xFFL)
        hash = (hash * FNV_PRIME_32) and MASK_32
    }
    return (hash % size).toInt()
}
