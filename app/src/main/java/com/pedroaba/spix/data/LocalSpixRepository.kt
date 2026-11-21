package com.pedroaba.spix.data

import android.content.Context
import com.pedroaba.spix.domain.DownloadRequest
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

/** Persists requested links only. A future download worker will populate the offline library. */
class LocalSpixRepository(context: Context) : SpixRepository {
    private val preferences = context.getSharedPreferences("spix", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutableRequests = MutableStateFlow(readRequests())
    override val requests = mutableRequests.asStateFlow()
    override val tracks = MutableStateFlow<List<Track>>(emptyList()).asStateFlow()

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

    override suspend fun removeRequest(videoId: String) = withContext(Dispatchers.IO) {
        mutex.withLock { save(mutableRequests.value.filterNot { it.videoId == videoId }) }
    }

    private fun save(requests: List<DownloadRequest>) {
        val json = JSONArray().apply {
            requests.forEach { request ->
                put(JSONObject().put("id", request.videoId).put("url", request.url).put("at", request.addedAt).apply {
                    request.metadata?.let { video ->
                        put("metadata", JSONObject().put("title", video.title).put("author", video.author).put("thumbnail", video.thumbnailUrl))
                    }
                })
            }
        }
        check(preferences.edit().putString("requests", json.toString()).commit()) { "Não foi possível salvar a fila." }
        mutableRequests.value = requests
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
            DownloadRequest(id, url, item.getLong("at"), metadata)
        }
    }.getOrDefault(emptyList())
}
