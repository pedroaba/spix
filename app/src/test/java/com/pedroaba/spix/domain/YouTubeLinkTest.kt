package com.pedroaba.spix.domain

import org.junit.Assert.*
import org.junit.Test

class YouTubeLinkTest {
    private val id = "dQw4w9WgXcQ"

    @Test fun `accepts supported video URLs and normalizes them`() {
        listOf("https://www.youtube.com/watch?v=$id&t=10", "https://youtu.be/$id?si=abc",
            "https://m.youtube.com/watch?v=$id", "https://music.youtube.com/watch?v=$id",
            "https://youtube.com/shorts/$id", "https://youtube.com/live/$id", "https://youtube.com/embed/$id")
            .forEach { assertEquals(it, "https://www.youtube.com/watch?v=$id", YouTubeLink.parse(it)?.url) }
    }

    @Test fun `rejects lookalike domains credentials playlists and malformed IDs`() {
        listOf("", "not a link", "https://youtube.com.evil.com/watch?v=$id", "https://evil.com/$id",
            "https://youtube.com@evil.com/watch?v=$id", "https://person@youtube.com/watch?v=$id",
            "javascript:alert(1)", "https://youtube.com/playlist?list=123", "https://youtube.com/watch?v=short",
            "https://youtu.be/${id}extra", "https://youtu.be/$id/extra").forEach { assertNull(it, YouTubeLink.parse(it)) }
    }
}
