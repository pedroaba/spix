package com.pedroaba.spix.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroaba.spix.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DownloadPersistenceTest {
    private class TestContext(base: Context) : ContextWrapper(base) {
        override fun getSharedPreferences(name: String, mode: Int) = baseContext.getSharedPreferences("test_download_persistence", mode)
    }
    @Test fun completedDownloadsAndLegacyLinksSurviveRepositoryRecreation() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val context = TestContext(base)
        val prefs = context.getSharedPreferences("spix", Context.MODE_PRIVATE)
        prefs.edit().putString("requests", """[{"id":"jNQXAC9IVRw","url":"https://youtu.be/jNQXAC9IVRw","at":1}]""").commit()
        try {
            val db = androidx.room.Room.inMemoryDatabaseBuilder(base, SpixDatabase::class.java).build()
            val repo = LocalSpixRepository(context, database = db)
            repo.awaitReady()
            assertEquals(DownloadStatus.Queued, repo.get("jNQXAC9IVRw")?.status)
            assertTrue(repo.playbackQueue().isEmpty())
            val video = VideoMetadata("jNQXAC9IVRw", "https://youtu.be/jNQXAC9IVRw", "Teste", "Canal", null)
            assertFalse(repo.enqueue(video))
            repo.updateDownload(video.videoId) { it.copy(status = DownloadStatus.Completed, progress = 100, localUri = "content://media/external/downloads/123", duration = "0:19") }
            val restored = LocalSpixRepository(context, database = db)
            restored.awaitReady()
            assertEquals(DownloadStatus.Completed, restored.get("jNQXAC9IVRw")?.status)
            assertEquals("0:19", restored.playbackQueue().single().duration)
            assertEquals(MediaKind.Video, com.pedroaba.spix.domain.MediaKind.Video)
            assertEquals("Teste", restored.playbackQueue().single().metadata?.title)
            restored.removeRequest(video.videoId)
            assertNull(restored.get(video.videoId))
            repo.close(); restored.close(); db.close()
        } finally { prefs.edit().clear().commit() }
    }
}
