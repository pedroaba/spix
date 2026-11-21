package com.pedroaba.spix.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedroaba.spix.data.SpixRepository
import com.pedroaba.spix.domain.YouTubeLink
import com.pedroaba.spix.domain.VideoPreviewState
import com.pedroaba.spix.domain.DownloadStatus
import com.pedroaba.spix.data.VideoMetadataSource
import com.pedroaba.spix.data.MetadataHttpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface SpixEvent {
    data class Message(val text: String) : SpixEvent
    data object LinkQueued : SpixEvent
}

class SpixViewModel(private val repository: SpixRepository, private val saved: SavedStateHandle, private val metadata: VideoMetadataSource, private val downloads: com.pedroaba.spix.download.DownloadScheduler) : ViewModel() {
    val link = saved.getStateFlow("link", "")
    val query = saved.getStateFlow("query", "")
    val requests = repository.requests
    val tracks = repository.tracks
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutablePreview = MutableStateFlow<VideoPreviewState>(VideoPreviewState.Idle)
    val preview = mutablePreview.asStateFlow()
    private var lookupJob: Job? = null
    private val eventChannel = Channel<SpixEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init { YouTubeLink.parse(link.value)?.let { lookup(it) } }

    fun updateLink(value: String) {
        saved["link"] = value
        mutableError.value = null
        val parsed = YouTubeLink.parse(value)
        val currentId = when (val state = preview.value) {
            is VideoPreviewState.Ready -> state.video.videoId
            is VideoPreviewState.Loading -> state.videoId
            is VideoPreviewState.Failed -> state.videoId
            VideoPreviewState.Idle -> null
        }
        if (parsed != null && parsed.videoId == currentId) return
        lookupJob?.cancel()
        mutablePreview.value = VideoPreviewState.Idle
        if (parsed != null) lookup(parsed, debounceMillis = 600)
    }

    fun searchVideo() {
        if (mutableBusy.value || !validateLink()) return
        YouTubeLink.parse(link.value)?.let { lookup(it) }
    }

    private fun lookup(parsed: YouTubeLink, debounceMillis: Long = 0) {
        lookupJob?.cancel()
        mutablePreview.value = VideoPreviewState.Loading(parsed.videoId)
        lookupJob = viewModelScope.launch {
            try {
                delay(debounceMillis)
                val video = metadata.fetch(parsed)
                ensureActive()
                if (YouTubeLink.parse(link.value)?.videoId == parsed.videoId) mutablePreview.value = VideoPreviewState.Ready(video)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                ensureActive()
                if (YouTubeLink.parse(link.value)?.videoId == parsed.videoId) {
                    val message = when (error) {
                        is UnknownHostException -> "Não foi possível conectar. Confira sua internet e tente novamente."
                        is SocketTimeoutException -> "O YouTube demorou para responder. Tente novamente."
                        is MetadataHttpException -> when (error.status) {
                            400, 401, 403, 404 -> "Não foi possível acessar os dados. Confira se o link aponta para um vídeo público e disponível."
                            429 -> "Muitas consultas no momento. Aguarde um pouco e tente novamente."
                            else -> "O YouTube não respondeu como esperado. Tente novamente."
                        }
                        else -> "Não foi possível buscar os dados. Confira sua conexão e tente novamente."
                    }
                    mutablePreview.value = VideoPreviewState.Failed(parsed.videoId, message)
                }
            }
        }
    }
    fun updateQuery(value: String) { saved["query"] = value }
    fun validateLink(): Boolean {
        val valid = YouTubeLink.parse(link.value) != null
        mutableError.value = if (valid) null else "Use um link válido de um vídeo do YouTube."
        return valid
    }

    fun enqueue() {
        if (mutableBusy.value || !validateLink()) return
        val parsed = YouTubeLink.parse(link.value) ?: return
        val video = (preview.value as? VideoPreviewState.Ready)?.video
        if (video == null || video.videoId != parsed.videoId) { searchVideo(); return }
        mutableBusy.value = true
        viewModelScope.launch {
            try {
                val added = repository.enqueue(video)
                val existing = requests.value.firstOrNull { it.videoId == video.videoId }
                if (existing?.status != DownloadStatus.Completed) {
                    try { downloads.start(video.videoId) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        repository.updateDownload(video.videoId) { it.copy(status = DownloadStatus.Failed, error = "Não foi possível iniciar. Tente novamente.") }
                        throw error
                    }
                }
                if (added) {
                    if (YouTubeLink.parse(link.value)?.videoId == video.videoId) updateLink("")
                    eventChannel.send(SpixEvent.LinkQueued)
                } else eventChannel.send(SpixEvent.Message("Este vídeo já está nos seus downloads."))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { eventChannel.send(SpixEvent.Message("Não foi possível salvar o link. Tente novamente.")) }
            finally { mutableBusy.value = false }
        }
    }

    fun storagePermissionDenied() {
        viewModelScope.launch { eventChannel.send(SpixEvent.Message("Permita o acesso ao armazenamento para salvar em Downloads/Spix.")) }
    }

    fun retry(videoId: String) {
        viewModelScope.launch {
            try {
                repository.updateDownload(videoId) { it.copy(status = DownloadStatus.Queued, progress = 0, error = null) }
                downloads.start(videoId)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                repository.updateDownload(videoId) { it.copy(status = DownloadStatus.Failed, error = "Não foi possível iniciar. Tente novamente.") }
                eventChannel.send(SpixEvent.Message("Não foi possível iniciar o download."))
            }
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

    fun remove(videoId: String) {
        viewModelScope.launch {
            try { downloads.cancel(videoId); repository.removeRequest(videoId) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { eventChannel.send(SpixEvent.Message("Não foi possível remover o item.")) }
        }
    }
}
