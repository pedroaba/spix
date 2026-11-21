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
    private val playlists = com.pedroaba.spix.data.PlaylistMetadataSource { error("Unexpected playlist lookup") }
    private val downloads = RecordingScheduler(scheduled)
    private val a = "dQw4w9WgXcQ"
    private val b = "jNQXAC9IVRw"
    private fun video(id: String) = VideoMetadata(id, "https://www.youtube.com/watch?v=$id", "Título $id", "Canal", null)

    private class Queue : SpixRepository {
        val requests = MutableStateFlow<List<DownloadRequest>>(emptyList())
        val tracks = MutableStateFlow<List<Track>>(emptyList())
        override suspend fun get(videoId: String) = requests.value.firstOrNull { it.videoId == videoId }
        var added: VideoMetadata? = null
        override suspend fun enqueue(video: VideoMetadata): Boolean {
            added = video
            if (requests.value.any { it.videoId == video.videoId }) return false
            requests.value += DownloadRequest(video.videoId, video.url, 0, video)
            return true
        }
        override suspend fun updateDownload(videoId: String, transform: (DownloadRequest) -> DownloadRequest) {
            requests.value = requests.value.map { if (it.videoId == videoId) transform(it) else it }
        }
        var retryCalls = 0
        override suspend fun retryDownloads(filter: com.pedroaba.spix.data.DownloadFilter, ids: List<String>?): List<String> {
            retryCalls++
            val status = when (filter) {
                com.pedroaba.spix.data.DownloadFilter.Failed -> DownloadStatus.Failed
                com.pedroaba.spix.data.DownloadFilter.Cancelled -> DownloadStatus.Cancelled
                else -> return emptyList()
            }
            val eligible = requests.value.filter { it.status == status && it.localUri == null && (ids == null || it.videoId in ids) }.map { it.videoId }.distinct()
            requests.value = requests.value.map { if (it.videoId in eligible) it.copy(status = DownloadStatus.Queued, progress = 0, error = null, transfer = null) else it }
            return eligible
        }
        val removed = mutableListOf<String>()
        override suspend fun removeRequest(videoId: String) {
            removed += videoId
            requests.value = requests.value.filterNot { it.videoId == videoId }
        }
    }

    @Test fun `a late result from an old link never replaces the current preview`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val first = CompletableDeferred<VideoMetadata>()
            val second = CompletableDeferred<VideoMetadata>()
            val vm = SpixViewModel(Queue(), SavedStateHandle(), VideoMetadataSource { link ->
                withContext(NonCancellable) { if (link.videoId == a) first.await() else second.await() }
            }, downloads, playlists)
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
            val vm = SpixViewModel(Queue(), SavedStateHandle(), VideoMetadataSource { calls++; video(it.videoId) }, downloads, playlists)
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
            }, downloads, playlists)
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
    @Test fun `playlist watch link queues all videos and ignores repeated submissions`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val queue = Queue()
            var playlistCalls = 0
            val source = com.pedroaba.spix.data.PlaylistMetadataSource {
                playlistCalls++
                PlaylistMetadata(it.playlistId, "Playlist", listOf(video(a), video(b)))
            }
            val vm = SpixViewModel(queue, SavedStateHandle(), VideoMetadataSource { error("Must resolve playlist") }, downloads, source)
            store.put("vm", vm)
            val url = "https://youtube.com/watch?v=$a&list=PLtest"
            vm.updateLink(url); advanceUntilIdle()
            assertTrue(vm.preview.value is VideoPreviewState.PlaylistReady)
            vm.enqueue(); vm.enqueue(); advanceUntilIdle()
            assertEquals(listOf(a, b), scheduled)
            assertEquals(2, queue.requests.value.size)
            vm.updateLink(url); advanceUntilIdle()
            vm.enqueue(); advanceUntilIdle()
            assertEquals(listOf(a, b), scheduled)
            assertEquals(2, playlistCalls)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `a scheduling failure does not stop the remaining playlist entries and can be retried`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val queue = Queue()
            var fail = true
            val scheduler = object : com.pedroaba.spix.download.DownloadScheduler {
                override suspend fun start(videoId: String) {
                    if (videoId == a && fail) throw IOException("scheduler unavailable")
                    scheduled += videoId
                }
                override suspend fun cancel(videoId: String) = Unit
            }
            val source = com.pedroaba.spix.data.PlaylistMetadataSource { PlaylistMetadata(it.playlistId, "Playlist", listOf(video(a), video(b))) }
            val vm = SpixViewModel(queue, SavedStateHandle(), VideoMetadataSource { error("Unexpected video") }, scheduler, source)
            store.put("vm", vm)
            vm.updateLink("https://youtube.com/playlist?list=PLtest"); advanceUntilIdle()
            vm.enqueue(); advanceUntilIdle()
            assertEquals(listOf(b), scheduled)
            assertEquals(DownloadStatus.Failed, queue.requests.value.first { it.videoId == a }.status)
            assertTrue(vm.link.value.isNotEmpty())
            assertFalse(vm.busy.value)
            fail = false
            vm.enqueue(); advanceUntilIdle()
            assertEquals(listOf(b, a), scheduled)
            assertEquals(DownloadStatus.Queued, queue.requests.value.first { it.videoId == a }.status)
            assertEquals("", vm.link.value)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `changing from playlist to video discards late playlist result`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val pending = CompletableDeferred<PlaylistMetadata>()
            val source = com.pedroaba.spix.data.PlaylistMetadataSource { withContext(NonCancellable) { pending.await() } }
            val vm = SpixViewModel(Queue(), SavedStateHandle(), VideoMetadataSource { video(it.videoId) }, downloads, source)
            store.put("vm", vm)
            vm.updateLink("https://youtube.com/watch?v=$a&list=PLtest"); advanceTimeBy(601); runCurrent()
            vm.updateLink("https://youtu.be/$a"); advanceTimeBy(601); runCurrent()
            pending.complete(PlaylistMetadata("PLtest", "Old playlist", listOf(video(b)))); advanceUntilIdle()
            assertEquals(a, (vm.preview.value as VideoPreviewState.Ready).video.videoId)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `completed and pending videos are skipped while cancelled downloads can be queued again`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val queue = Queue()
            val c = "abcdefghijk"
            queue.requests.value = listOf(
                DownloadRequest(a, video(a).url, 0, video(a), DownloadStatus.Completed),
                DownloadRequest(b, video(b).url, 0, video(b), DownloadStatus.Downloading),
                DownloadRequest(c, video(c).url, 0, video(c), DownloadStatus.Cancelled),
            )
            val source = com.pedroaba.spix.data.PlaylistMetadataSource { PlaylistMetadata(it.playlistId, "Playlist", listOf(video(a), video(b), video(c))) }
            val vm = SpixViewModel(queue, SavedStateHandle(), VideoMetadataSource { error("Unexpected video") }, downloads, source)
            store.put("vm", vm)
            vm.updateLink("https://youtube.com/playlist?list=PLtest"); advanceUntilIdle()
            vm.enqueue(); advanceUntilIdle()
            assertEquals(listOf(c), scheduled)
            assertEquals(DownloadStatus.Completed, queue.requests.value.first { it.videoId == a }.status)
            assertEquals(DownloadStatus.Downloading, queue.requests.value.first { it.videoId == b }.status)
            assertEquals(DownloadStatus.Queued, queue.requests.value.first { it.videoId == c }.status)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `playlist import waits for confirmation uses edited name and reuses completed videos`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val queue = Queue()
            queue.requests.value = listOf(DownloadRequest(a, video(a).url, 0, video(a), DownloadStatus.Completed))
            val imports = mutableListOf<PlaylistMetadata>()
            val source = com.pedroaba.spix.data.PlaylistMetadataSource { PlaylistMetadata(it.playlistId, "Original", listOf(video(a), video(b))) }
            val vm = SpixViewModel(queue, SavedStateHandle(), VideoMetadataSource { error("Unexpected video") }, downloads, source, { imports += it })
            store.put("vm", vm)
            vm.updateLink("https://youtube.com/playlist?list=PLtest"); advanceUntilIdle()
            assertEquals("Original", vm.playlistName.value)
            assertTrue(imports.isEmpty()); assertTrue(scheduled.isEmpty())
            assertEquals(listOf(a, b), (vm.preview.value as VideoPreviewState.PlaylistReady).playlist.videos.map { it.videoId })
            vm.updatePlaylistName("  Minha viagem  ")
            vm.enqueue(); advanceUntilIdle()
            assertEquals("Minha viagem", imports.single().title)
            assertEquals(listOf(a, b), imports.single().videos.map { it.videoId })
            assertEquals(listOf(b), scheduled)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `playlist creation failures preserve input and can be retried without starting downloads`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val queue = Queue()
            var fail = true
            val source = com.pedroaba.spix.data.PlaylistMetadataSource { PlaylistMetadata(it.playlistId, "Original", listOf(video(a))) }
            val vm = SpixViewModel(queue, SavedStateHandle(), VideoMetadataSource { error("Unexpected video") }, downloads, source,
                { if (fail) throw IOException("disk unavailable") })
            store.put("vm", vm)
            vm.updateLink("https://youtube.com/playlist?list=PLtest"); advanceUntilIdle()
            vm.updatePlaylistName("  "); vm.enqueue(); advanceUntilIdle()
            assertTrue(queue.requests.value.isEmpty())
            vm.updatePlaylistName("Minha playlist"); vm.enqueue(); advanceUntilIdle()
            assertTrue(scheduled.isEmpty()); assertTrue(vm.link.value.isNotBlank())
            fail = false
            vm.enqueue(); advanceUntilIdle()
            assertEquals(listOf(a), scheduled)
            assertEquals("", vm.link.value)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `file deletion must succeed before catalog removal and duplicate requests are ignored`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val queue = Queue()
            queue.requests.value = listOf(DownloadRequest(a, video(a).url, 0, video(a), DownloadStatus.Completed, localUri = "file:///video.mp4"))
            var fail = true
            var calls = 0
            val vm = SpixViewModel(queue, SavedStateHandle(), VideoMetadataSource { video(it.videoId) }, downloads, playlists,
                deleteFile = {
                    calls++
                    assertTrue(queue.removed.isEmpty())
                    if (fail) throw IOException("denied")
                })
            store.put("vm", vm)
            vm.remove(a); vm.remove(a); advanceUntilIdle()
            assertEquals(1, calls); assertTrue(queue.removed.isEmpty()); assertNotNull(queue.get(a))
            fail = false
            vm.remove(a); advanceUntilIdle()
            assertEquals(2, calls); assertEquals(listOf(a), queue.removed); assertNull(queue.get(a))
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `batch deletion keeps inaccessible files and continues with the remaining selection`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val queue = Queue()
            val third = "abcdefghijk"
            val unselected = "abcdefghij0"
            queue.requests.value = listOf(a, b, third, unselected).map { id ->
                DownloadRequest(id, video(id).url, 0, video(id), DownloadStatus.Completed, localUri = "file:///$id")
            }
            val attempts = mutableListOf<String>()
            val vm = SpixViewModel(queue, SavedStateHandle(), VideoMetadataSource { video(it.videoId) }, downloads, playlists,
                deleteFile = { uri ->
                    attempts += uri
                    if (uri == "file:///$b") throw IOException("denied")
                })
            store.put("vm", vm)
            vm.removeMany(listOf(a, b, a, third))
            advanceUntilIdle()
            assertEquals(listOf("file:///$a", "file:///$b", "file:///$third"), attempts)
            assertEquals(listOf(a, third), queue.removed)
            assertEquals(listOf(b, unselected), queue.requests.value.map { it.videoId })
            assertFalse(vm.removalBusy.value)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `selected retry is guarded against repeated clicks and leaves unselected items alone`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val queue = Queue()
            val c = "abcdefghijk"
            queue.requests.value = listOf(DownloadRequest(a, video(a).url, 0, video(a), DownloadStatus.Failed),
                DownloadRequest(b, video(b).url, 0, video(b), DownloadStatus.Failed),
                DownloadRequest(c, video(c).url, 0, video(c), DownloadStatus.Completed, localUri = "content://kept"))
            val vm = SpixViewModel(queue, SavedStateHandle(), VideoMetadataSource { video(it.videoId) }, downloads, playlists)
            store.put("vm", vm)
            vm.retryDownloads(com.pedroaba.spix.data.DownloadFilter.Failed, listOf(a, a, c))
            vm.retryDownloads(com.pedroaba.spix.data.DownloadFilter.Failed, listOf(a))
            advanceUntilIdle()
            assertEquals(1, queue.retryCalls); assertEquals(listOf(a), scheduled)
            assertEquals(DownloadStatus.Failed, queue.get(b)?.status)
            assertEquals("content://kept", queue.get(c)?.localUri)
            assertFalse(vm.retryBusy.value)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `retry all schedules an ordered group and respects the current filter`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val queue = Queue()
            queue.requests.value = listOf(DownloadRequest(a, video(a).url, 0, video(a), DownloadStatus.Failed), DownloadRequest(b, video(b).url, 0, video(b), DownloadStatus.Failed))
            val batches = mutableListOf<List<String>>()
            val scheduler = object : com.pedroaba.spix.download.DownloadScheduler {
                override suspend fun start(videoId: String) = Unit
                override suspend fun startPlaylist(videoIds: List<String>, title: String): Set<String> { batches += videoIds; return emptySet() }
                override suspend fun cancel(videoId: String) = Unit
            }
            val vm = SpixViewModel(queue, SavedStateHandle(), VideoMetadataSource { video(it.videoId) }, scheduler, playlists)
            store.put("vm", vm)
            vm.retryDownloads(com.pedroaba.spix.data.DownloadFilter.Failed, null); advanceUntilIdle()
            assertEquals(listOf(listOf(a,b)), batches)
            assertTrue(queue.requests.value.all { it.status == DownloadStatus.Queued })
            assertFalse(vm.retryBusy.value)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `bulk scheduling failure preserves a cancellation that happened while scheduling`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val queue = Queue()
            queue.requests.value = listOf(DownloadRequest(a, video(a).url, 0, video(a), DownloadStatus.Failed))
            val scheduler = object : com.pedroaba.spix.download.DownloadScheduler {
                override suspend fun start(videoId: String) {
                    queue.updateDownload(videoId) { it.copy(status = DownloadStatus.Cancelled) }
                    throw IOException("scheduler unavailable")
                }
                override suspend fun cancel(videoId: String) = Unit
            }
            val vm = SpixViewModel(queue, SavedStateHandle(), VideoMetadataSource { video(it.videoId) }, scheduler, playlists)
            store.put("vm", vm)
            vm.retryDownloads(com.pedroaba.spix.data.DownloadFilter.Failed, null); advanceUntilIdle()
            assertEquals(DownloadStatus.Cancelled, queue.get(a)?.status)
            assertFalse(vm.retryBusy.value)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

}
