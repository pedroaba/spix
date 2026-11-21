package com.pedroaba.spix.data

import com.pedroaba.spix.domain.*
import org.junit.Assert.*
import org.junit.Test

class EnqueuePolicyTest {
    private fun video(id: String) = VideoMetadata(id, "https://youtube.com/watch?v=$id", id, "Canal", null)
    @Test fun `batch deduplicates and preserves requested order`() {
        assertEquals(listOf("a", "b"), planEnqueue(listOf(video("a"), video("b"), video("a")), emptyMap()).map { it.videoId })
    }
    @Test fun `only failed and cancelled items can be queued again`() {
        val states = DownloadStatus.entries.associate { it.name to it }
        val candidates = states.keys.map(::video)
        assertEquals(listOf("Failed", "Cancelled"), planEnqueue(candidates, states).map { it.videoId })
    }
    @Test fun `one thousand videos remain a single bounded import plan`() {
        val items = (0 until 1000).map { video("$it") }
        assertEquals(1000, planEnqueue(items + items, emptyMap()).size)
    }
}
