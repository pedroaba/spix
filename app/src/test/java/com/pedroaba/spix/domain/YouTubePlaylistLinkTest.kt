package com.pedroaba.spix.domain

import org.junit.Assert.*
import org.junit.Test

class YouTubePlaylistLinkTest {
    private val id = "PLBB231211A4F62143"
    @Test fun `normalizes playlist and video links with list parameters`() {
        listOf("https://www.youtube.com/playlist?list=$id", "https://music.youtube.com/playlist?list=$id&si=share",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ&list=$id&index=5",
            "https://youtu.be/dQw4w9WgXcQ?list=$id").forEach {
            assertEquals("https://www.youtube.com/playlist?list=$id", YouTubePlaylistLink.parse(it)?.url)
        }
    }
    @Test fun `rejects unsupported hosts paths credentials and unsafe IDs`() {
        listOf("", "https://youtube.com.evil.com/playlist?list=$id", "https://user@youtube.com/playlist?list=$id",
            "https://youtube.com/channel/test?list=$id", "https://youtube.com/playlist?list=",
            "file://youtube.com/playlist?list=$id", "https://youtube.com/playlist?list=PL%26bad",
            "https://youtube.com/watch?v=dQw4w9WgXcQ", "https://youtu.be/invalid?list=$id").forEach {
            assertNull(it, YouTubePlaylistLink.parse(it))
        }
    }
}
