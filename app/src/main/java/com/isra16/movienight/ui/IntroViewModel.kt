package com.isra16.movienight.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isra16.movienight.net.INTRO_DURATION_MS
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Dice si la intro ya terminó (Fase 8, paso 2). El reloj arranca al crearse y dura [INTRO_DURATION_MS], pase lo que
 * pase con la sesión. Vive en un ViewModel (no en un `remember`) para que no se repita si la actividad se recrea
 * (tema, idioma o tamaño de letra cambian y la recrean; girar no, ver `configChanges` en el manifiesto) y para que
 * ir a segundo plano y volver no la reinicie. Una actividad nueva (salir con Atrás y abrir de nuevo) crea otro
 * ViewModel, igual que SessionViewModel: cuenta como abrir la app otra vez.
 */
class IntroViewModel : ViewModel() {

    var finished by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            delay(INTRO_DURATION_MS)
            finished = true
        }
    }
}
