package com.pedroaba.spix.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pedroaba.spix.data.LocalPlaylistRepository
import com.pedroaba.spix.data.PlaylistCoverOption
import com.pedroaba.spix.ui.components.VideoThumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistThumbnailPicker(repository: LocalPlaylistRepository, ids: List<String>, selected: String?,
    onDismiss: () -> Unit, onChoose: (String?) -> Unit) {
    var options by remember(ids) { mutableStateOf<List<PlaylistCoverOption>?>(null) }
    var error by remember(ids) { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(repository, ids, attempt) {
        error = false
        try { options = repository.covers(ids) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Capa da playlist", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Escolha a thumbnail de um dos vídeos selecionados.", Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Row(Modifier.fillMaxWidth().selectable(selected == null, role = Role.RadioButton, onClick = { onChoose(null) }).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Usar a primeira thumbnail disponível", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    RadioButton(selected == null, onClick = null)
                }
            }
            if (error) item {
                Text("Não foi possível carregar as thumbnails.", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { attempt++ }) { Text("Tentar novamente") }
            }
            else if (options == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if (options.orEmpty().isEmpty()) item { Text("Os vídeos selecionados ainda não têm thumbnails disponíveis.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(options.orEmpty(), key = { it.videoId }) { option ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .selectable(selected == option.thumbnailUrl, role = Role.RadioButton, onClick = { onChoose(option.thumbnailUrl) })
                    .padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    VideoThumbnail(option.thumbnailUrl, Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)))
                    Text(option.title ?: "Vídeo", Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    RadioButton(selected == option.thumbnailUrl, onClick = null)
                }
            }
        }
    }
}
