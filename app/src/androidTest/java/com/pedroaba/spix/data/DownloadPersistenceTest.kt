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
            val repo = LocalSpixRepository(context)
            assertEquals(DownloadStatus.Queued, repo.requests.value.single().status)
            assertTrue(repo.tracks.value.isEmpty())
            val video = VideoMetadata("jNQXAC9IVRw", "https://youtu.be/jNQXAC9IVRw", "Teste", "Canal", null)
            assertFalse(repo.enqueue(video))
            repo.updateDownload(video.videoId) { it.copy(status = DownloadStatus.Completed, progress = 100, localUri = "content://media/external/downloads/123", duration = "0:19") }
            val restored = LocalSpixRepository(context)
            assertEquals(DownloadStatus.Completed, restored.requests.value.single().status)
            assertEquals("0:19", restored.tracks.value.single().duration)
            assertEquals(MediaKind.Video, restored.tracks.value.single().kind)
            assertEquals("Teste", restored.tracks.value.single().title)
            restored.removeRequest(video.videoId)
            assertTrue(LocalSpixRepository(context).requests.value.isEmpty())
        } finally { prefs.edit().clear().commit() }
    }
}
