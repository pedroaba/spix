package com.pedroaba.spix.playback

import org.junit.Assert.*
import org.junit.Test

class PictureInPicturePolicyTest {
    private val video = PlaybackInfo(connected = true, mediaId = "video", playing = true)

    @Test fun playingOrBufferingVideoCanFloat() {
        assertTrue(canEnterPictureInPicture(video))
        assertTrue(canEnterPictureInPicture(video.copy(buffering = true)))
    }

    @Test fun audioPausedStoppedDisconnectedAndFailedPlaybackDoNotFloat() {
        listOf(video.copy(video = false), video.copy(playing = false), video.copy(mediaId = null),
            video.copy(connected = false), video.copy(error = "Falha")).forEach {
            assertFalse(canEnterPictureInPicture(it))
        }
    }
}
