package com.pedroaba.spix.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoAspectTest {
    @Test fun preservesLandscapeAndPortraitVideoProportions() {
        listOf(16f / 9f, 4f / 3f, 9f / 16f).forEach {
            assertEquals(it, videoAspect(it), 0f)
        }
    }

    @Test fun invalidProportionsUseWidescreenFallback() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach {
            assertEquals(16f / 9f, videoAspect(it), 0f)
        }
    }
}
