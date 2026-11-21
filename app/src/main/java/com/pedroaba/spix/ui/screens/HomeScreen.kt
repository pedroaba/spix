package com.pedroaba.spix.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.pedroaba.spix.domain.Track
import com.pedroaba.spix.domain.MediaKind
import com.pedroaba.spix.domain.DownloadRequest
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pedroaba.spix.ui.components.*
import com.pedroaba.spix.ui.theme.SpixColors
import com.pedroaba.spix.domain.VideoPreviewState

@Composable
fun HomeScreen(link: String, error: String?, busy: Boolean, preview: VideoPreviewState, motion: Boolean, tracks: List<Track>, requests: List<DownloadRequest>, onLink: (String) -> Unit, onSearch: () -> Unit, onSubmit: () -> Unit) {
    val libraryCount = tracks.size
    var filter by rememberSaveable { mutableStateOf("Todas") }
    val visible = remember(tracks, filter) { tracks.filter { filter == "Todas" || (filter == "Vídeos" && it.kind == MediaKind.Video) || (filter == "Músicas" && it.kind == MediaKind.Music) } }
    val thumbnails = remember(requests) { requests.associate { it.videoId to it.metadata?.thumbnailUrl } }
    val keyboard = LocalSoftwareKeyboardController.current
    val loading = preview is VideoPreviewState.Loading
    fun submit() {
        keyboard?.hide()
        if (preview is VideoPreviewState.Ready) onSubmit() else onSearch()
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "compose-download") { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(8.dp))
        Column {
            Text("Sua música, sem internet.", fontSize = 25.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold)
            Text("Cole um link e leve com você.", Modifier.padding(top = 5.dp), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = link, onValueChange = onLink, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
            shape = CircleShape, placeholder = { Text("Cole o link do YouTube", fontSize = 14.sp) },
            leadingIcon = { Icon(SpixIcons.Search, null, Modifier.size(22.dp)) },
            isError = error != null,
            supportingText = if (error != null) ({ Text(error) }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (!loading && !busy) submit() }),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.secondary),
        )
        VideoPreview(preview, motion, onSearch)
        Button(onClick = ::submit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            enabled = !busy && preview is VideoPreviewState.Ready,
            colors = ButtonDefaults.buttonColors(containerColor = SpixColors.Red, contentColor = androidx.compose.ui.graphics.Color.White)) {
            Icon(SpixIcons.Download, null, Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("Baixar vídeo", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Sua biblioteca", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("$libraryCount ${if (libraryCount == 1) "item" else "itens"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        LibraryFilters { filter = it }
        if (libraryCount == 0) EmptyState(SpixIcons.Headphones, "Sua biblioteca começa aqui", "Cole um link do YouTube acima para adicionar seu primeiro vídeo.")
        else if (visible.isEmpty()) Text("Nenhum item neste filtro.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        items(visible, key = { "video:${it.id}" }, contentType = { "video" }) { track ->
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column {
                    VideoThumbnail(thumbnails[track.id], Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)))
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(listOf(track.artist, track.duration).filter { it.isNotBlank() }.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Disponível offline", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OpenVideoButton(track.localUri)
                        }
                    }
                }
            }
        }
    }
}
