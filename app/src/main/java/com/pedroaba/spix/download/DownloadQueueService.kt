package com.pedroaba.spix.download

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import com.pedroaba.spix.MainActivity
import com.pedroaba.spix.R
import com.pedroaba.spix.SpixApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

internal const val DOWNLOAD_NOTIFICATION_ID = 0x53504958

/** Owns transfers independently of activities; Room and WorkManager preserve pending work. */
class DownloadQueueService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pump: Job? = null
    private var latestStartId = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private val app get() = application as SpixApplication

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel("downloads", "Downloads", NotificationManager.IMPORTANCE_LOW))
        // Remove notifications left by the old per-worker notification IDs, preserving media controls.
        notifications.activeNotifications.filter { it.notification.channelId == "downloads" && it.id != DOWNLOAD_NOTIFICATION_ID }
            .forEach { notifications.cancel(it.id) }
        if (pump?.isActive == true) return START_REDELIVER_INTENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, "downloads").setSmallIcon(R.drawable.ic_download_notification)
            .setContentTitle("Downloads do Spix").setContentText("Preparando a fila…")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(DOWNLOAD_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(DOWNLOAD_NOTIFICATION_ID, notification)
        } catch (blocked: IllegalStateException) {
            app.processScope.launch { app.downloadScheduler.defer() }
            stopSelf(startId)
            return START_NOT_STICKY
        }
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Spix:Downloads")
            .apply { acquire(6 * 60 * 60 * 1000L) }
        pump = scope.launch {
            try {
                app.downloadScheduler.awaitStartup()
                app.downloadScheduler.consumerGate.withLock {
                    val network = DownloadNetwork(this@DownloadQueueService)
                    while (true) {
                        val videoId = app.repository.nextQueued() ?: break
                        if (!network.isOnline()) { app.downloadScheduler.defer(); break }
                        val lost = runQueueTransfer(app.repository.observeStatus(videoId), network.online) {
                            VideoTransferTask(this@DownloadQueueService, "foreground-queue") { info ->
                                notifications.notify(info.notificationId, info.notification)
                            }.run(videoId)
                        }
                        if (lost) { app.downloadScheduler.defer(); break }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                android.util.Log.w("SpixQueue", "Transfer paused; pending items retained", failure)
                app.downloadScheduler.defer()
            } finally {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(latestStartId)
            }
        }
        return START_REDELIVER_INTENT
    }

    // Android 15 limits dataSync foreground time; persist/retry rather than crash or lose the queue.
    override fun onTimeout(startId: Int, fgsType: Int) {
        pump?.cancel()
        app.processScope.launch { app.downloadScheduler.defer() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
