package com.pedroaba.spix.domain

data class VideoMetadata(
    val videoId: String,
    val url: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String?,
)

enum class DownloadStatus { Queued, Downloading, Saving, Completed, Failed, Cancelled }
data class DownloadRequest(
    val videoId: String, val url: String, val addedAt: Long, val metadata: VideoMetadata? = null,
    val status: DownloadStatus = DownloadStatus.Queued, val progress: Int = 0,
    val error: String? = null, val localUri: String? = null, val duration: String = "",
)

sealed interface VideoPreviewState {
    data object Idle : VideoPreviewState
    data class Loading(val videoId: String) : VideoPreviewState
    data class Ready(val video: VideoMetadata) : VideoPreviewState
    data class Failed(val videoId: String, val message: String) : VideoPreviewState
}
enum class MediaKind { Music, Video }
data class Track(val id: String, val title: String, val artist: String, val duration: String, val localUri: String, val kind: MediaKind = MediaKind.Music)
