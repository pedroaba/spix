package com.pedroaba.spix.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.*
import com.pedroaba.spix.data.LocalPlaylistRepository
import com.pedroaba.spix.domain.Track
import com.pedroaba.spix.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveToPlaylistSheet(track: Track, repository: LocalPlaylistRepository, onDismiss: () -> Unit, onCreate: () -> Unit) {
    val pages = remember(repository) { repository.pages() }.collectAsLazyPagingItems()
    val membershipFlow = remember(repository, track.id) { repository.memberships(track.id) }
    val membership by membershipFlow.collectAsStateWithLifecycle(initialValue = null)
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Salvar na playlist", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(track.title, maxLines = 2, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ListItem(headlineContent = { Text("Nova playlist") }, leadingContent = { Icon(SpixIcons.Add, null) },
                modifier = Modifier.clickable(enabled = !busy, onClick = onCreate),
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                if (pages.loadState.refresh is LoadState.Loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (pages.itemCount == 0 && pages.loadState.refresh !is LoadState.Loading) item {
                    Text("Crie sua primeira playlist para salvar este vídeo.", Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(pages.itemCount, key = pages.itemKey { it.id }) { index ->
                    val playlist = pages[index] ?: return@items
                    val checked = membership?.contains(playlist.id) == true
                    ListItem(headlineContent = { Text(playlist.name) }, supportingContent = { Text(if (playlist.total > playlist.count) "${playlist.count} de ${playlist.total} disponíveis offline" else "${playlist.count} ${if (playlist.count == 1) "item" else "itens"}") },
                        leadingContent = { VideoThumbnail(playlist.coverUri, Modifier.size(48.dp)) },
                        trailingContent = { Checkbox(checked, null, enabled = !busy && membership != null) },
                        modifier = Modifier.toggleable(value = checked, enabled = !busy && membership != null, role = Role.Checkbox, onValueChange = { included ->
                            busy = true; error = null; feedback = null
                            scope.launch {
                                try {
                                    repository.setMembership(playlist.id, track.id, included)
                                    feedback = if (included) "Salvo em ${playlist.name}" else "Removido de ${playlist.name}"
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (failure: Exception) { error = failure.message ?: "Não foi possível atualizar a playlist. Tente novamente." }
                                finally { busy = false }
                            }
                        }), colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            feedback?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            Button(onClick = onDismiss, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Concluir") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSelectionToPlaylistSheet(
    videoIds: List<String>,
    repository: LocalPlaylistRepository,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val pages = remember(repository) { repository.pages() }.collectAsLazyPagingItems()
    var creating by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun save(id: String?, playlistName: String) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                val added = repository.addDownloadedSelection(id, playlistName, videoIds)
                onSaved(when (added) {
                    0 -> "A playlist já contém os itens disponíveis da seleção."
                    1 -> "1 item adicionado à playlist ${playlistName.trim()}."
                    else -> "$added itens adicionados à playlist ${playlistName.trim()}."
                })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "Não foi possível atualizar a playlist. Tente novamente."
            } finally {
                busy = false
            }
        }
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (creating) "Nova playlist" else "Adicionar à playlist", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(if (videoIds.size == 1) "1 item selecionado" else "${videoIds.size} itens selecionados",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (creating) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome da playlist") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { save(null, name) }, enabled = name.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Criar e adicionar")
                }
                TextButton(onClick = { creating = false; error = null }, enabled = !busy) { Text("Voltar") }
            } else {
                ListItem(
                    headlineContent = { Text("Nova playlist") },
                    leadingContent = { Icon(SpixIcons.Add, null) },
                    modifier = Modifier.clickable(enabled = !busy) { creating = true; error = null },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    if (pages.loadState.refresh is LoadState.Loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    items(pages.itemCount, key = pages.itemKey { it.id }) { index ->
                        val playlist = pages[index] ?: return@items
                        ListItem(
                            headlineContent = { Text(playlist.name) },
                            leadingContent = { VideoThumbnail(playlist.coverUri, Modifier.size(48.dp)) },
                            modifier = Modifier.clickable(enabled = !busy) { save(playlist.id, playlist.name) },
                        )
                    }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
