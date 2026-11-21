package com.pedroaba.spix.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pedroaba.spix.domain.DownloadRequest
import com.pedroaba.spix.domain.DownloadStatus
import com.pedroaba.spix.ui.components.*

@Composable
fun DownloadsScreen(requests: List<DownloadRequest>, onRemove: (String) -> Unit, onRetry: (String) -> Unit, onCancel: (String) -> Unit) {
    var removing by remember { mutableStateOf<DownloadRequest?>(null) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageTitle("Downloads", "Seu conteúdo, pronto para sair com você.") }
        if (requests.isEmpty()) item {
            EmptyState(SpixIcons.Download, "Nenhum download", "Adicione um vídeo pela tela inicial para baixar.")
        }
        items(requests, key = { it.videoId }) { request ->
            val active = request.status in setOf(DownloadStatus.Queued, DownloadStatus.Downloading, DownloadStatus.Saving)
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VideoThumbnail(request.metadata?.thumbnailUrl, Modifier.size(64.dp, 48.dp).clip(RoundedCornerShape(8.dp)))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(request.metadata?.title ?: "Vídeo · ${request.videoId}", fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            request.metadata?.author?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    Text(when (request.status) {
                        DownloadStatus.Queued -> "Na fila · aguardando conexão ou início"
                        DownloadStatus.Downloading -> if (request.progress == 0) "Preparando download…" else "Baixando · ${request.progress}%"
                        DownloadStatus.Saving -> "Salvando no dispositivo…"
                        DownloadStatus.Completed -> "Concluído · disponível offline"
                        DownloadStatus.Cancelled -> "Download cancelado"
                        DownloadStatus.Failed -> request.error ?: "Não foi possível baixar. Tente novamente."
                    }, fontSize = 13.sp, color = if (request.status == DownloadStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (active) {
                        if (request.status == DownloadStatus.Downloading && request.progress > 0)
                            LinearProgressIndicator(progress = { request.progress / 100f }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        if (active) {
                            if (request.status == DownloadStatus.Queued) TextButton(onClick = { onRetry(request.videoId) }) { Text("Iniciar") }
                            TextButton(onClick = { onCancel(request.videoId) }) { Text("Cancelar") }
                        } else {
                            if (request.status == DownloadStatus.Completed && request.localUri != null) OpenVideoButton(request.localUri)
                            else TextButton(onClick = { onRetry(request.videoId) }) { Text("Tentar novamente") }
                            IconButton(onClick = { removing = request }) { Icon(SpixIcons.Trash, "Remover da lista") }
                        }
                    }
                }
            }
        }
    }
    removing?.let { request ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text("Remover da lista?") },
            text = { Text("Arquivos já baixados permanecem na pasta do celular.") },
            confirmButton = { TextButton(onClick = { onRemove(request.videoId); removing = null }) { Text("Remover") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancelar") } })
    }
}
