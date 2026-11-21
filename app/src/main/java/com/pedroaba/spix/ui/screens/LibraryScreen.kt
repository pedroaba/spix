package com.pedroaba.spix.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pedroaba.spix.domain.*
import com.pedroaba.spix.data.*
import com.pedroaba.spix.ui.catalogViewModel
import androidx.paging.LoadState
import androidx.paging.compose.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pedroaba.spix.ui.components.*

@Composable
fun LibraryScreen(
    repository: SpixRepository,
    playlists: LocalPlaylistRepository,
    removalBusy: Boolean,
    onRemove: (List<String>) -> Unit,
    onPlaylistSaved: (String) -> Unit,
) {
    var alphabetical by rememberSaveable { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selection by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var confirmingDeletion by rememberSaveable { mutableStateOf(false) }
    var choosingPlaylist by rememberSaveable { mutableStateOf(false) }
    val selectedIds = remember(selection) { selection.toHashSet() }
    fun endSelection() {
        selection = emptyList()
        selecting = false
    }
    BackHandler(enabled = selecting) { endSelection() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val catalog = catalogViewModel(repository)
    LaunchedEffect(alphabetical) { catalog.query.value = LibraryQuery(alphabetical = alphabetical) }
    val visible = catalog.library.collectAsLazyPagingItems()
    val counts by catalog.counts.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            Surface(shape = CompactTrackRowShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = ::endSelection) { Icon(SpixIcons.Close, "Sair da seleção") }
                        Text(
                            if (selection.size == 1) "1 item selecionado" else "${selection.size} itens selecionados",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { choosingPlaylist = true }, enabled = selection.isNotEmpty() && !removalBusy) {
                            Text("Adicionar à playlist")
                        }
                        TextButton(
                            onClick = { confirmingDeletion = true },
                            enabled = selection.isNotEmpty() && !removalBusy,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text("Excluir") }
                    }
                }
            }
        } else if (removalBusy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
            item(key = "title") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        PageTitle("Sua biblioteca", "${counts.library} ${if (counts.library == 1) "item disponível" else "itens disponíveis"} offline")
                    }
                    if (!selecting && counts.library > 0) {
                        TextButton(onClick = { selecting = true }, enabled = !removalBusy) { Text("Selecionar") }
                    }
                    IconToggleButton(checked = alphabetical, onCheckedChange = { alphabetical = it }) {
                        Icon(SpixIcons.Sort, if (alphabetical) "Ordenar por mais recentes" else "Ordenar por nome", Modifier.size(22.dp), tint = if (alphabetical) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (counts.library == 0 && visible.loadState.refresh !is LoadState.Loading) item(key = "empty") { EmptyState(SpixIcons.Headphones, "Sua biblioteca começa aqui", "Os vídeos baixados aparecerão aqui.") }
            items(visible.itemCount, key = visible.itemKey { it.id }, contentType = visible.itemContentType { "track" }) { index ->
                val track = visible[index] ?: return@items
                TrackRow(track, track.thumbnailUrl, selected = track.id in selectedIds, onToggleSelection = if (selecting) {
                    { selection = if (track.id in selection) selection - track.id else selection + track.id }
                } else null)
            }
            if (visible.itemCount > 0) item(key = "offline") {
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Tudo pronto para ouvir offline", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Os arquivos baixados ficam neste dispositivo.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (confirmingDeletion) {
        AlertDialog(
            onDismissRequest = { confirmingDeletion = false },
            title = { Text(if (selection.size == 1) "Excluir 1 item do celular?" else "Excluir ${selection.size} itens do celular?") },
            text = { Text("Os arquivos selecionados serão removidos do celular, da biblioteca e das playlists.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDeletion = false
                        onRemove(selection)
                        endSelection()
                    },
                    enabled = selection.isNotEmpty() && !removalBusy,
                ) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDeletion = false }) { Text("Cancelar") } },
        )
    }
    if (choosingPlaylist) {
        AddSelectionToPlaylistSheet(selection, playlists, onDismiss = { choosingPlaylist = false }, onSaved = { message ->
            choosingPlaylist = false
            endSelection()
            onPlaylistSaved(message)
        })
    }
}

@Composable
fun SearchScreen(query: String, repository: SpixRepository, onQuery: (String) -> Unit, onBack: () -> Unit, onAdd: () -> Unit) {
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    fun add() { keyboard?.hide(); onAdd() }
    val catalog = catalogViewModel(repository)
    LaunchedEffect(query) { catalog.query.value = LibraryQuery(search = query, searchOnly = true) }
    val results = catalog.library.collectAsLazyPagingItems()
    val count by catalog.libraryCount.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "header") {
            Row(Modifier.heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { keyboard?.hide(); onBack() }) { Icon(SpixIcons.Back, "Voltar") }
                Text("Buscar", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        item(key = "input") {
            SearchInput(query, onQuery)
        }
        if (query.isBlank() || (results.itemCount == 0 && results.loadState.refresh !is LoadState.Loading)) item(key = "empty") {
            EmptyState(SpixIcons.Search, if (query.isBlank()) "Encontre suas músicas" else "Nenhuma música encontrada",
                if (query.isBlank()) "Busque pelo nome da música ou do artista na sua biblioteca." else "Não há resultados para “$query”.\nTente outro nome ou adicione um link.", Modifier.padding(top = 36.dp))
            SecondaryAction("Adicionar por link", ::add, SpixIcons.Add)
        } else {
            item(key = "results-title") {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Na sua biblioteca", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("${count} ${if (count == 1) "resultado" else "resultados"} para “$query”", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(results.itemCount, key = results.itemKey { it.id }, contentType = results.itemContentType { "track" }) { index -> results[index]?.let { TrackRow(it, it.thumbnailUrl) } }
            item(key = "add") {
                Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Leve mais músicas com você", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Para adicionar conteúdo, cole um link do YouTube na tela inicial.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    SecondaryAction("Adicionar por link", ::add, SpixIcons.Add)
                }
            }
        }
    }
}
