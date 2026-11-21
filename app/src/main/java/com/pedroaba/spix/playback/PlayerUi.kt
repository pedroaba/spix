package com.pedroaba.spix.playback

import android.view.LayoutInflater
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import com.pedroaba.spix.R
import com.pedroaba.spix.ui.components.*
import kotlinx.coroutines.delay

class PlayerAccess(val vm: PlaybackViewModel, val open: () -> Unit)
val LocalPlayerAccess = staticCompositionLocalOf<PlayerAccess?> { null }

@Composable
fun PlayerScreen(vm: PlaybackViewModel, pictureInPicture: Boolean = false, onBack: () -> Unit) {
    val info by vm.info.collectAsStateWithLifecycle()
    var queueOpen by rememberSaveable { mutableStateOf(true) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val queueState = rememberLazyListState()
    LaunchedEffect(info.index, queueOpen) {
        if (queueOpen && info.queue.isNotEmpty()) queueState.scrollToItem(info.index)
    }
    BackHandler(fullscreen) { fullscreen = false }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // Keep this view in the same composition slot for fullscreen and PiP transitions.
            WatchPlayer(vm, info, onBack = { if (fullscreen) fullscreen = false else onBack() },
                onFullscreen = { fullscreen = !fullscreen }, fullscreen = fullscreen, pictureInPicture = pictureInPicture)
            if (!pictureInPicture && !fullscreen) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(info.title.ifBlank { "Selecione um vídeo" }, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (info.artist.isNotBlank()) Text(info.artist, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    info.error?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = vm::retry) { Text("Tentar novamente") }
                    }
                }
                Surface(Modifier.fillMaxWidth().weight(1f), color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(info.queueTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (info.queue.isEmpty()) "Nenhum vídeo na fila" else "${info.index + 1} de ${info.queue.size} ${if (info.queue.size == 1) "vídeo" else "vídeos"}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { queueOpen = !queueOpen }) {
                                Icon(if (queueOpen) SpixIcons.Close else SpixIcons.Add,
                                    if (queueOpen) "Recolher fila" else "Mostrar fila")
                            }
                        }
                        QueuePlaybackControls(info, onRepeat = vm::repeat, onShuffle = vm::shuffle)
                        if (queueOpen) {
                            LazyColumn(Modifier.fillMaxWidth().weight(1f), state = queueState, contentPadding = PaddingValues(bottom = 16.dp)) {
                                itemsIndexed(info.queue, key = { _, item -> item.id }) { index, item ->
                                    QueueVideo(item, selected = index == info.index, playing = info.playing && !info.buffering) { vm.select(index) }
                                }
                            }
                        } else {
                            TextButton(onClick = { queueOpen = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text("Mostrar vídeos da fila")
                            }
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun WatchPlayer(vm: PlaybackViewModel, info: PlaybackInfo, onBack: () -> Unit,
    onFullscreen: () -> Unit, fullscreen: Boolean = false, pictureInPicture: Boolean = false) {
    var controlsVisible by remember { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }
    val reportSourceBounds = LocalPictureInPictureSourceBounds.current
    LaunchedEffect(controlsVisible, info.playing, info.buffering, info.error, menuOpen) {
        if (controlsVisible && info.playing && !info.buffering && info.error == null && !menuOpen) {
            delay(4000)
            controlsVisible = false
        }
    }
    val showControls = controlsVisible || !info.playing || info.buffering || info.error != null
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Column(
            (if (fullscreen || pictureInPicture) Modifier.fillMaxSize() else Modifier.fillMaxWidth()).background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(if (fullscreen || pictureInPicture) Modifier.fillMaxWidth().weight(1f)
                else Modifier.weight(1f, fill = false).aspectRatio(videoAspect(info.aspect))) {
                if (info.video && info.mediaId != null && info.connected) {
                    AndroidView(
                        factory = { context -> PlayerView(context).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setEnableComposeSurfaceSyncWorkaround(true)
                            keepScreenOn = true
                            player = vm.controller
                        } },
                        update = {
                            if (it.player !== vm.controller) it.player = vm.controller
                            it.keepScreenOn = info.playing
                            it.contentDescription = if (pictureInPicture) "Vídeo em janela flutuante" else "Vídeo"
                        },
                        onRelease = { it.player = null },
                        modifier = Modifier.fillMaxSize().clipToBounds().onGloballyPositioned { coordinates ->
                            if (!pictureInPicture) {
                                    val bounds = coordinates.boundsInWindow()
                                    if (bounds.width > 0 && bounds.height > 0) reportSourceBounds(android.graphics.Rect(
                                        bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt()))
                                }
                            },
                        )
                    } else VideoThumbnail(info.artwork, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        containerColor = Color.Black)
                    if (!pictureInPicture) {
                    Box(Modifier.matchParentSize().clickable(role = Role.Button,
                        onClickLabel = if (showControls) "Ocultar controles" else "Mostrar controles") { controlsVisible = !showControls })
                    if (showControls) {
                        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .65f), Color.Transparent, Color.Black.copy(alpha = .65f)))))
                        Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) { Icon(SpixIcons.Collapse, if (fullscreen) "Sair da tela cheia" else "Minimizar player") }
                            Spacer(Modifier.weight(1f))
                            if (!info.video) Text("Somente áudio", style = MaterialTheme.typography.labelMedium)
                            Box {
                                IconButton(onClick = { menuOpen = true }) { Icon(SpixIcons.MoreVertical, "Opções de reprodução") }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(text = { Text(if (info.video) "Ouvir somente áudio" else "Mostrar vídeo") },
                                        onClick = { menuOpen = false; vm.video(!info.video) }, enabled = info.mediaId != null)
                                    DropdownMenuItem(text = { Text("Reiniciar vídeo") }, onClick = { menuOpen = false; vm.seek(0) }, enabled = info.mediaId != null)
                                    DropdownMenuItem(text = { Text("Encerrar reprodução") }, onClick = { menuOpen = false; vm.stop(); onBack() })
                                }
                            }
                        }
                        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = vm::previous, enabled = info.mediaId != null,
                                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = .38f))) {
                                Icon(SpixIcons.Previous, "Anterior", Modifier.size(32.dp))
                            }
                            FilledIconButton(onClick = vm::togglePlay,
                                enabled = info.mediaId != null && info.connected && info.error == null,
                                modifier = Modifier.size(64.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = .5f), contentColor = Color.White,
                                    disabledContainerColor = Color.Black.copy(alpha = .4f), disabledContentColor = Color.White.copy(alpha = .38f))) {
                                if (info.buffering || (!info.connected && info.error == null)) CircularProgressIndicator(Modifier.size(30.dp), color = Color.White, strokeWidth = 2.dp)
                                else Icon(if (info.playing) SpixIcons.Pause else SpixIcons.Play,
                                    if (info.playing) "Pausar" else "Reproduzir", Modifier.size(36.dp))
                            }
                            IconButton(onClick = vm::next,
                                enabled = info.queue.size > 1 && (info.index < info.queue.lastIndex || info.shuffle || info.repeat == Player.REPEAT_MODE_ALL),
                                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = .38f))) {
                                Icon(SpixIcons.Next, "Próximo", Modifier.size(32.dp))
                            }
                        }
                    }
                    Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                        val frame = if (info.video) Modifier.aspectRatio(videoAspect(info.aspect)) else Modifier.fillMaxSize()
                        Box(frame) {
                            PlaybackTimeline(vm, info.mediaId, info.duration, showControls, Modifier.align(Alignment.BottomCenter))
                        }
                    }
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 10.dp)) {
                        if (showControls) {
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                PlaybackTimestamp(vm, info.duration)
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = onFullscreen) { FullscreenGlyph(fullscreen) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullscreenGlyph(fullscreen: Boolean) {
    Canvas(Modifier.size(24.dp).semantics { contentDescription = if (fullscreen) "Sair da tela cheia" else "Tela cheia" }) {
        val edge = size.width / 6
        val length = size.width / 5
        for (x in listOf(edge, size.width - edge)) for (y in listOf(edge, size.height - edge)) {
            val dx = if (x < size.width / 2) 1 else -1
            val dy = if (y < size.height / 2) 1 else -1
            val corner = if (fullscreen) Offset(x + dx * length, y + dy * length) else Offset(x, y)
            val direction = if (fullscreen) -1 else 1
            drawLine(Color.White, corner, Offset(corner.x + dx * length * direction, corner.y), 2.dp.toPx())
            drawLine(Color.White, corner, Offset(corner.x, corner.y + dy * length * direction), 2.dp.toPx())
        }
    }
}

@Composable
private fun PlaybackTimestamp(vm: PlaybackViewModel, duration: Long) {
    val position by vm.position.collectAsStateWithLifecycle()
    Text("${playbackTime(position)} / ${if (duration > 0) playbackTime(duration) else "--:--"}",
        style = MaterialTheme.typography.labelMedium, color = Color.White)
}

@Composable
private fun QueuePlaybackControls(info: PlaybackInfo, onRepeat: () -> Unit, onShuffle: () -> Unit) {
    val repeatOne = info.repeat == Player.REPEAT_MODE_ONE
    val repeatLabel = when (info.repeat) {
        Player.REPEAT_MODE_ONE -> "Repetir vídeo"
        Player.REPEAT_MODE_ALL -> "Repetir fila"
        else -> "Repetir"
    }
    val colors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        selectedContainerColor = MaterialTheme.colorScheme.inverseSurface,
        selectedLabelColor = MaterialTheme.colorScheme.inverseOnSurface,
        selectedLeadingIconColor = MaterialTheme.colorScheme.inverseOnSurface,
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = info.repeat != Player.REPEAT_MODE_OFF,
            onClick = onRepeat,
            enabled = info.mediaId != null,
            label = { Text(repeatLabel) },
            leadingIcon = {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    Icon(SpixIcons.Repeat, null, Modifier.fillMaxSize())
                    if (repeatOne) Text("1", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                }
            },
            modifier = Modifier.weight(1f, fill = false),
            shape = CircleShape,
            border = null,
            colors = colors,
        )
        FilterChip(
            selected = info.shuffle,
            onClick = onShuffle,
            enabled = info.queue.size > 1,
            label = { Text(if (info.shuffle) "Aleatório ligado" else "Aleatório") },
            leadingIcon = { Icon(SpixIcons.Shuffle, null, Modifier.size(24.dp)) },
            modifier = Modifier.weight(1f, fill = false),
            shape = CircleShape,
            border = null,
            colors = colors,
        )
    }
}

