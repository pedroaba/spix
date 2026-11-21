package com.pedroaba.spix.data

import com.pedroaba.spix.domain.*
import org.json.JSONObject

internal fun DownloadRequest.mediaRecord() = MediaRecord(videoId, url, addedAt, metadata?.title, metadata?.author, metadata?.thumbnailUrl, localUri, duration)
internal fun DownloadRequest.jobRecord() = JobRecord(videoId, status.name, progress, error, transfer?.json())
internal fun MediaRecord.track() = Track(videoId, title ?: "Vídeo · $videoId", author ?: "YouTube", duration, requireNotNull(localUri), MediaKind.valueOf(kind), thumbnailUrl)
internal fun DownloadSnapshot.request(): DownloadRequest = DownloadRequest(media.videoId, media.url, media.addedAt,
    media.title?.let { VideoMetadata(media.videoId, media.url, it, media.author ?: "YouTube", media.thumbnailUrl) },
    DownloadStatus.valueOf(status), progress, error, media.localUri, media.duration, transfer?.let(::parseTransfer))
internal fun DownloadTransfer.json(): String = JSONObject().put("bytes", downloadedBytes).put("total", totalBytes).put("estimate", totalIsEstimate)
    .put("speed", speedBytesPerSecond).put("eta", etaSeconds).put("kind", kind.name).toString()
internal fun parseTransfer(json: String): DownloadTransfer {
    val data = JSONObject(json)
    return DownloadTransfer(data.optLong("bytes"), data.optLong("total").takeIf { it > 0 }, data.optBoolean("estimate"),
        data.optDouble("speed").takeIf { it.isFinite() && it > 0 },
        if (data.has("eta") && !data.isNull("eta")) data.optLong("eta").takeIf { it >= 0 } else null,
        TransferKind.entries.firstOrNull { it.name == data.optString("kind") } ?: TransferKind.Media)
}
