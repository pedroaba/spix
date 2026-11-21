package com.pedroaba.spix.data

import android.content.Context
import android.content.SharedPreferences
import androidx.paging.*
import androidx.room.withTransaction
import com.pedroaba.spix.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray

/** Room owns persistent rows; only the current transfer is retained as high-frequency state. */
class LocalSpixRepository(
    internal val context: Context,
    internal val database: SpixDatabase = SpixDatabase.open(context),
    private val importLegacy: Boolean = true,
    private val legacyPreferences: SharedPreferences? = null,
) : SpixRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dao = database.catalog()
    private val mutations = Mutex()
    private val state = MutableStateFlow<StorageState>(StorageState.Loading)
    override val storageState = state.asStateFlow()
    private val progress = MutableStateFlow<DownloadRequest?>(null)
    override val liveProgress = progress.asStateFlow()
    private var initialization = initialize()
    private var checkpointAt = 0L

    private fun initialize() = scope.async {
        try {
            if (importLegacy && !dao.imported("preferences-v1")) {
                val prefs = legacyPreferences ?: context.getSharedPreferences("spix", Context.MODE_PRIVATE)
                val records = decodeLegacyRequests(prefs.getString("requests", "[]") ?: "[]", context.packageName.endsWith(".benchmark"))
                check(records.map { it.videoId }.distinct().size == records.size) { "A biblioteca antiga contém IDs repetidos." }
                val playlistJson = context.getSharedPreferences("spix_playlists", Context.MODE_PRIVATE).getString("items", "[]") ?: "[]"
                val array = JSONArray(playlistJson)
                // Validate every record before beginning the transaction; tolerant UI decoding is not migration.
                repeat(array.length()) { i -> array.getJSONObject(i).apply { getString("id"); getString("name"); getJSONArray("videos") } }
                val playlists = decodePlaylists(playlistJson)
                check(playlists.map { it.id }.distinct().size == playlists.size) { "A biblioteca antiga contém playlists repetidas." }
                check(playlists.size == array.length()) { "Não foi possível ler as playlists antigas." }
                database.withTransaction {
                    check(dao.mediaCount() == 0) { "Migração incompleta: a base será preservada para recuperação." }
                    records.chunked(100).forEach { chunk -> dao.insertMedia(chunk.map { it.mediaRecord() }); dao.jobs(chunk.map { it.jobRecord() }) }
                    playlists.forEachIndexed { index, playlist ->
                        dao.savePlaylist(PlaylistRecord(playlist.id, playlist.name, index.toLong()))
                        if (playlist.id.startsWith("youtube:")) {
                            dao.savePlaylistSync(PlaylistSyncRecord(playlist.id, java.util.UUID.randomUUID().toString(), automatic = false))
                        }
                        playlist.videoIds.distinct().mapIndexed { position, id -> PlaylistMemberRecord(playlist.id, id, position) }.chunked(100).forEach { members ->
                            dao.saveMembers(members)
                        }
                    }
                    check(dao.mediaCount() == records.size)
                    dao.markImported(ImportRecord("preferences-v1"))
                }
            }
            state.value = StorageState.Ready
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            state.value = StorageState.Failed("Não foi possível abrir a biblioteca. Seus dados foram preservados. Tente novamente.")
            throw error
        }
    }
    override suspend fun awaitReady() { initialization.await() }
    override suspend fun retryInitialization() {
        if (initialization.isActive) return initialization.await()
        state.value = StorageState.Loading
        initialization = initialize()
        initialization.await()
    }
    private fun <T> afterReady(source: () -> Flow<T>): Flow<T> = flow { awaitReady(); emitAll(source()) }.flowOn(Dispatchers.IO)
    override val counts = afterReady { dao.counts() }.distinctUntilChanged()
    override val activeDownload: Flow<DownloadRequest?> = afterReady { dao.active() }.combine(progress) { stored, live ->
        if (stored == null) live else if (stored.media.videoId == live?.videoId) live else stored.request()
    }.distinctUntilChanged()
    override fun library(query: LibraryQuery): Flow<PagingData<Track>> = afterReady {
        Pager(catalogPagingConfig) { dao.libraryPages(librarySql(query)) }.flow.map { data -> data.map { it.track() } }
    }
    override fun libraryCount(query: LibraryQuery) = afterReady { dao.libraryCount(librarySql(query, count = true)) }.distinctUntilChanged()
    override fun downloads(completed: Boolean): Flow<PagingData<DownloadRequest>> = afterReady {
        Pager(catalogPagingConfig) { dao.downloadPages(downloadsSql(completed)) }.flow.map { data -> data.map { it.request() } }
    }
    override fun downloads(filter: DownloadFilter): Flow<PagingData<DownloadRequest>> = afterReady {
        Pager(catalogPagingConfig) { dao.downloadPages(downloadsSql(filter)) }.flow.map { data -> data.map { it.request() } }
    }
    override suspend fun get(videoId: String): DownloadRequest? = withContext(Dispatchers.IO) {
        awaitReady()
        progress.value?.takeIf { it.videoId == videoId } ?: dao.get(videoId)?.request()?.let { row ->
            dao.checkpoint(videoId)?.let { row.copy(progress = it.progress, transfer = it.transfer?.let(::parseTransfer)) } ?: row
        }
    }
    override suspend fun getByUri(uri: String): DownloadRequest? = withContext(Dispatchers.IO) { awaitReady(); dao.getByUri(uri)?.request() }
    override suspend fun playbackQueue(): List<DownloadRequest> = withContext(Dispatchers.IO) { awaitReady(); dao.playbackQueue().map { it.request() } }
    override suspend fun savedMediaUris(): List<String> {
        awaitReady()
        return dao.savedMediaUris()
    }
    override suspend fun enqueue(video: VideoMetadata): Boolean {
        awaitReady()
        val existing = get(video.videoId)
        if (existing == null) return enqueueBatch(listOf(video)).isNotEmpty()
        // Legacy callers may enrich a saved link without restarting it.
        updateDownload(video.videoId) { it.copy(metadata = video) }
        return false
    }
    internal suspend fun <T> withDownloadTransaction(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        awaitReady()
        mutations.withLock { database.withTransaction(block) }
    }
    override suspend fun enqueueBatch(videos: List<VideoMetadata>): List<String> =
        withDownloadTransaction { enqueueInTransaction(videos) }

    internal suspend fun enqueueInTransaction(videos: List<VideoMetadata>, retryInterrupted: Boolean = true): List<String> {
        val existing = videos.map { it.videoId }.distinct().chunked(500)
            .flatMap { dao.statuses(it) }.associate { it.videoId to DownloadStatus.valueOf(it.status) }
        val planned = planEnqueue(videos, existing).filter { retryInterrupted || it.videoId !in existing }
        val now = System.currentTimeMillis()
        val newRows = planned.filter { it.videoId !in existing }.mapIndexed { index, video ->
            DownloadRequest(video.videoId, video.url, now + index, video).mediaRecord()
        }
        newRows.chunked(100).forEach { dao.insertMedia(it) }
        planned.chunked(100).forEach { dao.jobs(it.map { video -> JobRecord(video.videoId, "Queued") }) }
        planned.filter { it.videoId in existing }.forEach { dao.clearCheckpoint(it.videoId) }
        return planned.map { it.videoId }
    }
    override suspend fun updateDownload(videoId: String, transform: (DownloadRequest) -> DownloadRequest) = withContext(Dispatchers.IO) {
        awaitReady()
        mutations.withLock {
            val old = progress.value?.takeIf { it.videoId == videoId } ?: dao.get(videoId)?.request() ?: return@withLock
            val updated = transform(old)
            val structural = old.status != updated.status || old.metadata != updated.metadata || old.localUri != updated.localUri || old.duration != updated.duration || old.error != updated.error
            if (structural) database.withTransaction {
                if (old.mediaRecord() != updated.mediaRecord()) dao.updateMedia(updated.mediaRecord())
                dao.jobs(listOf(updated.jobRecord()))
                dao.clearCheckpoint(videoId)
            }
            if (updated.status in runningStatuses) {
                progress.value = updated
                val now = android.os.SystemClock.elapsedRealtime()
                if (!structural && (now - checkpointAt >= 2000 || updated.progress == 100 || old.transfer?.kind != updated.transfer?.kind)) {
                    dao.checkpoint(ProgressRecord(videoId, updated.progress, updated.transfer?.json()))
                    checkpointAt = now
                }
            } else if (progress.value?.videoId == videoId) progress.value = null
        }
    }
    override suspend fun removeRequest(videoId: String) = withContext(Dispatchers.IO) {
        awaitReady(); mutations.withLock { database.withTransaction { dao.clearVideoCovers(videoId); dao.removeFromPlaylists(videoId); dao.remove(videoId) }; if (progress.value?.videoId == videoId) progress.value = null }
    }
    override suspend fun retryDownloads(filter: DownloadFilter, ids: List<String>?): List<String> = withContext(Dispatchers.IO) {
        awaitReady()
        val status = when (filter) {
            DownloadFilter.Failed -> DownloadStatus.Failed.name
            DownloadFilter.Cancelled -> DownloadStatus.Cancelled.name
            else -> return@withContext emptyList()
        }
        mutations.withLock {
            database.withTransaction {
                val eligible = if (ids == null) dao.retryIds(status)
                    else ids.distinct().chunked(500).flatMap { dao.selectedRetryIds(status, it) }
                eligible.chunked(500).forEach {
                    dao.requeue(status, it)
                    dao.clearRetryCheckpoints(it)
                }
                eligible
            }
        }
    }
    override suspend fun batch(videoId: String): DownloadBatchRecord? = withContext(Dispatchers.IO) { awaitReady(); dao.batch(videoId) }
    override suspend fun recordBatch(ids: List<String>, title: String?) = withContext(Dispatchers.IO) {
        awaitReady()
        val ordered = ids.distinct()
        if (ordered.isNotEmpty()) database.withTransaction {
            if (title == null) ordered.chunked(500).forEach { dao.clearBatch(it) }
            else {
                val groupId = java.util.UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                ordered.mapIndexed { index, id -> DownloadBatchRecord(id, groupId, title, index + 1, ordered.size, now) }
                    .chunked(100).forEach { dao.saveBatch(it) }
            }
        }
    }
    override suspend fun cancelActiveDownloads() = withContext(Dispatchers.IO) {
        awaitReady()
        mutations.withLock {
            database.withTransaction { dao.cancelActiveDownloads(); dao.clearCancelledCheckpoints() }
            progress.value = null
        }
    }
    override suspend fun nextQueued(): String? { awaitReady(); return dao.nextQueued() }
    override suspend fun hasQueued(): Boolean { awaitReady(); return dao.hasQueued() }
    override suspend fun recoverRunning() { awaitReady(); mutations.withLock { progress.value = null; dao.recoverRunning() } }
    override fun observeStatus(id: String): Flow<DownloadStatus?> = afterReady { dao.observeStatus(id) }.map { status -> status?.let(DownloadStatus::valueOf) }.distinctUntilChanged()
    fun close() { scope.cancel() }
    companion object { private val runningStatuses = setOf(DownloadStatus.Preparing, DownloadStatus.Downloading, DownloadStatus.Processing, DownloadStatus.Saving) }
}
