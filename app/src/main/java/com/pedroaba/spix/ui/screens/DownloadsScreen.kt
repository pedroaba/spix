package com.pedroaba.spix.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pedroaba.spix.download.downloadStageLabel
import com.pedroaba.spix.domain.*
import com.pedroaba.spix.data.SpixRepository
import com.pedroaba.spix.ui.catalogViewModel
import com.pedroaba.spix.data.DownloadFilter
import androidx.paging.LoadState
import androidx.paging.compose.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.*
import com.pedroaba.spix.ui.components.*

@Composable
fun DownloadsScreen(repository: SpixRepository, onRemove: (String) -> Unit, onRetry: (String) -> Unit, onCancel: (String) -> Unit, onCancelAll: () -> Unit, retryBusy: Boolean, onRetryMany: (DownloadFilter, List<String>?) -> Unit, queueRequest: Int, onLibrary: () -> Unit) {
    var cancellingAll by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<DownloadRequest?>(null) }
    var selected by rememberSaveable { mutableStateOf("Em andamento") }
    var selecting by rememberSaveable(selected) { mutableStateOf(false) }
    var selection by rememberSaveable(selected) { mutableStateOf(emptyList<String>()) }
    val selectionIds = remember(selection) { selection.toHashSet() }
    val retryFilter = DownloadFilter.entries.firstOrNull { it.label == selected } ?: DownloadFilter.Active
    val retryable = retryFilter == DownloadFilter.Failed || retryFilter == DownloadFilter.Cancelled
    BackHandler(enabled = selecting && !retryBusy) { selecting = false; selection = emptyList() }
    var handledQueueRequest by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(queueRequest) {
        if (queueRequest != handledQueueRequest) {
            selected = "Em andamento"
            handledQueueRequest = queueRequest
        }
    }
    val catalog = catalogViewModel(repository)
    LaunchedEffect(selected) { catalog.downloadFilter.value = DownloadFilter.entries.firstOrNull { it.label == selected } ?: DownloadFilter.Active }
    val entries = catalog.downloads.collectAsLazyPagingItems()
    val counts by catalog.counts.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
        item(key = "title") { Column { PageTitle("Downloads", "Seu conteúdo, pronto para sair com você.") } }
        item(key = "filters") { Box(Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState())) { FilterPills(DownloadFilter.entries.map { it.label }, selected) { selected = it } } }
        if (retryable && (entries.itemCount > 0 || retryBusy)) item(key = "retry-actions") {
            if (!selecting) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = !retryBusy, modifier = Modifier.weight(1f), onClick = { onRetryMany(retryFilter, null) }) {
                    Text(if (retryBusy) "Adicionando à fila…" else "Tentar todos novamente")
                }
                TextButton(enabled = !retryBusy, onClick = { selecting = true; selection = emptyList() }) { Text("Selecionar") }
            } else Text("Toque nos itens que deseja tentar novamente.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (retryBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        if (entries.itemCount == 0 && entries.loadState.refresh !is LoadState.Loading) item(key = "empty") {
            EmptyState(if (selected == "Concluídos") SpixIcons.Offline else SpixIcons.Download,
                when (selected) { "Com erro" -> "Nenhum download com erro"; "Cancelados" -> "Nenhum download cancelado"; "Concluídos" -> "Nenhum download concluído"; else -> "Nenhum download em andamento" },
                if (selected == "Com erro") "As falhas ficam aqui para tentar novamente quando quiser." else "Adicione um vídeo ou uma playlist pela tela inicial.")
        }
        if (selected == "Concluídos" && counts.completed > 0) item(key = "completed-label") { QueueLabel("CONCLUÍDOS · ${counts.completed}") }
        if (selected == "Em andamento" && counts.active > 0) item(key = "cancel-all") {
            OutlinedButton(onClick = { cancellingAll = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(SpixIcons.Close, null); Spacer(Modifier.width(8.dp)); Text("Cancelar todos (${counts.active})")
            }
        }
        items(entries.itemCount, key = entries.itemKey { it.videoId }, contentType = entries.itemContentType { "download" }) { index ->
            entries[index]?.let { request ->
                LiveDownloadCard(request, repository, { onRetry(request.videoId) }, { onCancel(request.videoId) }, { removing = request }, onLibrary,
                    selecting = selecting && retryable, checked = request.videoId in selectionIds, retryEnabled = !retryBusy,
                    onSelected = { checked -> selection = if (checked) (selection + request.videoId).distinct() else selection - request.videoId })
            }
        }
    }
    if (selecting && retryable) Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${selection.size} ${if (selection.size == 1) "selecionado" else "selecionados"}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(enabled = !retryBusy, onClick = { selecting = false; selection = emptyList() }) { Text("Cancelar seleção") }
            }
            Button(enabled = selection.isNotEmpty() && !retryBusy, modifier = Modifier.fillMaxWidth(), onClick = { onRetryMany(retryFilter, selection.toList()) }) {
                Text(if (retryBusy) "Adicionando à fila…" else "Tentar selecionados novamente (${selection.size})")
            }
        }
    }
    }
    if (cancellingAll) AlertDialog(onDismissRequest = { cancellingAll = false }, title = { Text("Cancelar todos os downloads?") },
        text = { Text("A transferência atual e os vídeos na fila serão cancelados. Os arquivos já baixados serão mantidos.") },
        confirmButton = { TextButton(onClick = { cancellingAll = false; onCancelAll() }) { Text("Cancelar downloads") } },
        dismissButton = { TextButton(onClick = { cancellingAll = false }) { Text("Voltar") } })

    removing?.let { request ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text(if (request.localUri != null) "Excluir do celular?" else "Excluir item?") },
            text = { Text(if (request.localUri != null) "O vídeo será removido da biblioteca, das playlists e do armazenamento do celular." else "Este item será removido dos downloads.") },
            confirmButton = { TextButton(onClick = { onRemove(request.videoId); removing = null }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancelar") } })
    }
}

