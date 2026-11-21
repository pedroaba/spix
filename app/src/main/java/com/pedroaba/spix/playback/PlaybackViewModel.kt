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
    val video: Boolean = false, val aspect: Float = 16f / 9f, val error: String? = null,
    val queue: List<QueueEntry> = emptyList(), val index: Int = 0,
)
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
    private var pendingUri: String? = null
    private var desiredVideo = false
    private var surfaceVisible = false
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
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
                refresh()
                pendingUri?.let { uri -> pendingUri = null; open(uri) }
            }.onFailure {
                mutableInfo.update { it.copy(error = "Não foi possível conectar ao player. Tente novamente.") }
            }
        }, ContextCompat.getMainExecutor(app))
    }

    fun open(uri: String) {
        val player = controller
        if (player == null) { pendingUri = uri; return }
        val downloads = app.repository.requests.value.filter { it.status == DownloadStatus.Completed && it.localUri != null }
        val selected = downloads.indexOfFirst { it.localUri == uri }
        if (selected < 0) { mutableInfo.update { it.copy(error = "Este vídeo não está disponível na biblioteca.") }; return }
        if (player.currentMediaItem?.mediaId != downloads[selected].videoId || player.mediaItemCount == 0) {
            val items = downloads.map { item ->
                MediaItem.Builder().setMediaId(item.videoId).setUri(item.localUri!!)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(item.metadata?.title ?: "Vídeo")
                        .setArtist(item.metadata?.author ?: "YouTube")
                        .setArtworkUri(item.metadata?.thumbnailUrl?.let(Uri::parse)).build()).build()
            }
            player.setMediaItems(items, selected, 0)
            player.prepare()
        } else if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        else if (player.playerError != null) player.prepare()
        player.play()
        refresh()
    }

    fun togglePlay() { controller?.let(::togglePlayback) }
    fun seek(position: Long) { controller?.seekTo(position.coerceIn(0, info.value.duration.coerceAtLeast(0))); mutablePosition.value = position.coerceAtLeast(0) }
    fun previous() { controller?.seekToPrevious() }
    fun next() { controller?.seekToNext() }
    fun select(index: Int) { controller?.run { seekToDefaultPosition(index); prepare(); play() } }
    fun shuffle() { controller?.run { shuffleModeEnabled = !shuffleModeEnabled } }
    fun repeat() { controller?.run { repeatMode = when (repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF } } }
    fun stop() { controller?.run { stop(); clearMediaItems() } }
    fun retry() {
        if (controller == null) {
            pendingUri = app.repository.requests.value.firstOrNull { it.videoId == info.value.mediaId }?.localUri ?: pendingUri
            connect()
        } else controller?.run { prepare(); play() }
    }
    fun video(enabled: Boolean) {
        desiredVideo = enabled
        applyVideo()
        if (!enabled && controller?.playerError != null) controller?.prepare()
        refresh()
    }
    fun surface(visible: Boolean) { surfaceVisible = visible; applyVideo() }
    private fun applyVideo() {
        controller?.run { trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !desiredVideo || !surfaceVisible).build() }
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
            queue = List(player.mediaItemCount) { index -> player.getMediaItemAt(index).let { QueueEntry(it.mediaId, it.mediaMetadata.title?.toString().orEmpty(), it.mediaMetadata.artist?.toString().orEmpty(), it.mediaMetadata.artworkUri?.toString()) } },
            index = player.currentMediaItemIndex.coerceAtLeast(0),
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
