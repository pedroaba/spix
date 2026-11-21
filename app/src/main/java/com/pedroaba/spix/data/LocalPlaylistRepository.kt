package com.pedroaba.spix.data

import com.pedroaba.spix.domain.PlaylistMetadata
import androidx.paging.*
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class LocalPlaylist(val id: String, val name: String, val videoIds: List<String>, val coverUri: String? = null)

/** Membership is persisted once and queried in saved order, without loading every playlist. */
class LocalPlaylistRepository(private val repository: LocalSpixRepository) {
    private val db get() = repository.database
    private val dao get() = db.catalog()
    fun pages(): Flow<PagingData<PlaylistSummary>> = flow {
        repository.awaitReady()
        emitAll(Pager(catalogPagingConfig) { dao.playlistPages() }.flow)
    }.flowOn(Dispatchers.IO)
    val count: Flow<Int> = flow { repository.awaitReady(); emitAll(dao.playlistCount()) }.distinctUntilChanged().flowOn(Dispatchers.IO)
    fun observe(id: String): Flow<LocalPlaylist?> = flow {
        repository.awaitReady()
        emitAll(dao.playlistHeader(id).combine(dao.playlistIds(id)) { header, ids -> header?.let { LocalPlaylist(it.id, it.name, ids, it.coverUri) } })
    }.flowOn(Dispatchers.IO)
    fun artwork(id: String): Flow<String?> = flow {
        repository.awaitReady(); emitAll(dao.playlistArtwork(id))
    }.flowOn(Dispatchers.IO)
    suspend fun firstAvailableUri(id: String): String? = withContext(Dispatchers.IO) {
        repository.awaitReady(); dao.firstPlaylistUri(id)
    }
    suspend fun get(id: String): LocalPlaylist? = withContext(Dispatchers.IO) {
        repository.awaitReady(); dao.playlist(id)?.let { LocalPlaylist(it.id, it.name, dao.getPlaylistIds(id), it.coverUri) }
    }
    suspend fun save(id: String?, name: String, videoIds: List<String>, coverUri: String? = null, updateCover: Boolean = false, originalVideoIds: List<String>? = null) = withContext(Dispatchers.IO) {
        repository.awaitReady()
        require(name.trim().isNotEmpty()) { "Dê um nome à playlist." }
        var previousCover: String? = null
        db.withTransaction {
            val key = id ?: UUID.randomUUID().toString()
            val old = dao.playlist(key)
            val members = if (id != null && originalVideoIds != null) {
                check(old != null) { "Esta playlist não está mais disponível." }
                val original = originalVideoIds.toHashSet()
                val current = dao.getPlaylistIds(key)
                val currentSet = current.toHashSet()
                videoIds.filter { it !in original || it in currentSet } + current.filterNot(original::contains)
            } else videoIds
            previousCover = old?.coverUri
            val requested = if (updateCover) coverUri else old?.coverUri
            val cover = requested?.let { requestedUrl ->
                members.distinct().chunked(500).flatMap { dao.coverOptions(it) }
                    .firstOrNull { it.thumbnailUrl == requestedUrl }?.thumbnailUrl
            }
            dao.savePlaylist(PlaylistRecord(key, name.trim(), old?.position ?: System.currentTimeMillis(), cover))
            dao.clearMembers(key)
            members.distinct().mapIndexed { index, videoId -> PlaylistMemberRecord(key, videoId, index) }.chunked(100).forEach { dao.saveMembers(it) }
        }
        if (previousCover != coverUri) PlaylistCoverStore.delete(repository.context, previousCover)
    }

    suspend fun covers(ids: List<String>): List<PlaylistCoverOption> = withContext(Dispatchers.IO) {
        repository.awaitReady()
        val options = ids.distinct().chunked(500).flatMap { dao.coverOptions(it) }.associateBy { it.videoId }
        ids.distinct().mapNotNull { options[it] }.filter { !it.thumbnailUrl.isNullOrBlank() }
    }

    suspend fun importPlaylist(playlist: PlaylistMetadata) = withContext(Dispatchers.IO) {
        repository.awaitReady()
        db.withTransaction {
            val id = "youtube:${playlist.playlistId}"
            val old = dao.playlist(id)
            dao.savePlaylist(PlaylistRecord(id, playlist.title.trim(), old?.position ?: System.currentTimeMillis(), old?.coverUri))
            val sourceIds = playlist.videos.map { it.videoId }
            val ids = (sourceIds + dao.getPlaylistIds(id)).distinct()
            dao.clearMembers(id)
            ids.mapIndexed { index, videoId -> PlaylistMemberRecord(id, videoId, index) }.chunked(100).forEach { dao.saveMembers(it) }
            val sync = dao.playlistSync(id) ?: PlaylistSyncRecord(id, UUID.randomUUID().toString(), automatic = old == null)
            dao.savePlaylistSync(sync.copy(lastSyncedAt = System.currentTimeMillis()))
            recordSeen(id, sourceIds)
        }
    }