@Composable
private fun QueueVideo(item: QueueEntry, selected: Boolean, playing: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
    Row(Modifier.fillMaxWidth().background(background).selectable(selected = selected, role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.width(136.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))) {
            VideoThumbnail(item.artwork, Modifier.fillMaxSize())
            if (selected) Surface(Modifier.align(Alignment.BottomEnd).padding(4.dp), color = Color.Black.copy(alpha = .8f), shape = RoundedCornerShape(4.dp)) {
                if (playing) {
                    PlayingBars(Modifier.padding(4.dp).size(16.dp))
                } else {
                    Icon(SpixIcons.Pause, null, Modifier.padding(4.dp).size(16.dp), tint = Color.White)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (selected) Text(if (playing) "Reproduzindo" else "Vídeo atual", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun PlayingBars(modifier: Modifier, color: Color = Color.White) {
    val phase by rememberInfiniteTransition(label = "playingBars").animateFloat(
        initialValue = 0f,
        targetValue = (2 * kotlin.math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "barPhase",
    )
    Canvas(modifier) {
        val barWidth = size.width / 6f
        val corner = CornerRadius(barWidth / 2)
        repeat(3) { index ->
            val angle = phase * (if (index == 1) 2 else 1) + index * 2f
            val height = size.height * (0.57f + 0.32f * kotlin.math.sin(angle))
            val x = size.width * (index + 1) / 4f - barWidth / 2
            drawRoundRect(color, Offset(x, size.height - height), Size(barWidth, height), corner)
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun MiniPlayer(access: PlayerAccess, modifier: Modifier = Modifier) {
    val info by access.vm.info.collectAsStateWithLifecycle()
    if (info.mediaId == null) return
    Surface(
        modifier = modifier.width(192.dp).aspectRatio(16f / 9f),
        shape = RoundedCornerShape(12.dp),
        color = Color.Black,
        contentColor = Color.White,
        shadowElevation = 8.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (info.video && info.connected) {
                AndroidView(
                    factory = { context ->
                        (LayoutInflater.from(context).inflate(R.layout.mini_player_video, null) as PlayerView)
                            .apply { player = access.vm.controller }
                    },
                    update = { view ->
                        if (view.player !== access.vm.controller) view.player = access.vm.controller
                    },
                    onRelease = { it.player = null },
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                VideoThumbnail(info.artwork, Modifier.matchParentSize(), containerColor = Color.Black)
            }
            Box(
                Modifier.matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .55f), Color.Transparent, Color.Transparent)))
                    .semantics { contentDescription = "Miniplayer de ${info.title}" }
                    .clickable(role = Role.Button, onClickLabel = "Abrir player", onClick = access.open),
            )
            IconButton(
                onClick = access.vm::togglePlay,
                enabled = info.connected && info.error == null,
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Icon(if (info.playing) SpixIcons.Pause else SpixIcons.Play, if (info.playing) "Pausar" else "Reproduzir")
            }
            if (!info.playing) {
                IconButton(onClick = access.vm::stop, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(SpixIcons.Close, "Fechar miniplayer")
                }
            }
        }
    }
}

internal fun videoAspect(aspect: Float): Float =
    aspect.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)

internal fun playbackTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
