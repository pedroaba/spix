package com.pedroaba.spix.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.pedroaba.spix.SpixApplication
import com.pedroaba.spix.domain.DownloadStatus
import com.pedroaba.spix.domain.PlaylistMetadata
import com.pedroaba.spix.domain.VideoMetadata
import com.pedroaba.spix.download.PlaylistSync
import com.pedroaba.spix.download.PlaylistSyncResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class PlaylistSyncTest {
    private val id = "youtube:PLsource"

    @Test fun appendsOnceAndPreservesLocalEditsAndDownloadStates() = runBlocking {
        withRepository { repo, playlists ->
            val cover = video("original").thumbnailUrl
            repo.enqueueBatch(listOf(video("original"), video("ready"), video("running"), video("cancelled")))
            repo.updateDownload("original") { it.copy(status = DownloadStatus.Completed, localUri = "content://original") }
            repo.updateDownload("ready") { it.copy(status = DownloadStatus.Completed, localUri = "content://ready") }
            repo.updateDownload("running") { it.copy(status = DownloadStatus.Downloading) }
            repo.updateDownload("cancelled") { it.copy(status = DownloadStatus.Cancelled) }
            playlists.importPlaylist(source("removed", "original", "other"))
            playlists.save(id, "Nome local", listOf("other", "original", "local"), cover, updateCover = true)
            val editorSelection = playlists.get(id)!!.videoIds
            var remote = source("original", "removed", "ready", "newB", "running", "newA", "cancelled", "newB")
            val sync = PlaylistSync(playlists, { remote }, { true }, {})

            assertEquals(listOf(id), playlists.automaticPlaylistIds.first())
            val manual = async { sync.sync(id) }
            val periodic = async { sync.sync(id, automatic = true) }
            val results = listOf(manual.await(), periodic.await())
            assertEquals(setOf(PlaylistSyncResult.Synced(5), PlaylistSyncResult.Synced(0)), results.toSet())
            playlists.save(id, "Nome editado", editorSelection - "local", cover, updateCover = true, originalVideoIds = editorSelection)
            remote = source("removed", "newA", "ready", "newB")
            assertEquals(PlaylistSyncResult.Synced(0), sync.sync(id))
            val saved = playlists.get(id)!!
            assertEquals("Nome editado", saved.name)
            assertEquals(cover, saved.coverUri)
            assertEquals(listOf("other", "original", "ready", "newB", "running", "newA", "cancelled"), saved.videoIds)
            assertNull(repo.get("removed"))
            assertEquals("content://ready", repo.get("ready")?.localUri)
            assertEquals(DownloadStatus.Downloading, repo.get("running")?.status)
            assertEquals(DownloadStatus.Cancelled, repo.get("cancelled")?.status)
            assertEquals(DownloadStatus.Queued, repo.get("newB")?.status)
            assertEquals(1, repo.batch("newB")?.itemIndex)
            assertEquals(2, repo.batch("newA")?.itemIndex)
        }
    }

    @Test fun baselineOfflineOptOutAndQueueRecovery() = runBlocking {
        withRepository { repo, playlists ->
            playlists.save(id, "Importada antes da atualização", listOf("original"))
            repo.database.catalog().savePlaylistSync(PlaylistSyncRecord(id, "legacy", automatic = false))
            var online = false
            var remote = source("original", "removed")
            var sourceFailure: Exception? = null
            var failWake = false
            var wakes = 0
            val sync = PlaylistSync(playlists, { sourceFailure?.let { throw it }; remote }, { online }, {
                if (failWake) error("Interrompido antes de acionar a fila")
                wakes++
            })
            assertEquals(PlaylistSyncResult.Retry, sync.sync(id))
            assertNull(playlists.syncState(id)?.lastSyncedAt)
            online = true
            assertEquals(PlaylistSyncResult.Skipped, sync.sync(id, automatic = true))
            assertEquals(PlaylistSyncResult.Synced(0), sync.sync(id))
            val lastSuccess = playlists.syncState(id)?.lastSyncedAt
            assertNotNull(lastSuccess)
            sourceFailure = PlaylistUnavailableException("Privada")
            assertEquals(PlaylistSyncResult.Inaccessible, sync.sync(id))
            assertEquals(lastSuccess, playlists.syncState(id)?.lastSyncedAt)
            sourceFailure = null
            remote = source("original", "removed", "new")
            failWake = true
            assertEquals(PlaylistSyncResult.Retry, sync.sync(id))
            assertEquals(DownloadStatus.Queued, repo.get("new")?.status)
            failWake = false
            assertEquals(PlaylistSyncResult.Synced(0), sync.sync(id))
            assertEquals(2, wakes)
            assertEquals(listOf("original", "new"), playlists.get(id)?.videoIds)
        }
    }

    @Test fun discardsInFlightResponsesAfterOptOutAndRecreation() = runBlocking {
        withRepository { repo, playlists ->
            suspend fun blockedSync(mutate: suspend () -> Unit) {
                val fetching = CompletableDeferred<Unit>()
                val respond = CompletableDeferred<PlaylistMetadata>()
                val sync = PlaylistSync(playlists, { fetching.complete(Unit); respond.await() }, { true }, {})
                val pending = async { sync.sync(id, automatic = true) }
                fetching.await()
                mutate()
                respond.complete(source("stale"))
                assertEquals(PlaylistSyncResult.Skipped, pending.await())
                assertNull(repo.get("stale"))
            }
            playlists.importPlaylist(source("original"))
            blockedSync { playlists.setSyncAutomatic(id, false) }
            assertEquals(emptyList<String>(), playlists.automaticPlaylistIds.first())
            playlists.setSyncAutomatic(id, true)
            blockedSync {
                playlists.delete(id)
                playlists.importPlaylist(source("replacement"))
            }
            assertEquals(listOf("replacement"), playlists.get(id)?.videoIds)
        }
    }

    @Test fun automaticWorkFollowsThePersistedPreferenceAndDeletion() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as SpixApplication
        val sourceId = "PLsyncWork${java.util.UUID.randomUUID()}"
        val playlistId = "youtube:$sourceId"
        val workName = "spix-playlist-sync:$playlistId"
        val work = WorkManager.getInstance(context)
        suspend fun activeWork(expected: Int): List<WorkInfo> = withTimeout(15_000) {
            var current: List<WorkInfo>
            do {
                current = work.getWorkInfosForUniqueWork(workName).get().filterNot { it.state.isFinished }
                if (current.size != expected) delay(100)
            } while (current.size != expected)
            current
        }
        try {
            app.localPlaylists.importPlaylist(source("original").copy(playlistId = sourceId))
            val first = activeWork(1).single()
            app.localPlaylists.setSyncAutomatic(playlistId, false)
            activeWork(0)
            app.localPlaylists.setSyncAutomatic(playlistId, true)
            assertNotEquals(first.id, activeWork(1).single().id)
            app.localPlaylists.delete(playlistId)
            activeWork(0)
        } finally {
            app.localPlaylists.delete(playlistId)
            work.cancelUniqueWork(workName).result.get()
        }
    }

    private suspend fun withRepository(block: suspend (LocalSpixRepository, LocalPlaylistRepository) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, SpixDatabase::class.java).build()
        val repository = LocalSpixRepository(context, db, importLegacy = false)
        try {
            repository.awaitReady()
            block(repository, LocalPlaylistRepository(repository))
        } finally {
            repository.close()
            db.close()
        }
    }

    private fun source(vararg ids: String) = PlaylistMetadata("PLsource", "YouTube", ids.map(::video))
    private fun video(id: String) = VideoMetadata(id, "https://example.com/$id", id, "Canal", "https://example.com/$id.jpg")
}
