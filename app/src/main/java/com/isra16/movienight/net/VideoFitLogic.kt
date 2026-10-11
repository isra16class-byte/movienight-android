package com.isra16.movienight.net

/*
 * Cómo se acomoda el video dentro de la pantalla completa. JVM puro, sin Android.
 * Es una preferencia solo visual y local de cada persona: no se sincroniza ni toca el reproductor compartido.
 * La web no define ningún ajuste (usa el del navegador, que muestra el video entero); "Llenar" es propio de la app.
 */
enum class VideoFit {
    /** El video entero, sin recortar (franjas negras si la proporción no coincide). Es el de siempre y el valor inicial. */
    FIT,

    /** El video cubre toda la ventana, sin deformarse, y se recorta lo que sobre. */
    FILL,
    ;

    fun toggled(): VideoFit = if (this == FIT) FILL else FIT
}

/** Texto del botón: dice lo que hará al tocarlo (de [current] al otro modo). */
fun videoFitButtonLabel(current: VideoFit): String =
    if (current == VideoFit.FIT) "Llenar la pantalla" else "Ajustar al video"
