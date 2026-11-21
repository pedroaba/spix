package com.pedroaba.spix.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlaylistMembershipTest {
    @Test fun downloadedSelectionAppendsAvailableItemsInSelectionOrder() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, SpixDatabase::class.java).build()
        val repo = LocalSpixRepository(context, database = db, importLegacy = false)
        try {
            repo.awaitReady()
            val playlists = LocalPlaylistRepository(repo)
            db.catalog().insertMedia(listOf("a", "b", "c", "pending").map { id ->
                MediaRecord(id, "https://example.test/$id", 0, id, null, null,
                    if (id == "pending") null else "file:///$id", "")
            })
            playlists.save("existing", "Favoritos", listOf("a"))
            val selection = listOf("c", "a", "pending", "b", "c", "missing")
            assertEquals(2, playlists.addDownloadedSelection("existing", "", selection))
            assertEquals(listOf("a", "c", "b"), playlists.get("existing")?.videoIds)
            assertEquals(0, playlists.addDownloadedSelection("existing", "", selection))
            assertEquals(3, playlists.addDownloadedSelection(null, " Nova ", selection))
            assertEquals(2, playlists.count.first())
        } finally { repo.close(); db.close() }
    }

    @Test fun membershipIsIdempotentPreservesOrderAndSurvivesRecreation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, SpixDatabase::class.java).build()
        val repo = LocalSpixRepository(context, database = db, importLegacy = false)
        try {
            repo.awaitReady()
            val playlists = LocalPlaylistRepository(repo)
            playlists.save("one", "Favoritos", listOf("a", "b", "c"))
            playlists.save("two", "Viagem", listOf("other"))
            playlists.setMembership("one", "b", false)
            playlists.setMembership("one", "d", true)
            playlists.setMembership("one", "d", true)
            playlists.setMembership("two", "d", true)
            assertEquals(listOf("a", "c", "d"), playlists.get("one")?.videoIds)
            assertEquals(setOf("one", "two"), playlists.memberships("d").first().toSet())
            playlists.setMembership("one", "d", false)
            val recreated = LocalPlaylistRepository(repo)
            assertEquals(listOf("a", "c"), recreated.get("one")?.videoIds)
            assertEquals(listOf("other", "d"), recreated.get("two")?.videoIds)
            assertEquals(listOf("two"), recreated.memberships("d").first())
            try {
                playlists.setMembership("deleted", "d", true)
                fail("A removed playlist must not be recreated")
            } catch (_: IllegalStateException) { }
        } finally { repo.close(); db.close() }
    }
}
