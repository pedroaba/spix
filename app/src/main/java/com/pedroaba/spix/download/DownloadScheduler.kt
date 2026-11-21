package com.pedroaba.spix.download

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface DownloadScheduler {
    suspend fun start(videoId: String)
    suspend fun cancel(videoId: String)
}

class WorkDownloadScheduler(context: Context) : DownloadScheduler {
    private val manager = WorkManager.getInstance(context)
    override suspend fun start(videoId: String) = withContext(Dispatchers.IO) {
        val work = OneTimeWorkRequestBuilder<VideoDownloadWorker>()
            .setInputData(workDataOf("videoId" to videoId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresStorageNotLow(true).build())
            .addTag("spix-download").build()
        manager.enqueueUniqueWork(name(videoId), ExistingWorkPolicy.KEEP, work).result.get()
        Unit
    }
    override suspend fun cancel(videoId: String) = withContext(Dispatchers.IO) {
        manager.cancelUniqueWork(name(videoId)).result.get()
        Unit
    }
    private fun name(id: String) = "spix-video-$id"
}
