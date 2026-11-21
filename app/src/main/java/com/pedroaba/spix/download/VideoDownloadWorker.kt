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

class VideoDownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private val app = context.applicationContext as SpixApplication
    private val notificationId = id.hashCode() and Int.MAX_VALUE

    override suspend fun doWork(): Result {
        val videoId = inputData.getString("videoId") ?: return Result.failure()
        val item = app.repository.requests.value.firstOrNull { it.videoId == videoId } ?: return Result.success()
        if (item.status == DownloadStatus.Completed) return Result.success()
        val directory = File(applicationContext.cacheDir, "downloads/$id")
        var published: Uri? = null
        var committed = false
        try {
            applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("downloads", "Downloads", NotificationManager.IMPORTANCE_LOW)
            )
            val title = item.metadata?.title ?: "Vídeo do YouTube"
            setForeground(foreground(title, DownloadStatus.Queued))
            app.repository.updateDownload(videoId) { it.copy(status = DownloadStatus.Queued, progress = 0, error = null, transfer = null) }
            val file = downloadWithProgress(videoId, item.url, title, directory)
            currentCoroutineContext().ensureActive()
            app.repository.updateDownload(videoId) { it.copy(status = DownloadStatus.Saving, progress = 0, transfer = null) }
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
                app.repository.updateDownload(videoId) { it.copy(status = DownloadStatus.Completed, progress = 100, localUri = published.toString(), duration = duration, error = null, transfer = null) }
                committed = true
            }
            return Result.success()
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                app.repository.updateDownload(videoId) { if (it.status == DownloadStatus.Completed) it else it.copy(status = DownloadStatus.Cancelled, error = null) }
            }
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("SpixDownload", "Download failed", error)
            app.repository.updateDownload(videoId) { it.copy(status = DownloadStatus.Failed, error = downloadError(error)) }
            return Result.failure()
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                if (!committed) published?.let { runCatching { app.videoStorage.deleteVideo(it) } }
                directory.deleteRecursively()
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
                app.repository.updateDownload(videoId) { it.copy(status = status, progress = transfer?.percent ?: 0, transfer = transfer) }
                val phaseChanged = previous?.javaClass != update.javaClass ||
                    (previous as? DownloadUpdate.Transfer)?.data?.kind != transfer?.kind
                if (phaseChanged || now - lastNotification >= 1000 || transfer?.percent == 100) {
                    setForeground(foreground(title, status, transfer))
                    lastNotification = now
                }
                previous = update
            }
        }
        try { app.downloader.download(url, directory, id.toString()) { updates.trySend(it) } }
        finally {
            // Flush the last transfer/stage before Saving or a terminal state can be persisted.
            updates.close()
            publisher.join()
        }
    }

    private fun foreground(title: String, status: DownloadStatus, transfer: DownloadTransfer? = null): ForegroundInfo {
        val open = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val percent = transfer?.percent
        val notification = Notification.Builder(applicationContext, "downloads")
            .setSmallIcon(R.drawable.ic_download_notification).setContentTitle(title)
            .setContentText(downloadStageLabel(status, transfer))
            .setProgress(100, percent ?: 0, percent == null).setOnlyAlertOnce(true).setOngoing(true)
            .setContentIntent(open).addAction(Notification.Action.Builder(null, "Cancelar", cancel).build()).build()
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
