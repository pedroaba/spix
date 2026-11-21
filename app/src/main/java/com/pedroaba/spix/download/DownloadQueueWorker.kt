package com.pedroaba.spix.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pedroaba.spix.SpixApplication
import com.pedroaba.spix.domain.DownloadStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock

class DownloadQueueWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private val app = context.applicationContext as SpixApplication
    private val network = DownloadNetwork(context)
    override suspend fun doWork(): Result {
        app.downloadScheduler.awaitStartup()
        return app.downloadScheduler.consumerGate.withLock {
            try {
                repeat(10) {
                    val videoId = app.repository.nextQueued() ?: return@withLock Result.success()
                    if (!network.isOnline()) return@withLock Result.retry()
                    val lostConnection = runQueueTransfer(app.repository.observeStatus(videoId), network.online) {
                        VideoTransferTask(applicationContext, id.toString(), ::setForeground).run(videoId)
                    }
                    if (lostConnection || !network.isOnline()) return@withLock Result.retry()
                }
                if (app.repository.hasQueued()) app.downloadScheduler.defer()
                Result.success()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                android.util.Log.w("SpixQueue", "Queue will retry without dropping pending items", failure)
                Result.retry()
            }
        }
    }
}

/** Stop the current transfer on cancellation or loss of validated internet, not the rest of the list. */
internal suspend fun runQueueTransfer(
    status: kotlinx.coroutines.flow.Flow<DownloadStatus?>,
    online: kotlinx.coroutines.flow.Flow<Boolean>,
    operation: suspend () -> Unit,
): Boolean = supervisorScope {
    var lostConnection = false
    val transfer = async(start = CoroutineStart.LAZY) { operation() }
    val watcher = launch {
        kotlinx.coroutines.flow.combine(status, online) { state, connected -> state to connected }
            .first { (state, connected) -> state == null || state == DownloadStatus.Cancelled || !connected }
            .let { (_, connected) -> lostConnection = !connected; transfer.cancel() }
    }
    transfer.start()
    try { transfer.await() }
    catch (cancelled: CancellationException) { currentCoroutineContext().ensureActive() }
    finally { watcher.cancelAndJoin() }
    lostConnection
}

class CancelDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val all = intent.action == "com.pedroaba.spix.CANCEL_ALL"
        val id = intent.getStringExtra("videoId")
        if (!all && id == null) return
        val pending = goAsync()
        val app = context.applicationContext as SpixApplication
        app.processScope.launch {
            try {
                if (all) app.downloadScheduler.cancelAll() else app.downloadScheduler.cancel(requireNotNull(id))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { android.util.Log.w("SpixQueue", "Cancellation could not complete", failure) }
            finally { pending.finish() }
        }
    }
}
