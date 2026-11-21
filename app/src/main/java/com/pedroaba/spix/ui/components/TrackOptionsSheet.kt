package com.pedroaba.spix.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pedroaba.spix.SpixApplication
import com.pedroaba.spix.data.MediaFileDetails
import com.pedroaba.spix.data.readMediaFileDetails
import com.pedroaba.spix.domain.Track

val LocalDeleteTrack = staticCompositionLocalOf<(Track) -> Unit> { {} }

data class PlaylistTrackMoves(val up: (() -> Unit)?, val down: (() -> Unit)?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOptionsSheet(
    track: Track,
    onDismiss: () -> Unit,
    onPlay: (() -> Unit)? = null,
    moves: PlaylistTrackMoves? = null,
) {
    val context = LocalContext.current
    val save = LocalSaveToPlaylist.current
    val delete = LocalDeleteTrack.current
    var details by remember(track.id, track.localUri) { mutableStateOf<MediaFileDetails?>(null) }
    var failed by remember(track.id) { mutableStateOf(false) }
    LaunchedEffect(track.id, track.localUri) {
        try {
            val request = (context.applicationContext as SpixApplication).repository.get(track.id)
            details = readMediaFileDetails(context, track, request)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    val playback = if (onPlay == null) Modifier.playVideoOnClick(track.localUri)
        else Modifier.clickable(onClick = onPlay)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    VideoThumbnail(track.thumbnailUrl, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        details?.rows?.firstOrNull { it.first == "Tamanho no celular" }?.second?.substringBefore(" (")?.let {
                            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            item {
                ListItem(headlineContent = { Text("Ouvir agora") }, leadingContent = { Icon(SpixIcons.Play, null) }, modifier = playback,
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
                ListItem(headlineContent = { Text("Salvar na playlist") }, leadingContent = { Icon(SpixIcons.VideoLibrary, null) },
                    modifier = Modifier.clickable { onDismiss(); save(track) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
                if (moves != null) {
                    MoveTrackOption("Mover para cima", rotation = 180f, onMove = moves.up, onDismiss = onDismiss)
                    MoveTrackOption("Mover para baixo", rotation = 0f, onMove = moves.down, onDismiss = onDismiss)
                }
                ListItem(headlineContent = { Text("Excluir do celular", color = MaterialTheme.colorScheme.error) }, leadingContent = { Icon(SpixIcons.Trash, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { onDismiss(); delete(track) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Detalhes do arquivo", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (details == null && !failed) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (failed || details?.unavailable == true) item { Text("O arquivo não está acessível. Confira se ele ainda existe e se a pasta está disponível.", color = MaterialTheme.colorScheme.error) }
            items(details?.rows.orEmpty(), key = { it.first }) { (label, value) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SelectionContainer { Text(value, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}


@Composable
private fun MoveTrackOption(label: String, rotation: Float, onMove: (() -> Unit)?, onDismiss: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (onMove != null) 1f else .38f)
    ListItem(
        headlineContent = { Text(label, color = color) },
        leadingContent = { Icon(SpixIcons.Collapse, null, Modifier.rotate(rotation), tint = color) },
        modifier = Modifier.clickable(enabled = onMove != null) {
            onDismiss()
            onMove?.invoke()
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
}
