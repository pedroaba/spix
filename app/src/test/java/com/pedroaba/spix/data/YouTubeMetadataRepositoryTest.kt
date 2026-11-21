package com.pedroaba.spix.data

import com.pedroaba.spix.domain.YouTubeLink
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class YouTubeMetadataRepositoryTest {
    private val server = MockWebServer()
    private val link = requireNotNull(YouTubeLink.parse("https://youtu.be/dQw4w9WgXcQ?t=10"))
    private val response = """{"type":"video","title":"Música & vídeo","author_name":"Canal real","thumbnail_url":"https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"}"""

    @Before fun setUp() { server.start() }
    @After fun tearDown() { server.shutdown() }

    @Test fun `fetches real response fields using a canonical URL and caches by video ID`() = runBlocking {
        server.enqueue(MockResponse().setBody(response))
        val source = YouTubeMetadataRepository(endpoint = server.url("/oembed"))
        val video = source.fetch(link)
        assertEquals("Música & vídeo", video.title)
        assertEquals("Canal real", video.author)
        assertEquals(link.videoId, video.videoId)
        assertTrue(requireNotNull(video.thumbnailUrl).startsWith("https://"))
        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals(link.url, request.requestUrl?.queryParameter("url"))
        assertEquals("json", request.requestUrl?.queryParameter("format"))
        assertEquals(video, source.fetch(link))
        assertEquals(1, server.requestCount)
    }

    @Test fun `HTTP failure is surfaced and retry is not cached as failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setBody(response))
        val source = YouTubeMetadataRepository(endpoint = server.url("/oembed"))
        try { source.fetch(link); fail("Expected HTTP failure") }
        catch (error: MetadataHttpException) { assertEquals(404, error.status) }
        assertEquals("Canal real", source.fetch(link).author)
        assertEquals(2, server.requestCount)
    }

    @Test fun `rejects incomplete or malformed metadata and ignores insecure thumbnails`() {
        for (json in listOf("not JSON", "{}", """{"type":"video","title":null,"author_name":"A"}""", """{"type":"photo","title":"A","author_name":"B"}""")) {
            assertThrows(MetadataFormatException::class.java) { YouTubeMetadataRepository.parse(link, json) }
        }
        val noThumbnail = YouTubeMetadataRepository.parse(link, """{"type":"video","title":"A","author_name":"B","thumbnail_url":"http://example.com/image.jpg"}""")
        assertNull(noThumbnail.thumbnailUrl)
    }

    @Test fun `cancels an in-flight network request without waiting for the response`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val source = YouTubeMetadataRepository(endpoint = server.url("/oembed"))
        val request = async { source.fetch(link) }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
        request.cancelAndJoin()
        assertTrue(request.isCancelled)
    }
}
