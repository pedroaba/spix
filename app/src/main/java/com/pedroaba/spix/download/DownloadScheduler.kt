package com.pedroaba.spix.download

import android.content.Context
import androidx.work.*
import com.pedroaba.spix.data.SpixRepository
import com.pedroaba.spix.domain.DownloadStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex

interface DownloadScheduler {
    suspend fun start(videoId: String)
    suspend fun startBatch(videoIds: List<String>): Set<String> {
        val failures = mutableSetOf<String>()
        for (id in videoIds) try { start(id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failures += id }
        return failures
    }
    suspend fun startPlaylist(videoIds: List<String>, title: String): Set<String> = startBatch(videoIds)
    suspend fun cancel(videoId: String)
    suspend fun cancelAll() = Unit
}

/** A durable pump, not one waiting foreground worker/native process per video. */
class WorkDownloadScheduler(private val context: Context, private val repository: SpixRepository) : DownloadScheduler {
    private val manager = WorkManager.getInstance(context)
    private val controlPreferences = context.getSharedPreferences("spix_download_controls", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal val consumerGate = Mutex()
    private val startup = scope.async {
        repository.awaitReady()
        // Retire the old stuck queue once when upgrading to explicit queue cancellation.
        if (!controlPreferences.getBoolean("cancel_controls_v1", false)) {
            repository.cancelActiveDownloads()
            manager.cancelAllWorkByTag("spix-queue-pump").result.get()
            check(controlPreferences.edit().putBoolean("cancel_controls_v1", true).commit())
        }
        // The previous version used one work with this tag per video. Stop it after migration succeeds.
        manager.cancelAllWorkByTag("spix-download").result.get()
        repository.recoverRunning()
    }
    suspend fun bootstrap() {
        startup.await()
        if (repository.hasQueued()) wake()
        else context.getSystemService(android.app.NotificationManager::class.java).let { notifications ->
            notifications.activeNotifications.filter { it.notification.channelId == "downloads" }
                .forEach { notifications.cancel(it.id) }
        }
    }
    internal suspend fun awaitStartup() { startup.await() }
    internal suspend fun wakeQueuedDownloads() {
        awaitStartup()
        if (repository.hasQueued()) wake()
    }
    private suspend fun wake() {
        // User actions start a foreground service directly: no JobScheduler runtime quota.
        // Background recovery may be denied a foreground start; WorkManager remains durable fallback.
        try {
            androidx.core.content.ContextCompat.startForegroundService(context,
                android.content.Intent(context, DownloadQueueService::class.java))
        } catch (failure: IllegalStateException) {
            defer()
        }
    }
    internal suspend fun defer() = withContext(Dispatchers.IO) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresStorageNotLow(true).apply {
            if (android.os.Build.VERSION.SDK_INT >= 28) setRequiredNetworkRequest(
                android.net.NetworkRequest.Builder().addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED).build(), NetworkType.CONNECTED)
        }.build()
        val work = OneTimeWorkRequestBuilder<DownloadQueueWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
            .addTag("spix-queue-pump").build()
        // Appending also closes the race between an empty drain and a newly committed batch.
        manager.enqueueUniqueWork("spix-download-queue-v2", ExistingWorkPolicy.APPEND_OR_REPLACE, work).result.get()
        Unit
    }
    override suspend fun start(videoId: String) { startBatch(listOf(videoId)) }
    override suspend fun startBatch(videoIds: List<String>): Set<String> {
        startup.await(); repository.recordBatch(videoIds, null); wake(); return emptySet()
    }
    override suspend fun startPlaylist(videoIds: List<String>, title: String): Set<String> {
        startup.await(); repository.recordBatch(videoIds, title); wake(); return emptySet()
    }
    override suspend fun cancelAll() = withContext(Dispatchers.IO) {
        startup.await()
        repository.cancelActiveDownloads()
        context.stopService(android.content.Intent(context, DownloadQueueService::class.java))
        manager.cancelAllWorkByTag("spix-queue-pump").result.get()
        manager.cancelAllWorkByTag("spix-download").result.get()
        Unit
    }
    override suspend fun cancel(videoId: String) {
        repository.updateDownload(videoId) { if (it.status == DownloadStatus.Completed) it else it.copy(status = DownloadStatus.Cancelled, transfer = null) }
    }
}
