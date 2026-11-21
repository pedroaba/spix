package com.pedroaba.spix.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pedroaba.spix.data.SpixRepository
import com.pedroaba.spix.data.LibraryQuery
import com.pedroaba.spix.ui.catalogViewModel
import androidx.paging.LoadState
import androidx.paging.compose.*
import com.pedroaba.spix.data.LocalPlaylistRepository
import com.pedroaba.spix.data.PlaylistSummary
import com.pedroaba.spix.domain.DownloadStatus
import com.pedroaba.spix.domain.Track
import com.pedroaba.spix.download.PlaylistSync
import com.pedroaba.spix.download.PlaylistSyncResult
import com.pedroaba.spix.playback.LocalPlayerAccess
import com.pedroaba.spix.playback.PlayingBars
import com.pedroaba.spix.ui.components.*
import kotlinx.coroutines.launch

@Composable
private fun PlaylistRow(
    playlist: PlaylistSummary,
    playing: Boolean,
    onOpen: () -> Unit,
    onPlay: (() -> Unit)?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val itemCount = "${playlist.total} ${if (playlist.total == 1) "item" else "itens"}"
    val details = listOfNotNull(
        "YouTube".takeIf { playlist.id.startsWith("youtube:") },
        itemCount,
        "${playlist.count} offline".takeIf { playlist.count < playlist.total },
    ).joinToString(" · ")

    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(.46f)) {
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth(.86f).height(20.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
            )
            VideoThumbnail(
                playlist.coverUri,
                Modifier.padding(top = 7.dp).fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)),
            )
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(7.dp),
                shape = CircleShape,
                color = if (playing) MaterialTheme.colorScheme.primaryContainer else Color.Black.copy(alpha = .72f),
                contentColor = Color.White,
            ) {
                if (playing) {
                    PlayingBars(Modifier.padding(4.dp).size(16.dp), MaterialTheme.colorScheme.onPrimaryContainer)
                } else {
                    Icon(SpixIcons.VideoLibrary, contentDescription = null, modifier = Modifier.padding(4.dp).size(16.dp))
                }
            }
        }
        Column(Modifier.weight(.54f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(playlist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (playing) {
                Text("Tocando agora", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(SpixIcons.MoreVertical, "Opções de ${playlist.name}", Modifier.rotate(90f))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Reproduzir") }, enabled = onPlay != null, onClick = {
                    menuOpen = false
                    onPlay?.invoke()
                })
                DropdownMenuItem(text = { Text("Editar playlist") }, onClick = { menuOpen = false; onEdit() })
                DropdownMenuItem(text = { Text("Excluir playlist") }, onClick = { menuOpen = false; onDelete() })
            }
        }
    }
}

private data class PlaylistDrag(
    val track: Track,
    val y: Float,
    val grabOffset: Float,
    val slotHeight: Int,
    val hasMoved: Boolean,
)

private data class PlaylistDrop(val id: String, val after: Boolean)

private fun LazyListLayoutInfo.playlistDrop(drag: PlaylistDrag, loaded: Map<String, Track>): PlaylistDrop? {
    val item = visibleItemsInfo
        .filter { it.key in loaded }
        .minByOrNull { abs(drag.y - (it.offset + it.size / 2f)) } ?: return null
    val target = item.key as String
    if (target == drag.track.id) return null
    return PlaylistDrop(target, drag.y >= item.offset + item.size / 2f)
}

private fun playlistRowShift(
    index: Int,
    source: Int,
    target: Int,
    after: Boolean,
    slotHeight: Int,
): Float {
    if (source < target && index > source && (index < target || index == target && after)) {
        return -slotHeight.toFloat()
    }
    if (source > target && index < source && (index > target || index == target && !after)) {
        return slotHeight.toFloat()
    }
    return 0f
}

