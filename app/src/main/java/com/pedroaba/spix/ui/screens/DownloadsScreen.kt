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
import com.pedroaba.spix.ui.components.*

@Composable
fun DownloadsScreen(requests: List<DownloadRequest>, onRemove: (String) -> Unit) {
    var removing by remember { mutableStateOf<DownloadRequest?>(null) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageTitle("Downloads", "Seu conteúdo, pronto para sair com você.") }
        item {
            Text("Os downloads ainda não estão disponíveis nesta versão. Seus links ficam salvos aqui para baixar depois.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 22.sp)
        }
        if (requests.isEmpty()) item {
            EmptyState(SpixIcons.Download, "Nenhum link na fila", "Adicione um link pela tela inicial. Seus pedidos aparecerão aqui.")
        }
        items(requests, key = { it.videoId }) { request ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (request.metadata != null) VideoThumbnail(request.metadata.thumbnailUrl, Modifier.size(64.dp, 48.dp).clip(RoundedCornerShape(8.dp)))
                    else Icon(SpixIcons.Clock, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(request.metadata?.title ?: "Vídeo · ${request.videoId}", fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        request.metadata?.author?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text("Link salvo para baixar depois", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { removing = request }) { Icon(SpixIcons.Trash, "Remover link da fila") }
                }
            }
        }
    }
    removing?.let { request ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text("Remover este link?") },
            text = { Text("Você pode adicioná-lo novamente quando quiser.") },
            confirmButton = { TextButton(onClick = { onRemove(request.videoId); removing = null }) { Text("Remover") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancelar") } })
    }
}
