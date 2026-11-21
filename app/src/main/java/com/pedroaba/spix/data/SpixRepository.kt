package com.pedroaba.spix.data

import com.pedroaba.spix.domain.DownloadRequest
import com.pedroaba.spix.domain.Track
import com.pedroaba.spix.domain.VideoMetadata
import kotlinx.coroutines.flow.StateFlow

interface SpixRepository {
    val requests: StateFlow<List<DownloadRequest>>
    val tracks: StateFlow<List<Track>>
    suspend fun enqueue(video: VideoMetadata): Boolean
    suspend fun updateDownload(videoId: String, transform: (DownloadRequest) -> DownloadRequest)
    suspend fun removeRequest(videoId: String)
}
