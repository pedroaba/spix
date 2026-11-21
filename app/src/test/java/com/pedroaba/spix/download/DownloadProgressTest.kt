package com.pedroaba.spix.download

import com.pedroaba.spix.domain.*
import org.junit.Assert.*
import org.junit.Test

class DownloadProgressTest {
    private fun line(fields: String) = "SPIX_TRANSFER:{$fields}"
    private fun transfer(fields: String) = (DownloadProgressParser.parse(line(fields)) as DownloadUpdate.Transfer).data

    @Test fun `numeric hooks expose real bytes percent speed and ETA without requiring terminal ETA text`() {
        val data = transfer(""""status":"downloading","downloaded":1048576,"total":4194304,"speed":524288.5,"eta":5.2,"vcodec":"avc1","acodec":"none"""")
        assertEquals(25, data.percent)
        assertEquals(1048576L, data.downloadedBytes)
        assertEquals(524288.5, data.speedBytesPerSecond!!, .001)
        assertEquals(6L, data.etaSeconds)
        assertEquals(TransferKind.Video, data.kind)
        assertFalse(data.totalIsEstimate)
    }

    @Test fun `unknown totals keep byte progress without inventing a percent and estimates remain marked`() {
        val unknown = transfer(""""status":"downloading","downloaded":8192,"total":"NA","estimate":null,"speed":null,"eta":"NA"""")
        assertNull(unknown.percent)
        assertEquals(8192L, unknown.downloadedBytes)
        assertNull(unknown.etaSeconds)
        val estimated = transfer(""""status":"downloading","downloaded":500,"total":null,"estimate":2000""")
        assertEquals(25, estimated.percent)
        assertTrue(estimated.totalIsEstimate)
        val exact = transfer(""""status":"downloading","downloaded":500,"total":1000,"estimate":2000""")
        assertEquals(50, exact.percent)
        assertFalse(exact.totalIsEstimate)
    }

    @Test fun `stream completion is distinct from the completion of the whole job`() {
        val video = transfer(""""status":"finished","downloaded":1000,"total":1000,"vcodec":"avc1","acodec":"none"""")
        val audio = transfer(""""status":"downloading","downloaded":10,"total":100,"vcodec":"none","acodec":"mp4a"""")
        assertEquals(100, video.percent)
        assertEquals(TransferKind.Audio, audio.kind)
        assertEquals("Baixando áudio · 10%", downloadStageLabel(DownloadStatus.Downloading, audio))
        assertEquals(DownloadUpdate.Processing, DownloadProgressParser.parse("SPIX_PROCESS:started"))
        assertEquals("Finalizando arquivo…", downloadStageLabel(DownloadStatus.Processing, null))
    }

    @Test fun `ordinary output malformed lines and impossible byte counts never fabricate progress`() {
        for (bad in listOf("[download] 25% ETA Unknown", "ERROR: HTTP 403", "SPIX_TRANSFER:not json",
            line(""""status":"downloading","downloaded":-1,"total":500"""),
            line(""""status":"downloading","downloaded":"NA"""),
            line(""""status":"anything","downloaded":50"""))) assertNull(bad, DownloadProgressParser.parse(bad))
        assertEquals(100, transfer(""""status":"downloading","downloaded":500,"estimate":400""").percent)
    }

    @Test fun `throttling never hides the first transfer its final bytes or the next media stage`() {
        val gate = ProgressUpdateGate(400)
        val first = DownloadUpdate.Transfer(DownloadTransfer(1, 100, kind = TransferKind.Video))
        assertTrue(gate.accept(DownloadUpdate.Preparing, 0))
        assertTrue(gate.accept(first, 1))
        assertFalse(gate.accept(first.copy(data = first.data.copy(downloadedBytes = 2)), 20))
        assertTrue(gate.accept(first.copy(data = first.data.copy(downloadedBytes = 100)), 30))
        assertTrue(gate.accept(first.copy(data = first.data.copy(downloadedBytes = 105), finished = true), 31))
        assertTrue(gate.accept(DownloadUpdate.Transfer(DownloadTransfer(1, 20, kind = TransferKind.Audio)), 32))
        assertTrue(gate.accept(DownloadUpdate.Processing, 33))
        assertFalse(gate.accept(DownloadUpdate.Processing, 34))
    }
}
