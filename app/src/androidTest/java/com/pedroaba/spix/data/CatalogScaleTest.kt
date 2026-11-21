package com.pedroaba.spix.data

import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import androidx.paging.testing.asSnapshot
import androidx.paging.PagingSource
import com.pedroaba.spix.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class CatalogScaleTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun video(id: Int) = VideoMetadata("fixture$id", "https://youtube.com/watch?v=fixture$id", "Vídeo %04d".format(id), "Canal", "https://example.com/$id.jpg")
    @Test fun importThousandRowsIsDeduplicatedPagedAndProgressDoesNotInvalidateLibrary() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, SpixDatabase::class.java).build()
        val repo = LocalSpixRepository(context, database = db, importLegacy = false)
        try {
            repo.awaitReady()
            val videos = (0 until 1000).map(::video)
            assertEquals(1000, repo.enqueueBatch(videos + videos).size)
            assertTrue(repo.enqueueBatch(videos).isEmpty())
            for (v in videos) repo.updateDownload(v.videoId) { it.copy(status = DownloadStatus.Completed, localUri = "content://fixture/${v.videoId}", duration = "0:02") }
            assertEquals(1000, repo.libraryCount(LibraryQuery()).first())
            val firstPage = repo.library(LibraryQuery()).asSnapshot { }
            assertTrue(firstPage.size in 1..160)
            val distant = db.catalog().libraryPages(librarySql(LibraryQuery())).load(PagingSource.LoadParams.Refresh(900, 40, false)) as PagingSource.LoadResult.Page
            assertEquals(40, distant.data.size)
            assertEquals("fixture99", distant.data.first().videoId)
            assertEquals(1000, repo.playbackQueue().size)
            val extra = video(1001)
            repo.enqueueBatch(listOf(extra))
            repo.updateDownload(extra.videoId) { it.copy(status = DownloadStatus.Downloading) }
            delay(200) // Drain the preceding catalog/status invalidations before registering the sources.
            val sourceAfterInsert = db.catalog().libraryPages(librarySql(LibraryQuery()))
            val downloads = db.catalog().downloadPages(downloadsSql(false))
            val refresh = PagingSource.LoadParams.Refresh<Int>(null, 80, false)
            assertEquals(80, (sourceAfterInsert.load(refresh) as PagingSource.LoadResult.Page).data.size)
            downloads.load(refresh) // Registers Room's observer; an unloaded source would make this assertion vacuous.
            repeat(20) { tick -> repo.updateDownload(extra.videoId) { it.copy(progress = tick, transfer = DownloadTransfer(tick.toLong(), 100)) } }
            delay(100)
            assertFalse("Transfer progress must not invalidate library pages", sourceAfterInsert.invalid)
            assertFalse("Transfer progress must not invalidate download pages", downloads.invalid)
            assertEquals(19, repo.get(extra.videoId)?.progress)
            assertNotNull(db.catalog().checkpoint(extra.videoId))
        } finally { repo.close(); db.close() }
    }

    @Test fun filtersPlaylistOrderAndMembershipSurviveRecreation() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, SpixDatabase::class.java).build()
        val repo = LocalSpixRepository(context, database = db, importLegacy = false)
        try {
            repo.awaitReady()
            assertEquals(CatalogCounts(0, 0, 0, 0), repo.counts.first())
            repo.enqueueBatch(listOf(video(1), video(2), video(3)))
            for (id in 1..3) repo.updateDownload("fixture$id") { it.copy(status = DownloadStatus.Completed, localUri = "content://fixture/$id") }
            val playlists = LocalPlaylistRepository(repo)
            playlists.save("ordered", "Minha playlist", listOf("fixture3", "missing", "fixture1", "fixture3"))
            val recreated = LocalPlaylistRepository(repo)
            assertEquals(listOf("fixture3", "missing", "fixture1"), recreated.get("ordered")?.videoIds)
            val query = LibraryQuery(playlistId = "ordered")
            val page = db.catalog().libraryPages(librarySql(query)).load(PagingSource.LoadParams.Refresh(null, 80, false)) as PagingSource.LoadResult.Page
            assertEquals(listOf("fixture3", "fixture1"), page.data.map { it.videoId })
            assertEquals(2, repo.libraryCount(query).first())
            assertEquals(1, repo.libraryCount(LibraryQuery(search = "0002")).first())
            assertEquals(0, repo.libraryCount(LibraryQuery(search = "%")).first())
            repo.updateDownload("fixture2") { it.copy(metadata = video(2).copy(title = "CANÇÃO DO VERÃO")) }
            assertEquals(1, repo.libraryCount(LibraryQuery(search = "Canção do Verão")).first())
            assertEquals("content://fixture/3", recreated.firstAvailableUri("ordered"))
            playlists.delete("ordered")
            assertNull(playlists.get("ordered"))
        } finally { repo.close(); db.close() }
    }

    @Test fun malformedLegacyImportIsTransactionalAndRetryable() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, SpixDatabase::class.java).build()
        val prefs = context.getSharedPreferences("catalog_migration_test", 0)
        prefs.edit().putString("requests", """[{"id":"a","url":"url","at":1},{"at":2}]""").commit()
        val repo = LocalSpixRepository(context, database = db, legacyPreferences = prefs)
        try {
            assertTrue(runCatching { repo.awaitReady() }.isFailure)
            assertEquals(0, db.catalog().mediaCount())
            assertTrue(prefs.contains("requests"))
            prefs.edit().putString("requests", """[{"id":"a","url":"url","at":1,"status":"Completed","localUri":"content://old/1"}]""").commit()
            repo.retryInitialization()
            assertEquals("content://old/1", repo.get("a")?.localUri)
            assertEquals(1, db.catalog().mediaCount())
        } finally { repo.close(); db.close(); prefs.edit().clear().commit() }
    }
}
