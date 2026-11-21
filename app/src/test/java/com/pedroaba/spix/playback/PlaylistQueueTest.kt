package com.pedroaba.spix.playback

import com.pedroaba.spix.data.LocalPlaylist
import com.pedroaba.spix.domain.DownloadRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistQueueTest {
    private val library = listOf("a", "b", "c").map { DownloadRequest(it, "https://youtube.com/watch?v=$it", 0) }
    @Test fun queueContainsOnlyPlaylistItemsInTheirSavedOrder() {
        assertEquals(listOf("c", "a"), playlistQueue(library, LocalPlaylist("p", "Favoritas", listOf("c", "a"))).map { it.videoId })
    }
    @Test fun unavailableAndDuplicateItemsNeverExpandTheQueue() {
        assertEquals(listOf("b"), playlistQueue(library, LocalPlaylist("p", "Favoritas", listOf("missing", "b", "b"))).map { it.videoId })
        assertEquals(emptyList<DownloadRequest>(), playlistQueue(library, LocalPlaylist("p", "Vazia", listOf("missing"))))
    }
    @Test fun openingOutsideAPlaylistUsesTheLibrary() {
        assertEquals(library, playlistQueue(library, null))
    }
}
