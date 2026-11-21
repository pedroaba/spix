package com.pedroaba.spix.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.pedroaba.spix.data.SpixRepository
import com.pedroaba.spix.data.VideoMetadataSource
import com.pedroaba.spix.domain.*
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpixViewModelTest {
    private val scheduled = mutableListOf<String>()
    private class RecordingScheduler(private val scheduled: MutableList<String>) : com.pedroaba.spix.download.DownloadScheduler {
        override suspend fun start(videoId: String) { scheduled += videoId }
        override suspend fun cancel(videoId: String) = Unit
    }
    private val downloads = RecordingScheduler(scheduled)
    private val a = "dQw4w9WgXcQ"
    private val b = "jNQXAC9IVRw"
    private fun video(id: String) = VideoMetadata(id, "https://www.youtube.com/watch?v=$id", "Título $id", "Canal", null)

    private class Queue : SpixRepository {
        override val requests = MutableStateFlow<List<DownloadRequest>>(emptyList())
        override val tracks = MutableStateFlow<List<Track>>(emptyList())
        var added: VideoMetadata? = null
        override suspend fun enqueue(video: VideoMetadata): Boolean { added = video; return true }
        override suspend fun updateDownload(videoId: String, transform: (DownloadRequest) -> DownloadRequest) = Unit
        override suspend fun removeRequest(videoId: String) = Unit
    }

    @Test fun `a late result from an old link never replaces the current preview`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val first = CompletableDeferred<VideoMetadata>()
            val second = CompletableDeferred<VideoMetadata>()
            val vm = SpixViewModel(Queue(), SavedStateHandle(), VideoMetadataSource { link ->
                withContext(NonCancellable) { if (link.videoId == a) first.await() else second.await() }
            }, downloads)
            store.put("vm", vm)
            vm.updateLink("https://youtu.be/$a"); advanceTimeBy(601); runCurrent()
            vm.updateLink("https://youtu.be/$b"); advanceTimeBy(601); runCurrent()
            second.complete(video(b)); runCurrent()
            first.complete(video(a)); runCurrent()
            assertEquals(b, (vm.preview.value as VideoPreviewState.Ready).video.videoId)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `editing a valid link into invalid text cancels the debounced lookup`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var calls = 0
            val vm = SpixViewModel(Queue(), SavedStateHandle(), VideoMetadataSource { calls++; video(it.videoId) }, downloads)
            store.put("vm", vm)
            vm.updateLink("https://youtu.be/$a"); advanceTimeBy(200)
            vm.updateLink("not a link"); advanceUntilIdle()
            assertEquals(0, calls)
            assertEquals(VideoPreviewState.Idle, vm.preview.value)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `failed lookup can be retried and the confirmed metadata is queued`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var calls = 0
            val queue = Queue()
            val vm = SpixViewModel(queue, SavedStateHandle(), VideoMetadataSource {
                if (calls++ == 0) throw IOException("offline") else video(it.videoId)
            }, downloads)
            store.put("vm", vm)
            vm.updateLink("https://youtu.be/$a"); advanceUntilIdle()
            assertTrue(vm.preview.value is VideoPreviewState.Failed)
            vm.enqueue(); runCurrent()
            assertNull(queue.added)
            advanceUntilIdle()
            assertTrue(vm.preview.value is VideoPreviewState.Ready)
            vm.enqueue(); advanceUntilIdle()
            assertEquals(video(a), queue.added)
            assertEquals(listOf(a), scheduled)
            assertEquals("", vm.link.value)
            assertEquals(VideoPreviewState.Idle, vm.preview.value)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
