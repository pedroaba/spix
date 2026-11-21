package com.pedroaba.spix.playback

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.*
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.pedroaba.spix.SpixApplication
import com.pedroaba.spix.domain.DownloadStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PlaybackInfo(
    val connected: Boolean = false, val mediaId: String? = null, val title: String = "", val artist: String = "",
    val artwork: String? = null, val playing: Boolean = false, val buffering: Boolean = false,
    val duration: Long = 0, val shuffle: Boolean = false, val repeat: Int = Player.REPEAT_MODE_OFF,
    val video: Boolean = true, val aspect: Float = 16f / 9f, val error: String? = null,
    val queue: List<QueueEntry> = emptyList(), val index: Int = 0, val queueTitle: String = "Fila de reprodução",
    val playlistId: String? = null,
)
private const val PLAYLIST_ID_EXTRA = "com.pedroaba.spix.PLAYLIST_ID"
@androidx.compose.runtime.Immutable
data class QueueEntry(val id: String, val title: String, val artist: String, val artwork: String?)

class PlaybackViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SpixApplication
    private val mutableInfo = MutableStateFlow(PlaybackInfo())
    val info = mutableInfo.asStateFlow()
    private val mutablePosition = MutableStateFlow(0L)
    val position = mutablePosition.asStateFlow()
    var controller: MediaController? = null
        private set
    private var future: ListenableFuture<MediaController>? = null
    private var closed = false
    private data class PendingOpen(val uri: String, val playlistId: String?)
    private var pendingOpen: PendingOpen? = null
    private var activePlaylistId: String? = null
    private var desiredVideo = true
    private var openJob: Job? = null
    private var queueImportJob: Job? = null
    private var queueSnapshot: List<QueueEntry> = emptyList()
    private var queueIds: List<String> = emptyList()
    private var pendingSnapshot: List<QueueEntry>? = null
    private var activePlaylistTitle = "Fila de reprodução"
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) updateQueueSnapshot(player)
            refresh()
        }
    }

    private fun updateQueueSnapshot(player: Player) {
        queueImportJob?.cancel()
        val pending = pendingSnapshot
        if (pending != null && pending.size == player.mediaItemCount) {
            queueSnapshot = pending
            queueIds = pending.map { it.id }
            pendingSnapshot = null
            return
        }
        if (player.mediaItemCount == 0) {
            queueSnapshot = emptyList(); queueIds = emptyList(); return
        }
        // Controller access stays on its application thread; bounded chunks yield to input/drawing.
        queueImportJob = viewModelScope.launch {
            val result = ArrayList<QueueEntry>(player.mediaItemCount)
            repeat(player.mediaItemCount) { index ->
                ensureActive()
                val item = player.getMediaItemAt(index)
                result += QueueEntry(item.mediaId, item.mediaMetadata.title?.toString().orEmpty(), item.mediaMetadata.artist?.toString().orEmpty(), item.mediaMetadata.artworkUri?.toString())
                if (index % 40 == 39) delay(1)
            }
            queueSnapshot = result.toList(); queueIds = result.map { it.id }; refresh()
        }
    }

    init {
        connect()
        viewModelScope.launch {
            mutablePosition.subscriptionCount.collectLatest { subscribers ->
                if (subscribers > 0) while (isActive) {
                    mutablePosition.value = controller?.currentPosition?.coerceAtLeast(0) ?: 0
                    delay(400)
                }
            }
        }
    }

    private fun connect() {
        future?.let(MediaController::releaseFuture)
        val connection = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java)))
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(player: MediaController) {
                    if (closed || controller !== player) return
                    player.removeListener(listener)
                    controller = null
                    mutableInfo.update { it.copy(connected = false, playing = false, buffering = false, error = "O player foi desconectado. Tente novamente.") }
                }
            }).buildAsync()
        future = connection
        connection.addListener({
            if (closed || future !== connection) return@addListener
            runCatching { connection.get() }.onSuccess { player ->
                controller = player
                player.addListener(listener)
                applyVideo()
                updateQueueSnapshot(player)
                refresh()
                pendingOpen?.let { pending -> pendingOpen = null; open(pending.uri, pending.playlistId) }
            }.onFailure {
                mutableInfo.update { it.copy(error = "Não foi possível conectar ao player. Tente novamente.") }
            }
        }, ContextCompat.getMainExecutor(app))
    }

    fun open(uri: String, playlistId: String? = null) {
        val player = controller
        if (player == null) { pendingOpen = PendingOpen(uri, playlistId); return }
        openJob?.cancel()
        openJob = viewModelScope.launch {
            val available = app.repository.playbackQueue()
            val playlist = playlistId?.let { app.localPlaylists.get(it) }
            if (playlistId != null && playlist == null) {
                mutableInfo.update { it.copy(error = "Esta playlist não está mais disponível.") }
                return@launch
            }
            val downloads = withContext(Dispatchers.Default) { playlistQueue(available, playlist) }
            val selected = downloads.indexOfFirst { it.localUri == uri }
            if (selected < 0) { mutableInfo.update { it.copy(error = "Este vídeo não está disponível na biblioteca.") }; return@launch }
            val ids = downloads.map { it.videoId }
            val sameQueue = activePlaylistId == playlistId && queueIds == ids
            activePlaylistId = playlistId
            activePlaylistTitle = playlist?.name ?: "Fila de reprodução"
            if (!sameQueue) {
                val items = withContext(Dispatchers.Default) {
                    val playlistExtras = playlistId?.let { id ->
                        android.os.Bundle().apply { putString(PLAYLIST_ID_EXTRA, id) }
                    }
                    downloads.map { item ->
                        MediaItem.Builder().setMediaId(item.videoId).setUri(item.localUri!!)
                            .setMediaMetadata(MediaMetadata.Builder().setTitle(item.metadata?.title ?: "Vídeo")
                                .setArtist(item.metadata?.author ?: "YouTube")
                                .setArtworkUri(item.metadata?.thumbnailUrl?.let(Uri::parse))
                                .setExtras(playlistExtras).build()).build()
                    }
                }
                ensureActive()
                if (controller !== player || closed) return@launch
                pendingSnapshot = downloads.map { QueueEntry(it.videoId, it.metadata?.title ?: "Vídeo", it.metadata?.author ?: "YouTube", it.metadata?.thumbnailUrl) }
                queueSnapshot = requireNotNull(pendingSnapshot); queueIds = ids
                player.setMediaItems(items, selected, 0)
                player.prepare()
            } else if (player.currentMediaItemIndex != selected) {
                player.seekToDefaultPosition(selected)
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
            } else if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            else if (player.playerError != null) player.prepare()
            player.play()
            refresh()
        }
    }

    fun togglePlay() { controller?.let(::togglePlayback) }
    fun seek(position: Long) { controller?.seekTo(position.coerceIn(0, info.value.duration.coerceAtLeast(0))); mutablePosition.value = position.coerceAtLeast(0) }
    fun previous() { controller?.seekToPrevious() }
    fun next() { controller?.seekToNext() }
    fun select(index: Int) { controller?.run { seekToDefaultPosition(index); prepare(); play() } }
    fun shuffle() { controller?.run { shuffleModeEnabled = !shuffleModeEnabled } }
    fun repeat() { controller?.run { repeatMode = when (repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF } } }
    fun removeDeleted(videoId: String) {
        controller?.let { player ->
            for (index in player.mediaItemCount - 1 downTo 0) {
                if (player.getMediaItemAt(index).mediaId == videoId) player.removeMediaItem(index)
            }
        }
    }
    fun stop() { controller?.run { stop(); clearMediaItems() } }
    fun retry() {
        if (controller == null) {
            viewModelScope.launch {
                info.value.mediaId?.let { app.repository.get(it) }?.localUri?.let { pendingOpen = PendingOpen(it, activePlaylistId) }
                connect()
            }
        } else controller?.run { prepare(); play() }
    }
    fun video(enabled: Boolean) {
        desiredVideo = enabled
        applyVideo()
        if (!enabled && controller?.playerError != null) controller?.prepare()
        refresh()
    }
    private fun applyVideo() {
        // Visibility changes must not reselect tracks: that can flush audio as well as video.
        // ExoPlayer handles missing surfaces; only the explicit audio-only mode disables video.
        controller?.run {
            if (trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO) != !desiredVideo) {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !desiredVideo).build()
            }
        }
    }

    private fun refresh() {
        val player = controller ?: return
        val metadata = player.mediaMetadata
        val size = player.videoSize
        mutableInfo.value = PlaybackInfo(
            connected = true, mediaId = player.currentMediaItem?.mediaId, title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(), artwork = metadata.artworkUri?.toString(),
            playing = player.playWhenReady && player.playbackState != Player.STATE_ENDED && player.playerError == null,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0,
            shuffle = player.shuffleModeEnabled, repeat = player.repeatMode, video = desiredVideo,
            aspect = if (size.width > 0 && size.height > 0) size.width * size.pixelWidthHeightRatio / size.height else 16f / 9f,
            error = player.playerError?.let { "Não foi possível reproduzir este arquivo. Verifique se ele ainda existe e se o formato é compatível." },
            queue = queueSnapshot,
            index = player.currentMediaItemIndex.coerceAtLeast(0),
            queueTitle = activePlaylistTitle,
            playlistId = player.currentMediaItem?.mediaMetadata?.extras?.getString(PLAYLIST_ID_EXTRA),
        )
        mutablePosition.value = player.currentPosition.coerceAtLeast(0)
    }

    override fun onCleared() {
        closed = true
        controller?.removeListener(listener)
        future?.let(MediaController::releaseFuture)
        controller = null
    }
}

internal fun togglePlayback(player: Player) {
    if (player.isPlaying || (player.playWhenReady && player.playbackState != Player.STATE_ENDED)) player.pause()
    else {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }
}

/** A playlist queue keeps its own order and never falls back to the whole library. */
internal fun playlistQueue(
    available: List<com.pedroaba.spix.domain.DownloadRequest>,
    playlist: com.pedroaba.spix.data.LocalPlaylist?,
): List<com.pedroaba.spix.domain.DownloadRequest> = if (playlist == null) available else
    available.associateBy { it.videoId }.let { byId -> playlist.videoIds.distinct().mapNotNull(byId::get) }
