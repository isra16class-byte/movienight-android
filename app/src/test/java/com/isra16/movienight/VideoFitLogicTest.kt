package com.isra16.movienight

import com.isra16.movienight.net.VideoFit
import com.isra16.movienight.net.videoFitButtonLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoFitLogicTest {

    @Test
    fun `alternar va y vuelve`() {
        assertEquals(VideoFit.FILL, VideoFit.FIT.toggled())
        assertEquals(VideoFit.FIT, VideoFit.FILL.toggled())
        assertEquals(VideoFit.FIT, VideoFit.FIT.toggled().toggled())
    }

    @Test
    fun `el texto dice lo que hara el toque`() {
        assertEquals("Llenar la pantalla", videoFitButtonLabel(VideoFit.FIT))
        assertEquals("Ajustar al video", videoFitButtonLabel(VideoFit.FILL))
    }
}
