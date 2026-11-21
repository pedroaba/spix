package com.pedroaba.spix.data

import com.pedroaba.spix.domain.VideoMetadata
import com.pedroaba.spix.domain.YouTubeLink
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

class MetadataHttpException(val status: Int) : IOException("YouTube oEmbed HTTP $status")
class MetadataFormatException(cause: Throwable? = null) : IOException("Resposta de metadados inválida", cause)

/** Uses YouTube's public oEmbed endpoint; no API key, HTML scraping or iframe execution. */
class YouTubeMetadataRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).build(),
    private val endpoint: HttpUrl = "https://www.youtube.com/oembed".toHttpUrl(),
) : VideoMetadataSource {
    private data class Cached(val video: VideoMetadata, val at: Long)
    private val cache = linkedMapOf<String, Cached>()

    override suspend fun fetch(link: YouTubeLink): VideoMetadata {
        synchronized(cache) {
            cache[link.videoId]?.takeIf { System.nanoTime() - it.at < TimeUnit.MINUTES.toNanos(5) }?.let { return it.video }
        }
        val request = Request.Builder().url(endpoint.newBuilder()
            .addQueryParameter("url", link.url).addQueryParameter("format", "json").build())
            .header("Accept", "application/json").build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val video = response.use {
                            if (!it.isSuccessful) throw MetadataHttpException(it.code)
                            val body = it.body ?: throw MetadataFormatException()
                            if (body.contentLength() > 131_072) throw MetadataFormatException()
                            parse(link, body.string())
                        }
                        if (continuation.isActive) {
                            synchronized(cache) {
                                if (cache.size >= 20) cache.remove(cache.keys.first())
                                cache[link.videoId] = Cached(video, System.nanoTime())
                            }
                            continuation.resume(video)
                        }
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            })
        }
    }

    companion object {
        internal fun parse(link: YouTubeLink, json: String): VideoMetadata {
            try {
                val data = JSONObject(json)
                val title = (data.opt("title") as? String).orEmpty().trim()
                val author = (data.opt("author_name") as? String).orEmpty().trim()
                if (data.optString("type") != "video" || title.isBlank() || author.isBlank()) throw MetadataFormatException()
                val thumbnail = (data.opt("thumbnail_url") as? String)?.takeIf { value ->
                    runCatching { val uri = URI(value); uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null }.getOrDefault(false)
                }
                return VideoMetadata(link.videoId, link.url, title, author, thumbnail)
            } catch (error: MetadataFormatException) { throw error }
            catch (error: Exception) { throw MetadataFormatException(error) }
        }
    }
}
