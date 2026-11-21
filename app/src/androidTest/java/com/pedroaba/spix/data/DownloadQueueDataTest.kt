package com.pedroaba.spix.data

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroaba.spix.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DownloadQueueDataTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun request(index: Int, status: DownloadStatus) = DownloadRequest("queue$index", "https://example.com/$index", index.toLong(),
        VideoMetadata("queue$index", "url", "Vídeo $index", "Canal", null), status,
        localUri = if (status == DownloadStatus.Completed) "content://completed/$index" else null)

    @Test fun cancelAllIsDurablePreservesCompletedFilesAndClearsLiveProgress() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, SpixDatabase::class.java).build()
        val repo = LocalSpixRepository(context, db, importLegacy = false)
        try {
            repo.awaitReady()
            val rows = (0 until 350).map { index -> request(index, when (index % 4) {
                0 -> DownloadStatus.Queued; 1 -> DownloadStatus.Failed; 2 -> DownloadStatus.Completed; else -> DownloadStatus.Cancelled
            }) }
            db.catalog().insertMedia(rows.map { it.mediaRecord() })
            db.catalog().jobs(rows.map { it.jobRecord() })
            repo.updateDownload("queue0") { it.copy(status = DownloadStatus.Downloading, progress = 42, transfer = DownloadTransfer(42, 100)) }
            val completed = repo.playbackQueue()
            repo.cancelActiveDownloads()
            assertNull(repo.nextQueued())
            assertEquals(0, repo.counts.first().active)
            assertNull(repo.liveProgress.value)
            assertNull(db.catalog().checkpoint("queue0"))
            repo.recoverRunning()
            val reopened = LocalSpixRepository(context, db, importLegacy = false)
            reopened.awaitReady()
            assertEquals(DownloadStatus.Cancelled, reopened.get("queue0")?.status)
            assertFalse(reopened.hasQueued())
            assertEquals(completed, reopened.playbackQueue())
            assertEquals(DownloadStatus.Failed, reopened.get("queue1")?.status)
            reopened.close()
        } finally { repo.close(); db.close() }
    }

    @Test fun failedFilterPagesThrough350UniqueVideosAndReachesTheEnd() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, SpixDatabase::class.java).build()
        try {
            val rows = (0 until 350).map { request(it, DownloadStatus.Failed) } + request(999, DownloadStatus.Completed) + request(998, DownloadStatus.Cancelled)
            db.catalog().insertMedia(rows.map { it.mediaRecord() }); db.catalog().jobs(rows.map { it.jobRecord() })
            val source = db.catalog().downloadPages(downloadsSql(DownloadFilter.Failed))
            val ids = mutableListOf<String>()
            var key: Int? = null
            var pages = 0
            do {
                val params = if (key == null) PagingSource.LoadParams.Refresh<Int>(null, 40, false) else PagingSource.LoadParams.Append(key, 40, false)
                val page = source.load(params) as PagingSource.LoadResult.Page
                ids += page.data.map { it.media.videoId }
                key = page.nextKey
                assertTrue("Paging must reach the end", ++pages < 12)
            } while (key != null)
            assertEquals(350, ids.size)
            assertEquals(350, ids.distinct().size)
            assertEquals("queue349", ids.first()); assertEquals("queue0", ids.last())
            val active = db.catalog().downloadPages(downloadsSql(DownloadFilter.Active)).load(PagingSource.LoadParams.Refresh(null, 40, false)) as PagingSource.LoadResult.Page
            assertTrue(active.data.isEmpty())
        } finally { db.close() }
    }

    @Test fun playlistNotificationOrderPersistsAcrossRestartAndSingleRetryClearsGrouping() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, SpixDatabase::class.java).build()
        val repo = LocalSpixRepository(context, db, importLegacy = false)
        try {
            repo.awaitReady()
            val rows = (0 until 3).map { request(it, DownloadStatus.Queued) }
            db.catalog().insertMedia(rows.map { it.mediaRecord() }); db.catalog().jobs(rows.map { it.jobRecord() })
            repo.recordBatch(listOf("queue2", "queue0", "queue1"), "Playlist")
            assertEquals("queue2", repo.nextQueued())
            repo.updateDownload("queue2") { it.copy(status = DownloadStatus.Failed) }
            assertEquals("queue0", repo.nextQueued())
            val reopened = LocalSpixRepository(context, db, importLegacy = false)
            reopened.awaitReady()
            val batch = requireNotNull(reopened.batch("queue0"))
            assertEquals(2, batch.itemIndex); assertEquals(3, batch.total); assertEquals("Playlist", batch.title)
            reopened.recordBatch(listOf("queue0"), null)
            assertNull(reopened.batch("queue0"))
            reopened.removeRequest("queue1")
            assertNull(reopened.batch("queue1"))
            reopened.close()
        } finally { repo.close(); db.close() }
    }
    @Test fun retryAllIncludes350FailuresBeyondTheLoadedPageAndPreservesOtherStates() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, SpixDatabase::class.java).build()
        val repo = LocalSpixRepository(context, db, importLegacy = false)
        try {
            repo.awaitReady()
            val rows = (0 until 350).map { request(it, DownloadStatus.Failed).copy(progress = 80, error = "Falha antiga") } +
                request(990, DownloadStatus.Completed) + request(991, DownloadStatus.Downloading) + request(992, DownloadStatus.Cancelled)
            db.catalog().insertMedia(rows.map { it.mediaRecord() }); db.catalog().jobs(rows.map { it.jobRecord() })
            db.catalog().checkpoint(ProgressRecord("queue0", 80, null))
            val loaded = db.catalog().downloadPages(downloadsSql(DownloadFilter.Failed)).load(PagingSource.LoadParams.Refresh(null, 80, false)) as PagingSource.LoadResult.Page
            assertEquals(80, loaded.data.size)
            val retried = repo.retryDownloads(DownloadFilter.Failed, null)
            assertEquals(350, retried.size); assertEquals(350, retried.distinct().size)
            assertEquals(DownloadStatus.Queued, repo.get("queue0")?.status)
            assertEquals(0, repo.get("queue0")?.progress); assertNull(repo.get("queue0")?.error)
            assertNull(db.catalog().checkpoint("queue0"))
            assertEquals(DownloadStatus.Completed, repo.get("queue990")?.status)
            assertEquals("content://completed/990", repo.get("queue990")?.localUri)
            assertEquals(DownloadStatus.Downloading, repo.get("queue991")?.status)
            assertEquals(DownloadStatus.Cancelled, repo.get("queue992")?.status)
            assertTrue(repo.retryDownloads(DownloadFilter.Failed, null).isEmpty())
        } finally { repo.close(); db.close() }
    }

    @Test fun retrySelectionDeduplicatesAndIgnoresMissingActiveAndCompletedVideos() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, SpixDatabase::class.java).build()
        val repo = LocalSpixRepository(context, db, importLegacy = false)
        try {
            repo.awaitReady()
            val rows = listOf(request(0, DownloadStatus.Failed), request(1, DownloadStatus.Failed), request(2, DownloadStatus.Completed),
                request(3, DownloadStatus.Downloading), request(4, DownloadStatus.Cancelled))
            db.catalog().insertMedia(rows.map { it.mediaRecord() }); db.catalog().jobs(rows.map { it.jobRecord() })
            assertEquals(listOf("queue0"), repo.retryDownloads(DownloadFilter.Failed, listOf("queue0", "queue0", "queue2", "queue3", "queue4", "missing")))
            assertEquals(DownloadStatus.Failed, repo.get("queue1")?.status)
            assertEquals(listOf("queue4"), repo.retryDownloads(DownloadFilter.Cancelled, listOf("queue1", "queue4")))
            assertEquals(DownloadStatus.Failed, repo.get("queue1")?.status)
            assertTrue(repo.retryDownloads(DownloadFilter.Completed, null).isEmpty())
            assertEquals(DownloadStatus.Completed, repo.get("queue2")?.status)
            assertEquals(DownloadStatus.Downloading, repo.get("queue3")?.status)
        } finally { repo.close(); db.close() }
    }

    @Test fun retrySelectionHandlesMoreThanOneSqlParameterChunk() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, SpixDatabase::class.java).build()
        val repo = LocalSpixRepository(context, db, importLegacy = false)
        try {
            repo.awaitReady()
            val rows = (0 until 750).map { request(it, DownloadStatus.Failed) }
            db.catalog().insertMedia(rows.map { it.mediaRecord() }); db.catalog().jobs(rows.map { it.jobRecord() })
            val ids = rows.map { it.videoId }
            assertEquals(ids.toSet(), repo.retryDownloads(DownloadFilter.Failed, ids + ids.take(50)).toSet())
            assertTrue(repo.retryDownloads(DownloadFilter.Failed, ids).isEmpty())
        } finally { repo.close(); db.close() }
    }

}
