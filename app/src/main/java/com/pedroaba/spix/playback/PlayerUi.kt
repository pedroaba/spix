package com.pedroaba.spix.playback

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.pedroaba.spix.ui.components.*

class PlayerAccess(val vm: PlaybackViewModel, val open: () -> Unit)
val LocalPlayerAccess = staticCompositionLocalOf<PlayerAccess?> { null }
private val playerColors = darkColorScheme(background = Color.Black, surface = Color.Black, primary = Color.White,
    onPrimary = Color.Black, onSurface = Color.White, onSurfaceVariant = Color(0xFFB5B5B5), surfaceContainer = Color(0xFF202020))

@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(vm: PlaybackViewModel, onBack: () -> Unit) {
    val info by vm.info.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var queueOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        val window = activity?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val lightStatus = insets?.isAppearanceLightStatusBars
        val lightNavigation = insets?.isAppearanceLightNavigationBars
        insets?.isAppearanceLightStatusBars = false
        insets?.isAppearanceLightNavigationBars = false
        onDispose {
            lightStatus?.let { insets.isAppearanceLightStatusBars = it }
            lightNavigation?.let { insets.isAppearanceLightNavigationBars = it }
        }
    }
    DisposableEffect(lifecycle) {
        vm.surface(lifecycle.isAtLeast(Lifecycle.State.STARTED))
        onDispose { vm.surface(false) }
    }
    MaterialTheme(colorScheme = playerColors) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val mediaHeight = maxOf(maxWidth, maxHeight - 390.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(SpixIcons.Collapse, "Minimizar player") }
                Spacer(Modifier.weight(1f))
                Row(Modifier.clip(CircleShape).background(Color(0xFF202020)).padding(4.dp).selectableGroup()) {
                    listOf(false to "Música", true to "Vídeo").forEach { (video, label) ->
                        Box(Modifier.clip(CircleShape).background(if (info.video == video) Color(0xFF414141) else Color.Transparent)
                            .selectable(selected = info.video == video, role = Role.RadioButton, onClick = { vm.video(video) })
                            .padding(horizontal = 20.dp, vertical = 12.dp)) { Text(label, fontWeight = FontWeight.SemiBold) }
                    }
                }
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menuOpen = true }, enabled = info.mediaId != null) { Icon(SpixIcons.MoreVertical, "Mais opções") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = Color(0xFF202020)) {
                        DropdownMenuItem(text = { Text("Fila de reprodução") }, onClick = { menuOpen = false; queueOpen = true })
                        DropdownMenuItem(text = { Text("Reiniciar faixa") }, onClick = { menuOpen = false; vm.seek(0) })
                        DropdownMenuItem(text = { Text("Encerrar reprodução") }, onClick = { menuOpen = false; vm.stop(); onBack() })
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().height(mediaHeight), contentAlignment = Alignment.Center) {
                if (info.video && info.mediaId != null && info.connected) {
                    val ratio = info.aspect.coerceIn(.5f, 3f)
                    val width = minOf(maxWidth, maxHeight * ratio)
                    AndroidView(
                        factory = { context -> PlayerView(context).apply { useController = false; setEnableComposeSurfaceSyncWorkaround(true); keepScreenOn = true; player = vm.controller } },
                        update = { it.player = vm.controller },
                        onRelease = { it.player = null },
                        modifier = Modifier.width(width).aspectRatio(ratio).clip(RoundedCornerShape(12.dp)),
                    )
                } else VideoThumbnail(info.artwork, Modifier.size(maxWidth - 24.dp).clip(RoundedCornerShape(18.dp)))
                if (info.buffering || !info.connected) CircularProgressIndicator(Modifier.size(36.dp), color = Color.White)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(info.title.ifBlank { "Selecione um vídeo" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (info.artist.isNotBlank()) Text(info.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            info.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = vm::retry) { Text("Tentar novamente") }
            }
            PlaybackTimeline(vm, info.duration)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconToggleButton(checked = info.shuffle, onCheckedChange = { vm.shuffle() }, enabled = info.queue.size > 1) {
                    Icon(SpixIcons.Shuffle, if (info.shuffle) "Desativar aleatório" else "Ativar aleatório",
                        tint = if (info.shuffle) Color(0xFFFF7C98) else LocalContentColor.current)
                }
                PlayerButton(SpixIcons.Previous, "Anterior", vm::previous, info.mediaId != null)
                FilledIconButton(onClick = vm::togglePlay, enabled = info.mediaId != null && info.connected && info.error == null,
                    modifier = Modifier.size(76.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)) {
                    Icon(if (info.playing) SpixIcons.Pause else SpixIcons.Play, if (info.playing) "Pausar" else "Reproduzir", Modifier.size(36.dp))
                }
                PlayerButton(SpixIcons.Next, "Próximo", vm::next,
                    info.queue.size > 1 && (info.index < info.queue.lastIndex || info.shuffle || info.repeat == Player.REPEAT_MODE_ALL))
                IconButton(onClick = vm::repeat, enabled = info.mediaId != null) {
                    BadgedBox(badge = { if (info.repeat == Player.REPEAT_MODE_ONE) Badge { Text("1") } }) {
                        Icon(SpixIcons.Repeat, when (info.repeat) {
                            Player.REPEAT_MODE_OFF -> "Repetição desligada. Repetir fila"
                            Player.REPEAT_MODE_ALL -> "Repetindo fila. Repetir faixa"
                            else -> "Repetindo faixa. Desligar repetição"
                        }, tint = if (info.repeat != Player.REPEAT_MODE_OFF) Color(0xFFFF7C98) else LocalContentColor.current)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().clickable(enabled = info.queue.isNotEmpty(), role = Role.Button,
                onClickLabel = "Abrir fila de reprodução") { queueOpen = true }.padding(top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("A seguir", fontWeight = FontWeight.SemiBold)
                    Text("${info.queue.size} na fila", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Box(Modifier.width(64.dp).height(2.dp).background(Color.White))
            }
        }
        }
        if (queueOpen) ModalBottomSheet(onDismissRequest = { queueOpen = false }, containerColor = Color(0xFF171717)) {
            Text("Fila de reprodução", Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 450.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(info.queue, key = { _, item -> item.id }) { index, item ->
                    ListItem(
                        modifier = Modifier.clickable { vm.select(index); queueOpen = false },
                        headlineContent = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(item.artist, maxLines = 1) },
                        leadingContent = { VideoThumbnail(item.artwork, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))) },
                        trailingContent = { if (index == info.index) Icon(SpixIcons.Headphones, "Faixa atual", tint = Color(0xFFFF7C98)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackTimeline(vm: PlaybackViewModel, duration: Long) {
    val position by vm.position.collectAsStateWithLifecycle()
    var dragging by remember { mutableStateOf<Float?>(null) }
    val maximum = duration.coerceAtLeast(1).toFloat()
    Column {
        Slider(value = dragging ?: position.toFloat().coerceIn(0f, maximum), onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { vm.seek(it.toLong()) }; dragging = null },
            valueRange = 0f..maximum, enabled = duration > 0,
            thumb = { Box(Modifier.size(14.dp).background(Color.White, CircleShape)) },
            track = { state ->
                Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                    val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                    drawRoundRect(Color(0xFF414141), cornerRadius = radius)
                    val fraction = (state.value / maximum).coerceIn(0f, 1f)
                    if (fraction > 0) drawRoundRect(Color.White,
                        size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height), cornerRadius = radius)
                }
            },
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0xFF444444)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(playbackTime(dragging?.toLong() ?: position), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (duration > 0) playbackTime(duration) else "--:--", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlayerButton(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) { Icon(icon, label, Modifier.size(30.dp)) }
}

@Composable
fun MiniPlayer(access: PlayerAccess) {
    val info by access.vm.info.collectAsStateWithLifecycle()
    if (info.mediaId == null) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().clickable(onClick = access.open).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            VideoThumbnail(info.artwork, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(info.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(info.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = access.vm::togglePlay) { Icon(if (info.playing) SpixIcons.Pause else SpixIcons.Play, if (info.playing) "Pausar" else "Reproduzir") }
            IconButton(onClick = access.vm::stop) { Icon(SpixIcons.Close, "Encerrar reprodução") }
        }
    }
}

internal fun playbackTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
