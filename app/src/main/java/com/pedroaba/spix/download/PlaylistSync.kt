package com.pedroaba.spix.download

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pedroaba.spix.SpixApplication
import com.pedroaba.spix.data.LocalPlaylistRepository
import com.pedroaba.spix.data.PlaylistMetadataSource
import com.pedroaba.spix.data.PlaylistUnavailableException
import com.pedroaba.spix.domain.YouTubePlaylistLink
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface PlaylistSyncResult {
    data class Synced(val added: Int) : PlaylistSyncResult
    data object Inaccessible : PlaylistSyncResult
    data object Retry : PlaylistSyncResult
    data object Skipped : PlaylistSyncResult
}

class PlaylistSync(
    private val playlists: LocalPlaylistRepository,
    private val source: PlaylistMetadataSource,
    private val isOnline: () -> Boolean,
    private val wakeDownloads: suspend () -> Unit,
    private val prepareDownloads: suspend () -> Unit = {},
) {
    // The extractor is already serialized. Keep the read, fetch and commit together too.
    private val syncLock = Mutex()

    suspend fun sync(playlistId: String, automatic: Boolean = false): PlaylistSyncResult = syncLock.withLock {
        try {
            val state = playlists.syncState(playlistId) ?: return@withLock PlaylistSyncResult.Skipped
            if (automatic && !state.automatic) return@withLock PlaylistSyncResult.Skipped
            val link = YouTubePlaylistLink.fromId(playlistId.removePrefix("youtube:"))
                ?: return@withLock PlaylistSyncResult.Inaccessible
            if (!isOnline()) return@withLock PlaylistSyncResult.Retry
            val remote = source.fetchForSync(link)
            prepareDownloads()
            val added = playlists.applySync(state, remote, automatic) ?: return@withLock PlaylistSyncResult.Skipped
            wakeDownloads()
            PlaylistSyncResult.Synced(added)
        } catch (_: PlaylistUnavailableException) {
            PlaylistSyncResult.Inaccessible
        } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            PlaylistSyncResult.Retry
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w("SpixPlaylistSync", "Playlist sync can be retried", failure)
            PlaylistSyncResult.Retry
        }
    }
}

internal class PlaylistSyncSchedule(context: Context, private val playlists: LocalPlaylistRepository) {
    private val manager = WorkManager.getInstance(context)

    suspend fun observePreferences() {
        playlists.automaticPlaylistIds.retryWhen { error, _ ->
            Log.w("SpixPlaylistSync", "Playlist schedules will be reconciled again", error)
            delay(30_000)
            true
        }.collectLatest { enabled -> reconcileWithRetry(enabled) }
    }

    private suspend fun reconcileWithRetry(enabled: List<String>) {
        // Retry the reconciliation without waiting for another database change.
        while (true) {
            try {
                reconcile(enabled)
                return
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w("SpixPlaylistSync", "Playlist schedules will be reconciled again", failure)
                delay(30_000)
            }
        }
    }

    private suspend fun reconcile(enabled: List<String>) {
        val desired = enabled.toSet()
        val active = manager.getWorkInfosByTag(TAG).get().filterNot { it.state.isFinished }
        for (work in active) {
            currentCoroutineContext().ensureActive()
            val playlistId = work.tags.firstOrNull { it.startsWith(ID_TAG) }?.removePrefix(ID_TAG)
            if (playlistId !in desired) manager.cancelWorkById(work.id).result.get()
        }
        for (playlistId in enabled) {
            currentCoroutineContext().ensureActive()
            val work = PeriodicWorkRequestBuilder<PlaylistSyncWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(PLAYLIST_ID to playlistId))
                .addTag(TAG)
                .addTag(ID_TAG + playlistId)
                .build()
            manager.enqueueUniquePeriodicWork(ID_TAG + playlistId, ExistingPeriodicWorkPolicy.KEEP, work).result.get()
        }
    }

    companion object {
        private const val TAG = "spix-playlist-sync"
        private const val ID_TAG = "$TAG:"
        const val PLAYLIST_ID = "playlistId"
    }
}

class PlaylistSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val playlistId = inputData.getString(PlaylistSyncSchedule.PLAYLIST_ID) ?: return Result.failure()
        val app = applicationContext as SpixApplication
        return when (app.playlistSync.sync(playlistId, automatic = true)) {
            PlaylistSyncResult.Retry -> Result.retry()
            else -> Result.success()
        }
    }
}