    fun observeSync(id: String): Flow<PlaylistSyncRecord?> = flow {
        repository.awaitReady()
        emitAll(dao.observePlaylistSync(id))
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    internal val automaticPlaylistIds: Flow<List<String>> = flow {
        repository.awaitReady()
        emitAll(dao.automaticPlaylistIds())
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    internal suspend fun syncState(id: String): PlaylistSyncRecord? {
        repository.awaitReady()
        return dao.playlistSync(id)
    }

    suspend fun setSyncAutomatic(id: String, enabled: Boolean) {
        repository.awaitReady()
        dao.setPlaylistSyncAutomatic(id, enabled)
    }

    /** A null result means the playlist was removed, replaced or opted out during the request. */
    internal suspend fun applySync(
        initial: PlaylistSyncRecord,
        source: PlaylistMetadata,
        automatic: Boolean,
    ): Int? = repository.withDownloadTransaction {
        val id = initial.playlistId
        val current = dao.playlistSync(id) ?: return@withDownloadTransaction null
        if (current.generation != initial.generation || (automatic && !current.automatic)) {
            return@withDownloadTransaction null
        }
        currentCoroutineContext().ensureActive()
        val videos = source.videos.distinctBy { it.videoId }
        val seen = dao.playlistSyncSeen(id).toHashSet()
        val unseen = videos.filter { it.videoId !in seen }
        val additions = if (current.lastSyncedAt == null) emptyList() else {
            val members = dao.getPlaylistIds(id).toHashSet()
            unseen.filter { it.videoId !in members }
        }
        if (additions.isNotEmpty()) {
            val start = dao.maxMemberPosition(id) + 1
            additions.mapIndexed { index, video -> PlaylistMemberRecord(id, video.videoId, start + index) }
                .chunked(100).forEach { dao.saveMembers(it) }
            val queued = repository.enqueueInTransaction(additions, retryInterrupted = false)
            repository.recordBatch(queued, requireNotNull(dao.playlist(id)).name)
        }
        recordSeen(id, unseen.map { it.videoId })
        dao.savePlaylistSync(current.copy(lastSyncedAt = System.currentTimeMillis()))
        additions.size
    }

    private suspend fun recordSeen(id: String, videoIds: List<String>) {
        videoIds.map { PlaylistSyncSeenRecord(id, it) }.chunked(100).forEach { dao.savePlaylistSyncSeen(it) }
    }
    fun memberships(videoId: String): Flow<List<String>> = flow {
        repository.awaitReady(); emitAll(dao.memberships(videoId))
    }.flowOn(Dispatchers.IO)

    suspend fun moveVideo(id: String, videoId: String, targetId: String, after: Boolean) = withContext(Dispatchers.IO) {
        repository.awaitReady()
        db.withTransaction {
            val original = dao.getPlaylistIds(id)
            check(videoId in original && targetId in original) { "A playlist mudou. Tente reorganizar novamente." }
            if (videoId == targetId) return@withTransaction
            val order = original.toMutableList()
            order.remove(videoId)
            order.add(order.indexOf(targetId) + if (after) 1 else 0, videoId)
            if (order != original) {
                dao.updateMembers(order.mapIndexed { position, memberId -> PlaylistMemberRecord(id, memberId, position) })
            }
        }
    }

    suspend fun setMembership(id: String, videoId: String, included: Boolean) = withContext(Dispatchers.IO) {
        repository.awaitReady()
        db.withTransaction {
            check(dao.playlist(id) != null) { "Esta playlist não está mais disponível." }
            if (included && !dao.hasMember(id, videoId)) {
                // Append after the last position, including after gaps left by removals.
                dao.saveMembers(listOf(PlaylistMemberRecord(id, videoId, dao.maxMemberPosition(id) + 1)))
            } else if (!included) { dao.clearMemberCover(id, videoId); dao.removeMember(id, videoId) }
        }
    }

    suspend fun addDownloadedSelection(id: String?, newName: String, videoIds: List<String>): Int = withContext(Dispatchers.IO) {
        repository.awaitReady()
        db.withTransaction {
            val uniqueIds = videoIds.distinct()
            val available = uniqueIds.chunked(500).flatMap { dao.savedMediaIds(it) }.toHashSet()
            check(available.isNotEmpty()) { "Os itens selecionados não estão mais disponíveis na biblioteca." }
            val playlistId = id ?: UUID.randomUUID().toString()
            if (id == null) {
                val name = newName.trim()
                require(name.isNotEmpty()) { "Dê um nome à playlist." }
                dao.savePlaylist(PlaylistRecord(playlistId, name, System.currentTimeMillis()))
            } else {
                check(dao.playlist(id) != null) { "Esta playlist não está mais disponível." }
            }
            val existing = dao.getPlaylistIds(playlistId).toHashSet()
            val additions = uniqueIds.filter { it in available && it !in existing }
            val nextPosition = dao.maxMemberPosition(playlistId) + 1
            additions.mapIndexed { index, videoId -> PlaylistMemberRecord(playlistId, videoId, nextPosition + index) }
                .chunked(100).forEach { dao.saveMembers(it) }
            additions.size
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        repository.awaitReady()
        val cover = dao.playlist(id)?.coverUri
        dao.deletePlaylist(id)
        PlaylistCoverStore.delete(repository.context, cover)
    }
}

internal fun encodePlaylists(items: List<LocalPlaylist>): String = JSONArray().apply {
    items.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("videos", JSONArray(it.videoIds))) }
}.toString()

internal fun decodePlaylists(json: String): List<LocalPlaylist> = runCatching {
    val items = JSONArray(json)
    List(items.length()) { index ->
        val item = items.getJSONObject(index)
        val videos = item.getJSONArray("videos")
        LocalPlaylist(item.getString("id"), item.getString("name"), List(videos.length()) { videos.getString(it) }.distinct())
    }
}.getOrDefault(emptyList())
