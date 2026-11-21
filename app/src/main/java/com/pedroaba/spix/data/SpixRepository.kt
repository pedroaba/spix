package com.pedroaba.spix.data

import androidx.paging.PagingData
import com.pedroaba.spix.domain.*
import kotlinx.coroutines.flow.*

sealed interface StorageState {
    data object Loading : StorageState
    data object Ready : StorageState
    data class Failed(val message: String) : StorageState
}
private val readyState = MutableStateFlow<StorageState>(StorageState.Ready)
private val noProgress = MutableStateFlow<DownloadRequest?>(null)

interface SpixRepository {
    val storageState: StateFlow<StorageState> get() = readyState
    val liveProgress: StateFlow<DownloadRequest?> get() = noProgress
    val counts: Flow<CatalogCounts> get() = flowOf(CatalogCounts(0, 0, 0, 0))
    val activeDownload: Flow<DownloadRequest?> get() = flowOf(null)
    suspend fun awaitReady() = Unit
    suspend fun retryInitialization() = Unit
    fun library(query: LibraryQuery): Flow<PagingData<Track>> = flowOf(PagingData.empty())
    fun libraryCount(query: LibraryQuery): Flow<Int> = flowOf(0)
    fun downloads(completed: Boolean): Flow<PagingData<DownloadRequest>> = flowOf(PagingData.empty())
    fun downloads(filter: DownloadFilter): Flow<PagingData<DownloadRequest>> = downloads(filter == DownloadFilter.Completed)
    suspend fun get(videoId: String): DownloadRequest?
    suspend fun getByUri(uri: String): DownloadRequest? = playbackQueue().firstOrNull { it.localUri == uri }
    suspend fun playbackQueue(): List<DownloadRequest> = emptyList()
    suspend fun savedMediaUris(): List<String> = playbackQueue().mapNotNull { it.localUri }.distinct()
    suspend fun enqueue(video: VideoMetadata): Boolean
    suspend fun enqueueBatch(videos: List<VideoMetadata>): List<String> {
        val result = mutableListOf<String>()
        for (video in videos.distinctBy { it.videoId }) {
            val old = get(video.videoId)
            if (old != null && old.status !in setOf(DownloadStatus.Failed, DownloadStatus.Cancelled)) continue
            enqueue(video)
            if (old != null) updateDownload(video.videoId) { it.copy(status = DownloadStatus.Queued, progress = 0, transfer = null, error = null) }
            result += video.videoId
        }
        return result
    }
    suspend fun updateDownload(videoId: String, transform: (DownloadRequest) -> DownloadRequest)
    suspend fun removeRequest(videoId: String)
    suspend fun retryDownloads(filter: DownloadFilter, ids: List<String>? = null): List<String> = emptyList()
    suspend fun recordBatch(ids: List<String>, title: String?) = Unit
    suspend fun batch(videoId: String): DownloadBatchRecord? = null
    suspend fun cancelActiveDownloads() = Unit
    suspend fun nextQueued(): String? = null
    suspend fun hasQueued(): Boolean = false
    suspend fun recoverRunning() = Unit
    fun observeStatus(id: String): Flow<DownloadStatus?> = flowOf(null)
}
