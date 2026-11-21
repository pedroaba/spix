package com.pedroaba.spix.download

import com.pedroaba.spix.data.DownloadBatchRecord
import com.pedroaba.spix.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadQueueControlTest {
    private class BackgroundServiceStartNotAllowedException : IllegalStateException("app is in background")

    @Test fun transientSocketAndExtractorFailuresWaitInsteadOfFailingTheNextVideo() {
        assertTrue(isRetryableDownloadError(BackgroundServiceStartNotAllowedException()))
        assertTrue(isRetryableDownloadError(IllegalStateException("foreground update", BackgroundServiceStartNotAllowedException())))
        assertTrue(isRetryableDownloadError(java.net.SocketTimeoutException()))
        assertTrue(isRetryableDownloadError(java.net.UnknownHostException()))
        assertTrue(isRetryableDownloadError(java.io.IOException("Unable to download webpage: connection reset")))
        assertTrue(isRetryableDownloadError(java.io.IOException("HTTP Error 429: Too Many Requests")))
        assertTrue(isRetryableDownloadError(java.io.IOException("HTTP Error 403: Forbidden")))
        assertFalse(isRetryableDownloadError(java.io.IOException("Video unavailable: private video")))
        assertFalse(isRetryableDownloadError(SecurityException("Folder denied")))
        assertFalse(isRetryableDownloadError(java.io.IOException("ENOSPC: no space left on device")))
    }

    @Test fun retrySignalReachesThePumpAndDoesNotAdvanceToTheNextVideo() = runTest {
        var nextVideoStarted = false
        try {
            runQueueTransfer(MutableStateFlow(DownloadStatus.Downloading), MutableStateFlow(true)) {
                throw RetryDownloadException(java.net.SocketTimeoutException())
            }
            nextVideoStarted = true
            fail("The pump must receive the retry signal")
        } catch (_: RetryDownloadException) { }
        assertFalse(nextVideoStarted)
    }
    @Test fun cancellingCurrentTransferStopsItsOperationAndAllowsQueueToContinue() = runTest {
        val status = MutableStateFlow<DownloadStatus?>(DownloadStatus.Downloading)
        val online = MutableStateFlow(true)
        var stopped = false
        val job = async {
            runQueueTransfer(status, online) {
                try { awaitCancellation() } finally { stopped = true }
            }
        }
        runCurrent()
        status.value = DownloadStatus.Cancelled
        runCurrent()
        assertFalse(job.await())
        assertTrue(stopped)
    }
    @Test fun losingInternetStopsTransferAndSignalsQueueToWait() = runTest {
        val status = MutableStateFlow<DownloadStatus?>(DownloadStatus.Downloading)
        val online = MutableStateFlow(true)
        var stopped = false
        val job = async {
            runQueueTransfer(status, online) {
                try { awaitCancellation() } finally { stopped = true }
            }
        }
        runCurrent()
        online.value = false
        runCurrent()
        assertTrue(job.await())
        assertTrue(stopped)
    }
    @Test fun parentCancellationStopsTheOperationWithoutBeingSwallowed() = runTest {
        var stopped = false
        val job = launch {
            runQueueTransfer(MutableStateFlow(DownloadStatus.Downloading), MutableStateFlow(true)) {
                try { awaitCancellation() } finally { stopped = true }
            }
        }
        runCurrent(); job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(stopped)
    }
    @Test fun notificationShowsCurrentPercentageAndPlaylistPosition() {
        val transfer = DownloadTransfer(37, 100)
        assertEquals("Baixando mídia · 37%", downloadNotificationText(DownloadStatus.Downloading, transfer, null))
        val batch = DownloadBatchRecord("video", "batch", "Minha playlist", 1, 350, 0)
        assertEquals("1 de 350 · Baixando mídia · 37%", downloadNotificationText(DownloadStatus.Downloading, transfer, batch))
        assertEquals("1 de 350 · Finalizando arquivo…", downloadNotificationText(DownloadStatus.Processing, null, batch))
    }
}
