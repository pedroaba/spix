package com.pedroaba.spix.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedroaba.spix.data.SpixRepository
import com.pedroaba.spix.domain.YouTubePlaylistLink
import com.pedroaba.spix.data.PlaylistMetadataSource
import com.pedroaba.spix.domain.YouTubeLink
import com.pedroaba.spix.domain.VideoPreviewState
import com.pedroaba.spix.domain.DownloadStatus
import com.pedroaba.spix.data.VideoMetadataSource
import com.pedroaba.spix.data.MetadataHttpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SpixEvent {
    data class Message(val text: String) : SpixEvent
    data class LinkQueued(val message: String) : SpixEvent
    data class Removed(val videoId: String) : SpixEvent
    data class DeleteConsent(val sender: android.content.IntentSender) : SpixEvent
}

class SpixViewModel(private val repository: SpixRepository, private val saved: SavedStateHandle, private val metadata: VideoMetadataSource, private val downloads: com.pedroaba.spix.download.DownloadScheduler, private val playlists: PlaylistMetadataSource,
    private val savePlaylist: suspend (com.pedroaba.spix.domain.PlaylistMetadata) -> Unit = {},
    private val deleteFile: suspend (String) -> Unit = { error("O armazenamento não está disponível.") }) : ViewModel() {
    val link = saved.getStateFlow("link", "")
    val query = saved.getStateFlow("query", "")
    val playlistName = saved.getStateFlow("playlistName", "")
    fun updatePlaylistName(value: String) { saved["playlistName"] = value }
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val mutableRetryBusy = MutableStateFlow(false)
    val retryBusy = mutableRetryBusy.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutablePreview = MutableStateFlow<VideoPreviewState>(VideoPreviewState.Idle)
    val preview = mutablePreview.asStateFlow()
    private var lookupJob: Job? = null
    private val eventChannel = Channel<SpixEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private fun inputKey(value: String): String? = YouTubePlaylistLink.parse(value)?.let { "playlist:${it.playlistId}" }
        ?: YouTubeLink.parse(value)?.let { "video:${it.videoId}" }

    init { if (inputKey(link.value) != null) lookup() }

    fun updateLink(value: String) {
        val previous = inputKey(link.value)
        saved["link"] = value
        mutableError.value = null
        if (previous != null && previous == inputKey(value)) return
        lookupJob?.cancel()
        mutablePreview.value = VideoPreviewState.Idle
        if (inputKey(value) != null) lookup(debounceMillis = 600)
    }

    fun searchVideo() {
        if (mutableBusy.value || !validateLink()) return
        lookup()
    }

    private fun lookup(debounceMillis: Long = 0) {
        val input = link.value
        val key = inputKey(input) ?: return
        lookupJob?.cancel()
        mutablePreview.value = VideoPreviewState.Loading(key)
        lookupJob = viewModelScope.launch {
            try {
                delay(debounceMillis)
                val playlist = YouTubePlaylistLink.parse(input)
                val result = if (playlist != null) VideoPreviewState.PlaylistReady(playlists.fetchPlaylist(playlist))
                    else VideoPreviewState.Ready(metadata.fetch(requireNotNull(YouTubeLink.parse(input))))
                ensureActive()
                if (inputKey(link.value) == key) {
                    if (result is VideoPreviewState.PlaylistReady && saved.get<String>("playlistNameKey") != key) {
                        saved["playlistNameKey"] = key
                        saved["playlistName"] = result.playlist.title
                    }
                    mutablePreview.value = result
                }
            } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                ensureActive()
                if (inputKey(link.value) == key) mutablePreview.value = VideoPreviewState.Failed(key, "O YouTube demorou para responder. Tente novamente.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                ensureActive()
                if (inputKey(link.value) == key) {
                    val message = when {
                        error is UnknownHostException -> "Não foi possível conectar. Confira sua internet e tente novamente."
                        error is SocketTimeoutException -> "O YouTube demorou para responder. Tente novamente."
                        YouTubePlaylistLink.parse(input) != null -> "Não foi possível carregar a playlist. Confira se ela é pública ou não listada e contém vídeos disponíveis. Tente novamente."
                        error is MetadataHttpException -> when (error.status) {
                            400, 401, 403, 404 -> "Não foi possível acessar os dados. Confira se o link aponta para um vídeo público e disponível."
                            429 -> "Muitas consultas no momento. Aguarde um pouco e tente novamente."
                            else -> "O YouTube não respondeu como esperado. Tente novamente."
                        }
                        else -> "Não foi possível buscar os dados. Confira sua conexão e tente novamente."
                    }
                    mutablePreview.value = VideoPreviewState.Failed(key, message)
                }
            }
        }
    }
    fun updateQuery(value: String) { saved["query"] = value }
    fun validateLink(): Boolean {
        val valid = inputKey(link.value) != null
        mutableError.value = if (valid) null else "Use um link válido de vídeo ou playlist do YouTube."
        return valid
    }

    fun enqueue() {
        if (mutableBusy.value || !validateLink()) return
        val key = inputKey(link.value) ?: return
        val playlist = (preview.value as? VideoPreviewState.PlaylistReady)?.playlist?.copy(title = playlistName.value.trim())
        if (playlist != null && playlist.title.isBlank()) {
            viewModelScope.launch { eventChannel.send(SpixEvent.Message("Dê um nome à playlist antes de confirmar.")) }
            return
        }
        val video = (preview.value as? VideoPreviewState.Ready)?.video
        val videos = when {
            playlist != null && key == "playlist:${playlist.playlistId}" -> playlist.videos
            video != null && key == "video:${video.videoId}" -> listOf(video)
            else -> { searchVideo(); return }
        }
        mutableBusy.value = true
        viewModelScope.launch {
            var added = 0
            var failed = 0
            var enqueuedIds = emptyList<String>()
            try {
                try {
                    val ids = repository.enqueueBatch(videos)
                    enqueuedIds = ids
                    if (playlist != null) savePlaylist(playlist)
                    val failures = if (ids.isEmpty()) emptySet() else if (playlist != null) downloads.startPlaylist(ids, playlist.title) else downloads.startBatch(ids)
                    for (id in failures) repository.updateDownload(id) { it.copy(status = DownloadStatus.Failed, error = "Não foi possível iniciar. Tente novamente.") }
                    added = ids.size - failures.size
                    failed = failures.size
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    for (id in enqueuedIds) repository.updateDownload(id) { it.copy(status = DownloadStatus.Failed, error = "Não foi possível iniciar. Tente novamente.") }
                    failed = if (enqueuedIds.isEmpty()) videos.size else enqueuedIds.size
                }
                val message = when {
                    failed > 0 -> "Downloads adicionados: $added. Falhas ao adicionar: $failed. Tente novamente."
                    added == 0 -> if (playlist != null) "Playlist criada. Os vídeos já estão nos seus downloads." else "Este vídeo já está nos seus downloads."
                    playlist == null -> "Download adicionado à fila."
                    added == 1 -> "1 vídeo da playlist adicionado à fila."
                    else -> "Playlist criada. $added vídeos adicionados à fila."
                }
                if (added > 0 || (playlist != null && failed == 0)) {
                    if (failed == 0 && inputKey(link.value) == key) updateLink("")
                    eventChannel.send(SpixEvent.LinkQueued(message))
                } else eventChannel.send(SpixEvent.Message(message))
            } finally { mutableBusy.value = false }
        }
    }

    fun storagePermissionDenied() {
        viewModelScope.launch { eventChannel.send(SpixEvent.Message("Permita o acesso ao armazenamento para salvar em Downloads/Spix.")) }
    }

    fun retry(videoId: String) {
        if (mutableRetryBusy.value) return
        viewModelScope.launch {
            try {
                repository.updateDownload(videoId) { it.copy(status = DownloadStatus.Queued, progress = 0, error = null, transfer = null) }
                downloads.start(videoId)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                repository.updateDownload(videoId) { it.copy(status = DownloadStatus.Failed, error = "Não foi possível iniciar. Tente novamente.") }
                eventChannel.send(SpixEvent.Message("Não foi possível iniciar o download."))
            }
        }
    }

    fun retryDownloads(filter: com.pedroaba.spix.data.DownloadFilter, selectedIds: List<String>?) {
        if (mutableRetryBusy.value || filter !in listOf(com.pedroaba.spix.data.DownloadFilter.Failed, com.pedroaba.spix.data.DownloadFilter.Cancelled)) return
        mutableRetryBusy.value = true
        viewModelScope.launch {
            var ids = emptyList<String>()
            try {
                ids = repository.retryDownloads(filter, selectedIds)
                if (ids.isEmpty()) {
                    eventChannel.send(SpixEvent.Message("Nenhum item deste filtro precisa de nova tentativa."))
                    return@launch
                }
                val failed = if (ids.size == 1) downloads.startBatch(ids) else downloads.startPlaylist(ids, "Novas tentativas")
                for (id in failed) repository.updateDownload(id) {
                    if (it.status == DownloadStatus.Queued) it.copy(status = DownloadStatus.Failed, error = "Não foi possível iniciar. Tente novamente.") else it
                }
                val count = ids.size - failed.size
                val message = if (failed.isEmpty()) "$count ${if (count == 1) "download adicionado" else "downloads adicionados"} novamente à fila."
                    else "$count adicionados à fila; ${failed.size} não puderam ser iniciados."
                if (count > 0) eventChannel.send(SpixEvent.LinkQueued(message))
                else eventChannel.send(SpixEvent.Message(message))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                for (id in ids) repository.updateDownload(id) {
                    if (it.status == DownloadStatus.Queued) it.copy(status = DownloadStatus.Failed, error = "Não foi possível iniciar. Tente novamente.") else it
                }
                eventChannel.send(SpixEvent.Message("Não foi possível reenviar os downloads. Tente novamente."))
            } finally { mutableRetryBusy.value = false }
        }
    }

    fun cancel(videoId: String) {
        viewModelScope.launch {
            try {
                downloads.cancel(videoId)
                repository.updateDownload(videoId) { if (it.status == DownloadStatus.Completed) it else it.copy(status = DownloadStatus.Cancelled) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { eventChannel.send(SpixEvent.Message("Não foi possível cancelar o download.")) }
        }
    }

    fun cancelAllDownloads() {
        viewModelScope.launch {
            try { downloads.cancelAll(); eventChannel.send(SpixEvent.Message("Todos os downloads pendentes foram cancelados.")) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { eventChannel.send(SpixEvent.Message("Não foi possível cancelar toda a fila. Tente novamente.")) }
        }
    }
    private val removals = mutableSetOf<String>()
    private val removalMutex = Mutex()
    private val mutableRemovalBusy = MutableStateFlow(false)
    val removalBusy = mutableRemovalBusy.asStateFlow()
    private var pendingDeletionResult: CompletableDeferred<Boolean>? = null

    fun remove(videoId: String) = removeMany(listOf(videoId))

    fun removeMany(videoIds: List<String>) {
        val ids = videoIds.filter(removals::add)
        if (ids.isEmpty()) return
        mutableRemovalBusy.value = true
        viewModelScope.launch {
            try {
                removalMutex.withLock {
                    var deleted = 0
                    var declined = false
                    for (id in ids) {
                        try {
                            downloads.cancel(id)
                            val uri = repository.get(id)?.localUri
                            if (uri != null && !deleteWithConsent(id, uri)) {
                                declined = true
                                break
                            }
                            repository.removeRequest(id)
                            eventChannel.send(SpixEvent.Removed(id))
                            deleted++
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // Keep the catalog entry when its file could not be removed.
                        }
                    }
                    val message = when {
                        ids.size == 1 && deleted == 1 -> "Item excluído do celular."
                        ids.size == 1 && declined -> "Exclusão cancelada. O item foi mantido."
                        ids.size == 1 -> "Não foi possível excluir o arquivo. O item foi mantido; confira o acesso à pasta e tente novamente."
                        deleted == ids.size -> "$deleted itens excluídos do celular."
                        declined -> "Exclusão cancelada. $deleted de ${ids.size} itens excluídos."
                        else -> "$deleted de ${ids.size} itens excluídos. Os demais foram mantidos."
                    }
                    eventChannel.send(SpixEvent.Message(message))
                }
            } finally {
                removals.removeAll(ids.toSet())
                mutableRemovalBusy.value = removals.isNotEmpty()
            }
        }
    }

    private suspend fun deleteWithConsent(id: String, uri: String): Boolean {
        try {
            deleteFile(uri)
        } catch (consent: com.pedroaba.spix.data.FileDeletionConsent) {
            val result = CompletableDeferred<Boolean>()
            pendingDeletionResult = result
            saved["pendingDeletion"] = id
            val approved = try {
                eventChannel.send(SpixEvent.DeleteConsent(consent.sender))
                result.await()
            } finally {
                pendingDeletionResult = null
                saved["pendingDeletion"] = null
            }
            if (!approved) return false
            deleteFile(uri)
        }
        return true
    }

    fun deletionConsentResult(approved: Boolean) {
        pendingDeletionResult?.let {
            it.complete(approved)
            return
        }
        val id = saved.get<String>("pendingDeletion") ?: return
        saved["pendingDeletion"] = null
        if (approved) remove(id)
        else viewModelScope.launch { eventChannel.send(SpixEvent.Message("Exclusão cancelada. O item foi mantido.")) }
    }
}
