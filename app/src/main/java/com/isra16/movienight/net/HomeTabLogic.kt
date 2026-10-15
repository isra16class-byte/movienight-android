package com.isra16.movienight.net

/*
 * Pestañas de la barra inferior de Home (Fase 8, paso 4). JVM puro, sin Android.
 * El orden del enum es el orden de la barra; la pestaña elegida se guarda como número (`ordinal`) para sobrevivir a un giro.
 */

enum class HomeTab(val label: String) {
    ROOMS("Salas"),
    LIBRARY("Biblioteca"),
    UPLOADS("Subidas"),
    PROFILE("Perfil"),
}

/** Pestaña con la que se abre Home. Propuesta nuestra (es la primera de la barra); no la confirmó la persona. */
val DEFAULT_HOME_TAB = HomeTab.ROOMS

/** Pestaña que corresponde a un número guardado: fuera de rango (por ejemplo, si algún día cambia la lista) da [DEFAULT_HOME_TAB]. */
fun homeTabAt(index: Int): HomeTab = HomeTab.entries.getOrNull(index) ?: DEFAULT_HOME_TAB

/** La barra se esconde mientras el teclado está abierto (`imeBottomPx` = alto del teclado en píxeles): si no, quedaría encima de él. */
fun showHomeBottomBar(imeBottomPx: Int): Boolean = imeBottomPx <= 0
