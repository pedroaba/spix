package com.pedroaba.spix.domain

@androidx.compose.runtime.Immutable
data class VideoMetadata(
    val videoId: String,
    val url: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String?,
)

data class PlaylistMetadata(
    val playlistId: String,
    val title: String,
    val videos: List<VideoMetadata>,
    val unavailableCount: Int = 0,
)

enum class DownloadStatus { Queued, Preparing, Downloading, Processing, Saving, Completed, Failed, Cancelled }
enum class TransferKind { Video, Audio, Media }
@androidx.compose.runtime.Immutable
data class DownloadTransfer(
    val downloadedBytes: Long,
    val totalBytes: Long? = null,
    val totalIsEstimate: Boolean = false,
    val speedBytesPerSecond: Double? = null,
    val etaSeconds: Long? = null,
    val kind: TransferKind = TransferKind.Media,
) {
    val percent: Int? get() = totalBytes?.takeIf { it > 0 }?.let {
        (downloadedBytes.toDouble() / it * 100).toInt().coerceIn(0, 100)
    }
}
@androidx.compose.runtime.Immutable
data class DownloadRequest(
    val videoId: String, val url: String, val addedAt: Long, val metadata: VideoMetadata? = null,
    val status: DownloadStatus = DownloadStatus.Queued, val progress: Int = 0,
    val error: String? = null, val localUri: String? = null, val duration: String = "",
    val transfer: DownloadTransfer? = null,
)

sealed interface VideoPreviewState {
    data object Idle : VideoPreviewState
    data class Loading(val videoId: String) : VideoPreviewState
    data class Ready(val video: VideoMetadata) : VideoPreviewState
    data class PlaylistReady(val playlist: PlaylistMetadata) : VideoPreviewState
    data class Failed(val videoId: String, val message: String) : VideoPreviewState
}
enum class MediaKind { Music, Video }
@androidx.compose.runtime.Immutable
data class Track(val id: String, val title: String, val artist: String, val duration: String, val localUri: String, val kind: MediaKind = MediaKind.Music, val thumbnailUrl: String? = null)
