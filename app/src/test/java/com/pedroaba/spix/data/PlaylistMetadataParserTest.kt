package com.pedroaba.spix.data

import com.pedroaba.spix.domain.YouTubePlaylistLink
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class PlaylistMetadataParserTest {
    private val link = requireNotNull(YouTubePlaylistLink.parse("https://youtube.com/playlist?list=PLtest"))
    @Test fun `reads ordered unique entries and skips unavailable videos`() {
        val playlist = parsePlaylist(link, """{"title":"Minha playlist","entries":[
            {"id":"jNQXAC9IVRw","title":"Primeiro","channel":"Canal"},
            {"id":"dQw4w9WgXcQ","title":"Segundo","uploader":"Autor"},
            {"id":"jNQXAC9IVRw","title":"Repetido"},
            {"id":"abcdefghijk","title":"[Private video]"},
            {"id":"abcdefghijl","availability":"needs_auth"},null,{"id":"invalid"}
        ]}""")
        assertEquals("Minha playlist", playlist.title)
        assertEquals(listOf("jNQXAC9IVRw", "dQw4w9WgXcQ"), playlist.videos.map { it.videoId })
        assertEquals("Canal", playlist.videos[0].author)
        assertEquals("Autor", playlist.videos[1].author)
        assertEquals(4, playlist.unavailableCount)
        assertTrue(playlist.videos.all { "list=" !in it.url })
    }
    @Test fun `missing flat metadata uses safe fallback values`() {
        val playlist = parsePlaylist(link, """{"entries":[{"id":"jNQXAC9IVRw","title":null,"channel":null}]}""")
        assertEquals("Playlist do YouTube", playlist.title)
        assertEquals("Vídeo · jNQXAC9IVRw", playlist.videos.single().title)
        assertEquals("YouTube", playlist.videos.single().author)
    }
    @Test(expected = IOException::class) fun `empty playlist fails`() {
        parsePlaylist(link, """{"entries":[]}""")
    }
    @Test fun `sync accepts an empty remote playlist`() {
        assertTrue(parsePlaylist(link, """{"entries":[]}""", allowEmpty = true).videos.isEmpty())
    }
    @Test(expected = IOException::class) fun `unavailable playlist fails`() {
        parsePlaylist(link, """{"title":"Private"}""")
    }
}