@Composable
private fun QueueLabel(label: String) { Text(label, Modifier.padding(top = 8.dp), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
private fun LiveDownloadCard(request: DownloadRequest, repository: SpixRepository, onRetry: () -> Unit, onCancel: () -> Unit, onRemove: () -> Unit, onLibrary: () -> Unit, selecting: Boolean, checked: Boolean, retryEnabled: Boolean, onSelected: (Boolean) -> Unit) {
    val flow = remember(repository, request.videoId) { repository.liveProgress.map { it?.takeIf { live -> live.videoId == request.videoId } }.distinctUntilChanged() }
    val current by flow.collectAsStateWithLifecycle(initialValue = null)
    DownloadCard(current ?: request, onRetry, onCancel, onRemove, onLibrary, selecting, checked, retryEnabled, onSelected)
}

@Composable
private fun DownloadCard(request: DownloadRequest, onRetry: () -> Unit, onCancel: () -> Unit, onRemove: () -> Unit, onLibrary: () -> Unit, selecting: Boolean, checked: Boolean, retryEnabled: Boolean, onSelected: (Boolean) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    val active = request.status.isActiveDownload()
    val failed = request.status == DownloadStatus.Failed || request.status == DownloadStatus.Cancelled
    val title = request.metadata?.title ?: "Vídeo · ${request.videoId}"
    if (showDetails && request.localUri != null) TrackOptionsSheet(
        Track(request.videoId, title, request.metadata?.author.orEmpty(), request.duration, request.localUri, thumbnailUrl = request.metadata?.thumbnailUrl),
        { showDetails = false })
    Surface(modifier = Modifier.clip(RoundedCornerShape(12.dp)).then(
        if (selecting) Modifier.toggleable(value = checked, enabled = retryEnabled, role = Role.Checkbox, onValueChange = onSelected)
        else Modifier.playVideoOnClick(request.localUri.takeIf { request.status == DownloadStatus.Completed })),
        shape = RoundedCornerShape(12.dp), contentColor = MaterialTheme.colorScheme.onSurface, color = if (selecting && checked) MaterialTheme.colorScheme.primaryContainer else if (active || request.status == DownloadStatus.Completed) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VideoThumbnail(request.metadata?.thumbnailUrl, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (request.status == DownloadStatus.Completed) listOfNotNull(request.metadata?.author, request.duration.takeIf { it.isNotBlank() }).joinToString(" · ")
                        else downloadStageLabel(request.status, request.transfer, request.progress), fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (selecting) Box(Modifier.padding(start = 12.dp).size(48.dp), contentAlignment = Alignment.Center) {
                    Checkbox(checked, onCheckedChange = null, enabled = retryEnabled)
                }
                when {
                    active -> IconButton(onClick = onCancel) { Icon(SpixIcons.Close, "Cancelar download", Modifier.size(22.dp)) }
                    failed && !selecting -> IconButton(onClick = onRetry, enabled = retryEnabled) { Icon(SpixIcons.Play, "Tentar novamente", Modifier.size(22.dp)) }
                    else -> Unit
                }
                if (!active && !selecting) Box {
                    IconButton(onClick = { if (request.status == DownloadStatus.Completed && request.localUri != null) showDetails = true else menu = true }, Modifier.size(48.dp)) { Icon(SpixIcons.MoreVertical, "Mais opções de $title", Modifier.size(20.dp)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Excluir item") }, onClick = { menu = false; onRemove() }, leadingIcon = { Icon(SpixIcons.Trash, null) })
                    }
                }
            }
            if (request.status == DownloadStatus.Completed) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Download concluído", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
                Text("Já está na sua biblioteca para ouvir offline.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onLibrary, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = com.pedroaba.spix.ui.theme.SpixColors.Red, contentColor = androidx.compose.ui.graphics.Color.White)) {
                    Text("Ver na biblioteca", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (active) {
                if (request.status == DownloadStatus.Downloading && request.transfer != null) DownloadTransferDetails(request.transfer)
                else DownloadBar(request)
                if (request.status == DownloadStatus.Queued) TextButton(onClick = onRetry) { Text("Iniciar") }
            }
            if (failed) {
                Text(request.error ?: "Você pode iniciar uma nova tentativa quando quiser.", fontSize = 13.sp, color = if (request.status == DownloadStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                if (!selecting) SecondaryAction("Tentar novamente", onRetry, enabled = retryEnabled)
            }
        }
    }
}
