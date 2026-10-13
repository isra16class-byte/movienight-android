package com.isra16.movienight.net

/*
 * Qué modo de color usa la app (Fase 8, paso 1). JVM puro, sin Android.
 * La persona elige claro u oscuro con un interruptor dentro de la app: la app NO sigue el ajuste de modo oscuro del
 * teléfono. La preferencia se guarda como texto (no como Boolean) para que, si algún día hubiera un tercer modo,
 * un valor viejo no se interprete mal.
 */

enum class ThemeMode(val storageValue: String) {
    LIGHT("light"),
    DARK("dark");

    val isDark: Boolean get() = this == DARK

    fun toggled(): ThemeMode = if (this == DARK) LIGHT else DARK
}

/**
 * Modo del primer arranque, y el que se usa si lo guardado no se entiende. Es una propuesta que la persona no
 * confirmó de forma explícita. Si se cambia, hay que cambiar a la par `window_background` en `res/values/colors.xml`
 * (el color de la ventana mientras la app arranca), que no puede leer este valor.
 */
val DEFAULT_THEME_MODE = ThemeMode.DARK

/** Modo a usar según lo guardado: `null` (primer arranque) o un valor que no se reconoce dan [DEFAULT_THEME_MODE]. */
fun themeModeFor(stored: String?): ThemeMode =
    ThemeMode.entries.firstOrNull { it.storageValue == stored } ?: DEFAULT_THEME_MODE

/** Modo que corresponde al interruptor de la pantalla principal (`true` = oscuro). */
fun themeModeFromSwitch(darkChecked: Boolean): ThemeMode = if (darkChecked) ThemeMode.DARK else ThemeMode.LIGHT
