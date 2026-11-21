package com.pedroaba.spix.data

import android.content.Context
import com.pedroaba.spix.domain.DownloadRequest
import com.pedroaba.spix.domain.DownloadTransfer
import com.pedroaba.spix.domain.TransferKind
import com.pedroaba.spix.domain.DownloadStatus
import com.pedroaba.spix.domain.MediaKind
import com.pedroaba.spix.domain.Track
import com.pedroaba.spix.domain.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Durable queue and offline library; legacy saved links remain readable. */
class LocalSpixRepository(context: Context) : SpixRepository {
    private val preferences = context.getSharedPreferences("spix", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutableRequests = MutableStateFlow(readRequests())
    override val requests = mutableRequests.asStateFlow()
    private val mutableTracks = MutableStateFlow(toTracks(mutableRequests.value))
    override val tracks = mutableTracks.asStateFlow()

    override suspend fun enqueue(video: VideoMetadata): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (mutableRequests.value.any { it.videoId == video.videoId }) {
                save(mutableRequests.value.map { if (it.videoId == video.videoId) it.copy(metadata = video) else it })
                return@withLock false
            }
            save(listOf(DownloadRequest(video.videoId, video.url, System.currentTimeMillis(), video)) + mutableRequests.value)
            true
        }
    }

    override suspend fun updateDownload(videoId: String, transform: (DownloadRequest) -> DownloadRequest) = withContext(Dispatchers.IO) {
        mutex.withLock { save(mutableRequests.value.map { if (it.videoId == videoId) transform(it) else it }) }
    }

    override suspend fun removeRequest(videoId: String) = withContext(Dispatchers.IO) {
        mutex.withLock { save(mutableRequests.value.filterNot { it.videoId == videoId }) }
    }

    private fun save(requests: List<DownloadRequest>) {
        val json = JSONArray().apply {
            requests.forEach { request ->
                put(JSONObject().put("id", request.videoId).put("url", request.url).put("at", request.addedAt)
                    .put("status", request.status.name).put("progress", request.progress).put("error", request.error)
                    .put("localUri", request.localUri).put("duration", request.duration).apply {
                    request.transfer?.let { transfer ->
                        put("transfer", JSONObject().put("bytes", transfer.downloadedBytes).put("total", transfer.totalBytes)
                            .put("estimate", transfer.totalIsEstimate).put("speed", transfer.speedBytesPerSecond)
                            .put("eta", transfer.etaSeconds).put("kind", transfer.kind.name))
                    }
                    request.metadata?.let { video ->
                        put("metadata", JSONObject().put("title", video.title).put("author", video.author).put("thumbnail", video.thumbnailUrl))
                    }
                })
            }
        }
        check(preferences.edit().putString("requests", json.toString()).commit()) { "Não foi possível salvar a fila." }
        mutableRequests.value = requests
        mutableTracks.value = toTracks(requests)
    }

    private fun readRequests(): List<DownloadRequest> = runCatching {
        val json = JSONArray(preferences.getString("requests", "[]"))
        List(json.length()) { index ->
            val item = json.getJSONObject(index)
            val id = item.getString("id")
            val url = item.getString("url")
            val metadata = item.optJSONObject("metadata")?.let { data ->
                val title = data.optString("title").trim()
                val author = data.optString("author").trim()
                if (title.isBlank() || author.isBlank()) null else VideoMetadata(id, url, title, author,
                    data.optString("thumbnail").takeIf { it.startsWith("https://") })
            }
            DownloadRequest(id, url, item.getLong("at"), metadata,
                status = DownloadStatus.entries.firstOrNull { it.name == item.optString("status") } ?: DownloadStatus.Queued,
                progress = item.optInt("progress").coerceIn(0, 100),
                error = item.optString("error").takeIf { it.isNotBlank() && it != "null" },
                localUri = item.optString("localUri").takeIf { it.isNotBlank() && it != "null" },
                duration = item.optString("duration"),
                transfer = item.optJSONObject("transfer")?.let { data ->
                    DownloadTransfer(data.optLong("bytes").coerceAtLeast(0),
                        totalBytes = data.optLong("total").takeIf { it > 0 },
                        totalIsEstimate = data.optBoolean("estimate"),
                        speedBytesPerSecond = data.optDouble("speed").takeIf { it.isFinite() && it > 0 },
                        etaSeconds = if (data.has("eta") && !data.isNull("eta")) data.optLong("eta").takeIf { it >= 0 } else null,
                        kind = TransferKind.entries.firstOrNull { it.name == data.optString("kind") } ?: TransferKind.Media)
                })
        }
    }.getOrDefault(emptyList())

    private fun toTracks(requests: List<DownloadRequest>) = requests.mapNotNull { item ->
        if (item.status != DownloadStatus.Completed || item.localUri == null) null
        else Track(item.videoId, item.metadata?.title ?: "Vídeo · ${item.videoId}", item.metadata?.author ?: "YouTube",
            item.duration, item.localUri, MediaKind.Video)
    }
}
