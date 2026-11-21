package com.pedroaba.spix.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTimeTest {
    @Test fun unknownNegativeAndHourDurationsNeverRenderInvalidTimes() {
        assertEquals("0:00", playbackTime(Long.MIN_VALUE))
        assertEquals("0:00", playbackTime(0))
        assertEquals("4:24", playbackTime(264999))
        assertEquals("1:02:03", playbackTime(3723000))
    }
}
