package com.pedroaba.spix.data

import com.pedroaba.spix.domain.*
import org.junit.Assert.*
import org.junit.Test

class LegacyImportTest {
    @Test fun `migration preserves media identity location metadata and transfer`() {
        val row = decodeLegacyRequests("""[{"id":"jNQXAC9IVRw","url":"https://youtu.be/jNQXAC9IVRw","at":123,"status":"Completed","progress":100,"localUri":"content://media/external/downloads/8","duration":"0:19","metadata":{"title":"Título","author":"Canal","thumbnail":"https://example.com/image.jpg"},"transfer":{"bytes":100,"total":200,"kind":"Video"}}]""").single()
        assertEquals("jNQXAC9IVRw", row.videoId)
        assertEquals("content://media/external/downloads/8", row.localUri)
        assertEquals(DownloadStatus.Completed, row.status)
        assertEquals(123L, row.addedAt)
        assertEquals("Título", row.metadata?.title)
        assertEquals(50, row.transfer?.percent)
    }
    @Test fun `legacy links without metadata are preserved`() {
        val rows = decodeLegacyRequests("""[{"id":"jNQXAC9IVRw","url":"https://youtu.be/jNQXAC9IVRw","at":1}]""")
        assertEquals(DownloadStatus.Queued, rows.single().status)
        assertNull(rows.single().metadata)
    }
    @Test(expected = Exception::class) fun `malformed migration fails instead of silently emptying the library`() {
        decodeLegacyRequests("""[{"id":"ok","url":"url","at":1}, {"at":2}]""")
    }
    @Test fun `an empty migration remains empty`() { assertTrue(decodeLegacyRequests("[]").isEmpty()) }
}
