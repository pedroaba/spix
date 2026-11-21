package com.pedroaba.spix.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.pedroaba.spix.ui.theme.SpixColors

@Composable
fun HomeScreen(link: String, error: String?, busy: Boolean, preview: VideoPreviewState, repository: SpixRepository,
    cards: Boolean, onCards: (Boolean) -> Unit, onLink: (String) -> Unit, onSubmit: () -> Unit, onDownloads: () -> Unit, onCancel: (String) -> Unit) {
    val catalog = catalogViewModel(repository)
    val visible = catalog.library.collectAsLazyPagingItems()
    val counts by catalog.counts.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    fun submit() { keyboard?.hide(); onSubmit() }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
        item(key = "add") {
            Column {
                Text("Sua música, sem internet.", fontSize = 25.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold)
                Text("Cole um link e leve com você.", Modifier.padding(top = 4.dp), fontSize = 16.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                LinkInput(link, onLink, error, !busy, ::submit)
                Spacer(Modifier.height(12.dp))
                Button(onClick = ::submit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy && link.isNotBlank() && error == null,
                    colors = ButtonDefaults.buttonColors(containerColor = SpixColors.Red, contentColor = androidx.compose.ui.graphics.Color.White)) {
                    Icon(SpixIcons.Download, null, Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
                    Text(if (busy) "Adicionando à fila…" else if (preview is VideoPreviewState.PlaylistReady) "Baixar playlist" else "Baixar vídeo", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (counts.active > 0) item(key = "active") {
            Spacer(Modifier.height(8.dp))
            ActiveHomeDownload(catalog, counts.active, onDownloads, onCancel)
        }
        if (error == null) item(key = "library-header") {
            Column(Modifier.padding(top = if (counts.active == 0) 26.dp else 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Sua biblioteca", fontSize = 21.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold)
                        Text("${counts.library} ${if (counts.library == 1) "item" else "itens"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LibraryLayoutToggle(cards, onCards)
                }
            }
        }
        when {
            error != null -> item(key = "help") { LinkHelp { onLink("") } }
            counts.library == 0 && visible.loadState.refresh !is LoadState.Loading -> item(key = "empty") {
                EmptyState(SpixIcons.Headphones, "Sua biblioteca começa aqui", "Cole um link do YouTube acima para ouvir sua primeira música offline.")
            }
        }
        if (error == null) items(visible.itemCount, key = visible.itemKey { it.id }, contentType = visible.itemContentType { "track" }) { index ->
            visible[index]?.let { TrackRow(it, it.thumbnailUrl, expanded = cards) }
        }
    }
}

@Composable
private fun LinkHelp(onClear: () -> Unit) {
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Como adicionar", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        listOf("Abra o YouTube" to "Encontre o vídeo ou a playlist que deseja baixar.",
            "Copie o link" to "Toque em Compartilhar e Copiar link.",
            "Cole no Spix" to "Confira o conteúdo antes de baixar.").forEachIndexed { index, (title, detail) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { Text("${index + 1}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                }
                Column { Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
            }
        }
        SecondaryAction("Limpar e colar outro link", onClear)
    }
}

@Composable
private fun ActiveHomeDownload(catalog: com.pedroaba.spix.ui.CatalogViewModel, count: Int, onDownloads: () -> Unit, onCancel: (String) -> Unit) {
    val request by catalog.active.collectAsStateWithLifecycle()
    request?.let { HomeDownloadProgress(it, count, onDownloads) { onCancel(it.videoId) } }
}
