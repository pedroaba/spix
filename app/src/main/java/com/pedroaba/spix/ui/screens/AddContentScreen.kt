package com.pedroaba.spix.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pedroaba.spix.domain.VideoPreviewState
import com.pedroaba.spix.ui.components.*
import com.pedroaba.spix.ui.theme.SpixColors

@Composable
fun AddContentScreen(link: String, error: String?, busy: Boolean, preview: VideoPreviewState, motion: Boolean, location: String,
    playlistName: String, onPlaylistName: (String) -> Unit,
    onLink: (String) -> Unit, onRetry: () -> Unit, onBack: () -> Unit, onDownload: () -> Unit) {
    val playlist = (preview as? VideoPreviewState.PlaylistReady)?.playlist
    val ready = preview is VideoPreviewState.Ready || (playlist != null && playlistName.isNotBlank())
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !busy) { Icon(SpixIcons.Back, "Voltar") }
            Text("Adicionar conteúdo", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)) {
            item {
                Text(if (playlist != null) "Confira sua playlist" else "Pronto para levar com você", fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
                Text(if (playlist != null) "Revise os vídeos e altere o nome se quiser. Nada será baixado antes de confirmar." else "Confira o conteúdo antes de baixar.", Modifier.padding(top = 4.dp), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { LinkInput(link, onLink, error, !busy, onRetry) }
            item { VideoPreview(preview, motion, onRetry) }
            if (playlist != null) {
                item {
                    OutlinedTextField(playlistName, onPlaylistName, label = { Text("Nome da playlist") }, singleLine = true,
                        enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        supportingText = { Text("Você pode manter o nome original ou escolher outro.") })
                    Text("Vídeos da playlist · ${playlist.videos.size}", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                itemsIndexed(playlist.videos, key = { _, video -> video.videoId }) { index, video ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        VideoThumbnail(video.thumbnailUrl, Modifier.width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)))
                        Column(Modifier.weight(1f)) {
                            Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            Text(video.author, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Text(if (playlist != null) "A playlist será criada ao confirmar. Os vídeos ficam disponíveis offline conforme os downloads terminam; vídeos já baixados serão aproveitados." else "Disponível na sua biblioteca após o download.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Salvar em $location · até 720p com áudio.", Modifier.padding(top = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.padding(top = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onDownload, enabled = ready && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SpixColors.Red, contentColor = androidx.compose.ui.graphics.Color.White)) {
                Icon(SpixIcons.Download, null, Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
                Text(if (busy) "Confirmando…" else if (playlist != null) "Confirmar playlist e baixar" else "Baixar vídeo", fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = onBack, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Trocar link") }
        }
    }
}
