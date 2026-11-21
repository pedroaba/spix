package com.pedroaba.spix.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.work.*
import com.pedroaba.spix.MainActivity
import com.pedroaba.spix.R
import com.pedroaba.spix.SpixApplication
import com.pedroaba.spix.domain.DownloadStatus
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import com.pedroaba.spix.domain.DownloadTransfer

/** Compatibility entry for already-enqueued pre-migration work. */
class VideoDownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as SpixApplication
        app.downloadScheduler.startBatch(emptyList())
        return Result.success()
    }
}

internal class VideoTransferTask(
    private val applicationContext: Context,
    private val taskId: String,
    private val foregroundPublisher: suspend (ForegroundInfo) -> Unit,
) {
    private val app = applicationContext.applicationContext as SpixApplication
    private val notificationId = DOWNLOAD_NOTIFICATION_ID
    private suspend fun setForeground(info: ForegroundInfo) = foregroundPublisher(info)
    private var currentVideoId: String? = null
    private var batch: com.pedroaba.spix.data.DownloadBatchRecord? = null
    suspend fun run(videoId: String) {
        currentVideoId = videoId
        val item = app.repository.get(videoId) ?: return
        if (item.status != DownloadStatus.Queued) return
        batch = app.repository.batch(videoId)
        val directory = File(applicationContext.cacheDir, "downloads/$taskId-$videoId")
        var published: Uri? = null
        var committed = false
        try {
            applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("downloads", "Downloads", NotificationManager.IMPORTANCE_LOW)
            )
            val title = item.metadata?.title ?: "Vídeo do YouTube"
            setForeground(foreground(title, DownloadStatus.Queued))
            val file = downloadWithProgress(videoId, item.url, title, directory)
            currentCoroutineContext().ensureActive()
            app.repository.updateDownload(videoId) { if (it.status == DownloadStatus.Cancelled) it else it.copy(status = DownloadStatus.Saving, progress = 0, transfer = null) }
            setForeground(foreground(title, DownloadStatus.Saving))
            val duration = withContext(Dispatchers.IO) {
                runCatching {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(file.absolutePath)
                        val seconds = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) / 1000
                        "%d:%02d".format(seconds / 60, seconds % 60)
                    } finally { retriever.release() }
                }.getOrDefault("")
            }
            if (app.repository.get(videoId)?.status == DownloadStatus.Cancelled) return
            val safeTitle = (item.metadata?.title ?: videoId).replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(100).trim().ifEmpty { videoId }
            published = app.videoStorage.createVideo("$safeTitle [$videoId].mp4")
            val active = currentCoroutineContext()
            app.videoStorage.writeVideo(published) { output ->
                file.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        active.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                app.repository.updateDownload(videoId) {
                    if (it.status == DownloadStatus.Cancelled) it else {
                        it.copy(status = DownloadStatus.Completed, progress = 100, localUri = published.toString(), duration = duration, error = null, transfer = null)
                    }
                }
                committed = app.repository.get(videoId)?.let { it.status == DownloadStatus.Completed && it.localUri == published.toString() } == true
            }
            return
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                app.repository.updateDownload(videoId) { if (it.status == DownloadStatus.Completed) it else if (it.status == DownloadStatus.Cancelled) it else it.copy(status = DownloadStatus.Queued, progress = 0, transfer = null, error = null) }
            }
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("SpixDownload", "Download failed", error)
            val retry = !DownloadNetwork(applicationContext).isOnline() || isRetryableDownloadError(error)
            app.repository.updateDownload(videoId) {
                if (it.status in setOf(DownloadStatus.Cancelled, DownloadStatus.Completed)) it
                else if (retry) it.copy(status = DownloadStatus.Queued, progress = 0, transfer = null, error = null)
                else it.copy(status = DownloadStatus.Failed, error = downloadError(error), transfer = null)
            }
            // Stop the pump on environmental failures, instead of failing every remaining video.
            if (retry && app.repository.get(videoId)?.status == DownloadStatus.Queued) throw RetryDownloadException(error)
            return
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                if (!committed) published?.let { runCatching { app.videoStorage.deleteVideo(it) } }
                directory.deleteRecursively()
                applicationContext.getSystemService(NotificationManager::class.java).cancel(notificationId)
            }
        }
    }

    private suspend fun downloadWithProgress(videoId: String, url: String, title: String, directory: File): File = coroutineScope {
        // The native output reader must never wait for disk writes or notification IPC.
        val updates = Channel<DownloadUpdate>(Channel.CONFLATED)
        val publisher = launch(Dispatchers.IO) {
            val gate = ProgressUpdateGate()
            var previous: DownloadUpdate? = null
            var lastNotification = 0L
            for (update in updates) {
                val now = android.os.SystemClock.elapsedRealtime()
                if (!gate.accept(update, now)) continue
                val status = when (update) {
                    DownloadUpdate.Preparing -> DownloadStatus.Preparing
                    is DownloadUpdate.Transfer -> DownloadStatus.Downloading
                    DownloadUpdate.Processing -> DownloadStatus.Processing
                }
                val transfer = (update as? DownloadUpdate.Transfer)?.data
                app.repository.updateDownload(videoId) { if (it.status in setOf(DownloadStatus.Cancelled, DownloadStatus.Completed)) it else it.copy(status = status, progress = transfer?.percent ?: 0, transfer = transfer) }
                val phaseChanged = previous?.javaClass != update.javaClass ||
                    (previous as? DownloadUpdate.Transfer)?.data?.kind != transfer?.kind
                if (phaseChanged || now - lastNotification >= 1000 || transfer?.percent == 100) {
                    setForeground(foreground(title, status, transfer))
                    lastNotification = now
                }
                previous = update
            }
        }
        try { app.downloader.download(url, directory, taskId) { updates.trySend(it) } }
        finally {
            // Flush the last transfer/stage before Saving or a terminal state can be persisted.
            updates.close()
            publisher.join()
        }
    }

    private fun foreground(title: String, status: DownloadStatus, transfer: DownloadTransfer? = null): ForegroundInfo {
        val open = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancel = PendingIntent.getBroadcast(applicationContext, title.hashCode(),
            Intent(applicationContext, CancelDownloadReceiver::class.java).putExtra("videoId", currentVideoId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val percent = transfer?.percent
        val stage = downloadStageLabel(status, transfer)
        val batchLabel = batch?.let { "${it.itemIndex} de ${it.total} · ${it.title}" }
        val detail = if (batchLabel == null) stage else "$batchLabel\n$title\n$stage"
        val cancelAll = PendingIntent.getBroadcast(applicationContext, 0x53504958,
            Intent(applicationContext, CancelDownloadReceiver::class.java).setAction("com.pedroaba.spix.CANCEL_ALL"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(applicationContext, "downloads")
            .setSmallIcon(R.drawable.ic_download_notification).setContentTitle(title)
            .setContentText(downloadNotificationText(status, transfer, batch))
            .setSubText(batch?.title)
            .setStyle(Notification.BigTextStyle().bigText(detail))
            .setProgress(100, percent ?: 0, percent == null).setOnlyAlertOnce(true).setOngoing(true)
            .setContentIntent(open).addAction(Notification.Action.Builder(null, "Cancelar vídeo", cancel).build())
            .addAction(Notification.Action.Builder(null, "Cancelar todos", cancelAll).build()).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(notificationId, notification)
    }
}

internal fun downloadError(error: Exception): String {
    val text = error.message.orEmpty().lowercase()
    return when {
        error is SecurityException -> "Sem acesso à pasta. Escolha novamente o destino nas configurações."
        "no space" in text || "enospc" in text -> "Sem espaço suficiente no celular. Libere espaço e tente novamente."
        "private" in text || "unavailable" in text || "not available" in text -> "Este vídeo está privado ou indisponível para download."
        "sign in" in text || "403" in text || "bot" in text -> "O YouTube bloqueou esta tentativa. Tente novamente mais tarde ou use outro vídeo público."
        "live" in text || "match filter" in text -> "Transmissões ao vivo não são suportadas nesta versão."
        "max-filesize" in text || "larger than" in text -> "O vídeo excede o limite de 2 GB desta versão."
        else -> "Não foi possível baixar o vídeo. Confira a conexão e a pasta escolhida e tente novamente."
    }
}

internal fun downloadNotificationText(status: DownloadStatus, transfer: DownloadTransfer?, batch: com.pedroaba.spix.data.DownloadBatchRecord?): String {
    val stage = downloadStageLabel(status, transfer)
    return if (batch == null) stage else "${batch.itemIndex} de ${batch.total} · $stage"
}
