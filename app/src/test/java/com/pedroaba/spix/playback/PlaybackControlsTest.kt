package com.pedroaba.spix.playback

import androidx.media3.common.Player
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackControlsTest {
    private fun exercise(state: Int, wantsPlay: Boolean, playing: Boolean): List<String> {
        val commands = mutableListOf<String>()
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "getPlaybackState" -> state
                "getPlayWhenReady" -> wantsPlay
                "isPlaying" -> playing
                "seekTo" -> { commands += "seek:${args!![0]}"; null }
                "play", "pause", "prepare" -> { commands += method.name; null }
                else -> error("Unexpected player call: ${method.name}")
            }
        } as Player
        togglePlayback(player)
        return commands
    }
    @Test fun replayAfterEndSeeksToZeroEvenWhenPlayWhenReadyRemainsTrue() {
        assertEquals(listOf("seek:0", "play"), exercise(Player.STATE_ENDED, true, false))
    }
    @Test fun bufferingCanBePausedAndAnIdlePlayerCanBePreparedAgain() {
        assertEquals(listOf("pause"), exercise(Player.STATE_BUFFERING, true, false))
        assertEquals(listOf("prepare", "play"), exercise(Player.STATE_IDLE, false, false))
        assertEquals(listOf("play"), exercise(Player.STATE_READY, false, false))
    }
}