@Composable
fun PlaylistDetail(id: String, repository: LocalPlaylistRepository, catalogRepository: SpixRepository, onEdit: () -> Unit, onBack: () -> Unit, playlistSync: PlaylistSync) {
    val observed = remember(repository, id) { repository.observe(id) }
    val playlist by observed.collectAsStateWithLifecycle(initialValue = null)
    val artworkFlow = remember(repository, id) { repository.artwork(id) }
    val artwork by artworkFlow.collectAsStateWithLifecycle(initialValue = null)
    val catalog = catalogViewModel(catalogRepository, "playlist-detail")
    LaunchedEffect(id) { catalog.query.value = LibraryQuery(playlistId = id) }
    val available = catalog.library.collectAsLazyPagingItems()
    val availableCount by catalog.libraryCount.collectAsStateWithLifecycle()
    val activeDownload by catalogRepository.activeDownload.collectAsStateWithLifecycle(initialValue = null)
    var deleting by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val syncFlow = remember(repository, id) { repository.observeSync(id) }
    val syncState by syncFlow.collectAsStateWithLifecycle(initialValue = null)
    var syncMenuOpen by remember(id) { mutableStateOf(false) }
    var syncing by remember(id) { mutableStateOf(false) }
    var syncMessage by remember(id) { mutableStateOf<String?>(null) }
    val player = LocalPlayerAccess.current
    val listState = rememberLazyListState()
    var drag by remember(id) { mutableStateOf<PlaylistDrag?>(null) }
    var savingOrder by remember(id) { mutableStateOf(false) }
    var orderError by remember(id) { mutableStateOf<String?>(null) }
    val loaded by remember(available) { derivedStateOf { available.itemSnapshotList.items.associateBy { it.id } } }
    val edge = with(LocalDensity.current) { 56.dp.toPx() }
    val haptics = LocalHapticFeedback.current
    val canReorder = availableCount > 1 && !savingOrder
    BackHandler { if (!savingOrder) onBack() }
    val currentPlaylist = playlist
    if (currentPlaylist == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val downloading = activeDownload?.takeIf {
        it.status == DownloadStatus.Downloading && it.videoId in currentPlaylist.videoIds
    }
    fun play(track: Track) { player?.vm?.open(track.localUri, currentPlaylist.id); player?.open?.invoke() }
    fun move(trackId: String, drop: PlaylistDrop) {
        if (savingOrder) return
        savingOrder = true
        orderError = null
        scope.launch {
            try {
                repository.moveVideo(id, trackId, drop.id, drop.after)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                orderError = "Não foi possível salvar a ordem. Tente novamente."
            } finally {
                savingOrder = false
            }
        }
    }
    LaunchedEffect(drag?.track?.id) {
        if (drag == null) return@LaunchedEffect
        listState.stopScroll()
        while (true) {
            withFrameNanos { }
            val moving = drag ?: break
            if (!moving.hasMoved) continue
            val layout = listState.layoutInfo
            val top = layout.viewportStartOffset + edge
            val bottom = layout.viewportEndOffset - edge
            val distance = when {
                moving.y < top -> moving.y - top
                moving.y > bottom -> moving.y - bottom
                else -> 0f
            }
            if (distance != 0f) listState.scrollBy(distance.coerceIn(-edge, edge) / 7f)
        }
    }
    val drop = drag?.takeIf { it.hasMoved }?.let { listState.layoutInfo.playlistDrop(it, loaded) }
    val positions = remember(currentPlaylist.videoIds) {
        currentPlaylist.videoIds.withIndex().associate { (index, id) -> id to index }
    }
    val sourcePosition = drag?.let { positions[it.track.id] }
    val targetPosition = drop?.let { positions[it.id] }
    Box(Modifier.fillMaxSize().clipToBounds()) {
        LazyColumn(
            Modifier.fillMaxSize().pointerInput(id, canReorder) {
                if (!canReorder) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (down.isConsumed) return@awaitEachGesture
                    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                        it.key in loaded && down.position.y >= it.offset && down.position.y < it.offset + it.size
                    } ?: return@awaitEachGesture
                    val track = loaded[item.key] ?: return@awaitEachGesture
                    val slotHeight = item.size + listState.layoutInfo.mainAxisItemSpacing
                    try {
                        // Let the row receive its ordinary press before waiting for a hold.
                        awaitPointerEvent(PointerEventPass.Final)
                        val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        drag = PlaylistDrag(track, held.position.y, held.position.y - item.offset, slotHeight, hasMoved = false)
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            change.consume()
                            if (!change.pressed) {
                                drag?.takeIf { it.hasMoved }?.copy(y = change.position.y)?.let { released ->
                                    listState.layoutInfo.playlistDrop(released, loaded)?.let { move(track.id, it) }
                                }
                                break
                            }
                            val moving = drag ?: break
                            drag = moving.copy(
                                y = change.position.y,
                                hasMoved = moving.hasMoved || abs(change.position.y - down.position.y) > viewConfiguration.touchSlop,
                            )
                        }
                    } finally {
                        drag = null
                    }
                }
            },
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(SpixIcons.Back, "Voltar para playlists") }
                    Text("Playlist", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onEdit) { Text("Editar") }
                    IconButton(onClick = { deleting = true }) { Icon(SpixIcons.Trash, "Excluir playlist") }
                    syncState?.let { state ->
                        Box {
                            IconButton(onClick = { syncMenuOpen = true }) { Icon(SpixIcons.MoreVertical, "Opções da playlist") }
                            DropdownMenu(expanded = syncMenuOpen, onDismissRequest = { syncMenuOpen = false }) {
                                DropdownMenuItem(text = { Text("Sincronizar agora") }, enabled = !syncing, onClick = {
                                    syncMenuOpen = false
                                    syncing = true
                                    syncMessage = null
                                    scope.launch {
                                        try {
                                            syncMessage = when (val result = playlistSync.sync(id)) {
                                                is PlaylistSyncResult.Synced -> when (result.added) {
                                                    0 -> "Nenhum vídeo novo."
                                                    1 -> "1 vídeo adicionado à playlist."
                                                    else -> "${result.added} vídeos adicionados à playlist."
                                                }
                                                PlaylistSyncResult.Inaccessible -> "A playlist do YouTube está inacessível."
                                                PlaylistSyncResult.Retry -> "Não foi possível sincronizar. Tente novamente."
                                                PlaylistSyncResult.Skipped -> null
                                            }
                                        } finally {
                                            syncing = false
                                        }
                                    }
                                })
                                DropdownMenuItem(
                                    text = { Text("Sincronização automática") },
                                    modifier = Modifier.semantics { stateDescription = if (state.automatic) "Ativada" else "Desativada" },
                                    trailingIcon = { Checkbox(checked = state.automatic, onCheckedChange = null) },
                                    onClick = {
                                        syncMenuOpen = false
                                        scope.launch {
                                            try {
                                                repository.setSyncAutomatic(id, !state.automatic)
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (_: Exception) {
                                                syncMessage = "Não foi possível salvar a preferência. Tente novamente."
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoThumbnail(artwork,
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)))
                    Text(currentPlaylist.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("$availableCount de ${currentPlaylist.videoIds.size} ${if (currentPlaylist.videoIds.size == 1) "item disponível" else "itens disponíveis"} offline", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    syncState?.let { state ->
                        val lastSync = remember(state.lastSyncedAt) {
                            state.lastSyncedAt?.let {
                                java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                                    .format(java.util.Date(it))
                            }
                        }
                        val status = if (syncing) "Consultando o YouTube…" else lastSync?.let { "Última consulta em $it" } ?: "Aguardando a primeira consulta ao YouTube"
                        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        syncMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Button(onClick = { scope.launch { repository.firstAvailableUri(id)?.let { uri -> player?.vm?.open(uri, id); player?.open?.invoke() } } }, enabled = available.itemCount > 0 && player != null) {
                        Icon(SpixIcons.Play, null); Spacer(Modifier.width(8.dp)); Text("Reproduzir playlist")
                    }
                    if (currentPlaylist.videoIds.size > availableCount) Text("Há vídeos pendentes, indisponíveis ou ainda não baixados nesta playlist.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            downloading?.let { download ->
                item(key = "playlist-active-download") {
                    ListItem(
                        modifier = Modifier.fillMaxWidth().clip(CompactTrackRowShape),
                        leadingContent = {
                            Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        },
                        headlineContent = {
                            Text(download.metadata?.title ?: download.videoId, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = { Text("Baixando…") },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    )
                }
            }
            if (downloading == null && available.itemCount == 0 && available.loadState.refresh !is LoadState.Loading) item {
                if (currentPlaylist.videoIds.isEmpty()) EmptyState(SpixIcons.VideoLibrary, "Sua playlist está vazia", "Toque em Editar para escolher vídeos e músicas da biblioteca.")
                else EmptyState(SpixIcons.Download, "Seus vídeos aparecerão aqui", "A playlist já foi criada. Acompanhe os downloads para ouvir offline.")
            }
            items(available.itemCount, key = available.itemKey { it.id }, contentType = available.itemContentType { "playlist-track" }) { index ->
                val track = available[index] ?: return@items
                val position = positions[track.id]
                val moving = drag
                val shift by animateFloatAsState(
                    targetValue = if (position != null && sourcePosition != null && targetPosition != null &&
                        moving != null
                    ) playlistRowShift(position, sourcePosition, targetPosition, drop.after, moving.slotHeight) else 0f,
                    animationSpec = spring(stiffness = 550f, dampingRatio = .85f),
                    label = "playlistRowShift",
                )
                val snapshot = available.itemSnapshotList
                val moveUp: (() -> Unit)? = snapshot.getOrNull(index - 1)?.takeIf { canReorder }?.let { previous ->
                    { move(track.id, PlaylistDrop(previous.id, false)) }
                }
                val moveDown: (() -> Unit)? = snapshot.getOrNull(index + 1)?.takeIf { canReorder }?.let { next ->
                    { move(track.id, PlaylistDrop(next.id, true)) }
                }
                Box(
                    Modifier.fillMaxWidth()
                        .graphicsLayer {
                            translationY = shift
                            alpha = if (moving?.track?.id == track.id) 0f else 1f
                        }
                        .semantics {
                            if (canReorder) {
                                contentDescription = "Reordenar ${track.title}"
                                customActions = buildList {
                                    moveUp?.let { add(CustomAccessibilityAction("Mover para cima") { it(); true }) }
                                    moveDown?.let { add(CustomAccessibilityAction("Mover para baixo") { it(); true }) }
                                }
                            }
                        },
                ) {
                    TrackRow(
                        track, track.thumbnailUrl,
                        onPlay = { play(track) },
                        moves = PlaylistTrackMoves(moveUp, moveDown),
                    )
                }
            }
        }
        drag?.let { moving ->
            val tilt by rememberInfiniteTransition(label = "liftedPlaylistVideo").animateFloat(
                initialValue = -.4f,
                targetValue = .4f,
                animationSpec = infiniteRepeatable(tween(220), RepeatMode.Reverse),
                label = "playlistVideoTilt",
            )
            Surface(
                modifier = Modifier.fillMaxWidth()
                    .offset { IntOffset(0, (moving.y - moving.grabOffset).roundToInt()) }
                    .graphicsLayer { rotationZ = tilt }
                    .clearAndSetSemantics { },
                shape = CompactTrackRowShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
            ) {
                TrackRow(
                    moving.track, moving.track.thumbnailUrl,
                    onPlay = {},
                )
            }
        }
        if (orderError != null) {
            Snackbar(Modifier.align(Alignment.BottomCenter).padding(8.dp),
                action = { TextButton(onClick = { orderError = null }) { Text("Fechar") } }) {
                Text(orderError.orEmpty())
            }
        }
    }
    if (deleting) {
        DeletePlaylistDialog(repository, id, currentPlaylist.name, scope, onDismiss = { deleting = false }, onDeleted = {
            deleting = false
            onBack()
        })
    }
}

@Composable
private fun DeletePlaylistDialog(
    repository: LocalPlaylistRepository,
    id: String,
    name: String,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Excluir playlist?") },
        text = { Column { Text("“$name” será excluída. Os arquivos baixados continuarão na biblioteca."); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try { repository.delete(id); onDeleted() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message ?: "Não foi possível excluir a playlist." }
                finally { busy = false }
            }
        }) { Text("Excluir") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
fun PlaylistEditorScreen(playlistId: String?, initialVideoId: String?, catalogRepository: SpixRepository,
    repository: LocalPlaylistRepository, onBack: () -> Unit, onSaved: () -> Unit) {
    var coverUri by rememberSaveable(playlistId) { mutableStateOf<String?>(null) }
    var name by rememberSaveable(playlistId) { mutableStateOf("") }
    var selected by rememberSaveable(playlistId) { mutableStateOf(listOfNotNull(initialVideoId)) }
    var originalSelection by rememberSaveable(playlistId) { mutableStateOf(emptyList<String>()) }
    var initialized by rememberSaveable(playlistId) { mutableStateOf(playlistId == null) }
    var query by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var choosingCover by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(playlistId) {
        if (!initialized && playlistId != null) {
            try {
                val playlist = repository.get(playlistId) ?: error("Esta playlist não está mais disponível.")
                name = playlist.name; selected = playlist.videoIds; originalSelection = playlist.videoIds
                coverUri = playlist.coverUri?.takeIf { it.startsWith("https://") || it.startsWith("http://") }; initialized = true
            } catch (failure: Exception) { error = failure.message ?: "Não foi possível abrir a playlist." }
        }
    }
    BackHandler(enabled = true) { if (!busy) onBack() }
    val catalog = catalogViewModel(catalogRepository, "playlist-editor")
    LaunchedEffect(query) { catalog.query.value = LibraryQuery(search = query) }
    val visible = catalog.library.collectAsLazyPagingItems()
    val selectedIds = remember(selected) { selected.toHashSet() }
    if (choosingCover) PlaylistThumbnailPicker(repository, selected, coverUri,
        onDismiss = { choosingCover = false }, onChoose = { coverUri = it; choosingCover = false })
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !busy) { Icon(SpixIcons.Back, "Voltar") }
            Text(if (playlistId == null) "Nova playlist" else "Editar playlist", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            VideoThumbnail(coverUri ?: visible.itemSnapshotList.items.firstOrNull { it.id in selectedIds }?.thumbnailUrl, Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)))
                            Column {
                                TextButton(enabled = initialized && !busy && selected.isNotEmpty(), onClick = { choosingCover = true }) {
                                    Text(if (coverUri == null) "Escolher thumbnail" else "Trocar thumbnail")
                                }
                                if (coverUri != null) TextButton(enabled = !busy, onClick = { coverUri = null }) { Text("Usar automática") }
                            }
                        }
                        Text("Escolha a thumbnail de um vídeo da playlist. Por padrão, usamos a primeira disponível.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(name, { name = it; error = null }, label = { Text("Nome da playlist") },
                            placeholder = { Text("Ex.: Para relaxar") }, singleLine = true, enabled = initialized && !busy,
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                        Text("Salva neste dispositivo · disponível offline", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Text("Adicionar da biblioteca", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${selected.size} ${if (selected.size == 1) "item selecionado" else "itens selecionados"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                SearchInput(query) { query = it }
            }
            if (visible.loadState.refresh is LoadState.Loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (visible.itemCount == 0 && visible.loadState.refresh !is LoadState.Loading) item {
                Text(if (query.isBlank()) "Sua biblioteca está vazia. Você pode criar a playlist agora e adicionar vídeos depois." else "Nenhum item encontrado. Tente outro nome.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(visible.itemCount, key = visible.itemKey { it.id }) { index ->
                val track = visible[index] ?: return@items
                val checked = track.id in selectedIds
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .toggleable(value = checked, enabled = initialized && !busy, role = Role.Checkbox,
                        onValueChange = {
                            selected = if (it) selected + track.id else selected - track.id
                            if (!it && coverUri == track.thumbnailUrl) coverUri = null
                        }).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VideoThumbnail(track.thumbnailUrl, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Checkbox(checked, null, enabled = initialized && !busy)
                }
            }
        }
        error?.let { Text(it, Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.error) }
        Button(enabled = initialized && name.isNotBlank() && !busy, onClick = {
            busy = true
            scope.launch {
                try {
                    repository.save(playlistId, name, selected, coverUri, updateCover = true, originalVideoIds = originalSelection)
                    onSaved()
                }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message ?: "Não foi possível salvar a playlist." }
                finally { busy = false }
            }
        }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).navigationBarsPadding().heightIn(min = 48.dp)) {
            Text(if (busy) "Salvando…" else if (playlistId == null) "Criar playlist" else "Salvar alterações")
        }
    }
}

@Composable
fun PlaylistsScreen(repository: LocalPlaylistRepository, onCreate: () -> Unit, onOpen: (String) -> Unit, onEdit: (String) -> Unit) {
    val pages = remember(repository) { repository.pages() }.collectAsLazyPagingItems()
    val count by repository.count.collectAsStateWithLifecycle(initialValue = 0)
    val player = LocalPlayerAccess.current
    val scope = rememberCoroutineScope()
    val playback = player?.vm?.info?.collectAsStateWithLifecycle()?.value
    val playingPlaylistId = playback?.takeIf { it.playing && !it.buffering }?.playlistId
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingName by rememberSaveable { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    PageTitle("Playlists", "$count ${if (count == 1) "playlist" else "playlists"}")
                }
                FilledTonalButton(onClick = onCreate) {
                    Icon(SpixIcons.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Criar")
                }
            }
        }
        if (pages.loadState.refresh is LoadState.Loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (pages.loadState.refresh is LoadState.Error) item {
            TextButton(onClick = pages::retry) { Text("Tentar carregar playlists novamente") }
        }
        if (pages.itemCount == 0 && pages.loadState.refresh is LoadState.NotLoading) item {
            EmptyState(SpixIcons.VideoLibrary, "Nenhuma playlist", "Toque em Criar para organizar suas músicas e vídeos.")
        }
        items(pages.itemCount, key = pages.itemKey { it.id }) { index ->
            pages[index]?.let { playlist ->
                PlaylistRow(
                    playlist = playlist,
                    playing = playlist.id == playingPlaylistId,
                    onOpen = { onOpen(playlist.id) },
                    onPlay = if (playlist.count > 0 && player != null) {
                        {
                            scope.launch {
                                repository.firstAvailableUri(playlist.id)?.let { uri ->
                                    player.vm.open(uri, playlist.id)
                                    player.open()
                                }
                            }
                        }
                    } else null,
                    onEdit = { onEdit(playlist.id) },
                    onDelete = { deletingId = playlist.id; deletingName = playlist.name },
                )
            }
        }
    }
    deletingId?.let { id ->
        DeletePlaylistDialog(repository, id, deletingName, scope, onDismiss = { deletingId = null }, onDeleted = { deletingId = null })
    }
}
