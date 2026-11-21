package com.pedroaba.spix.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalPlaylistRepositoryTest {
    @Test fun savedPlaylistsRestoreNamesOrderAndMembership() {
        val items = listOf(LocalPlaylist("1", "Músicas & vídeos 🎵", listOf("c", "a")), LocalPlaylist("2", "Vazia", emptyList()))
        assertEquals(items, decodePlaylists(encodePlaylists(items)))
    }
    @Test fun corruptStorageIsHandledWithoutCrashing() {
        assertEquals(emptyList<LocalPlaylist>(), decodePlaylists("invalid"))
    }
}
