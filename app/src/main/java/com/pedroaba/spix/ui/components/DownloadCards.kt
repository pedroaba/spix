package com.pedroaba.spix.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pedroaba.spix.domain.*
import com.pedroaba.spix.download.downloadStageLabel
import com.pedroaba.spix.ui.theme.SpixColors

internal fun DownloadStatus.isActiveDownload() = this in setOf(DownloadStatus.Queued, DownloadStatus.Preparing, DownloadStatus.Downloading, DownloadStatus.Processing, DownloadStatus.Saving)

@Composable
fun DownloadBar(request: DownloadRequest, modifier: Modifier = Modifier) {
    val percent = if (request.status == DownloadStatus.Downloading) request.transfer?.percent
        ?: request.progress.takeIf { request.transfer == null && it > 0 } else null
    if (percent != null) LinearProgressIndicator(progress = { percent / 100f }, modifier = modifier.fillMaxWidth().height(3.dp),
        color = SpixColors.Red, trackColor = MaterialTheme.colorScheme.outline, gapSize = 0.dp, drawStopIndicator = {})
    else LinearProgressIndicator(modifier.fillMaxWidth().height(3.dp), color = SpixColors.Red, trackColor = MaterialTheme.colorScheme.outline)
}

@Composable
fun HomeDownloadProgress(request: DownloadRequest, count: Int, onOpen: () -> Unit, onCancel: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), contentColor = MaterialTheme.colorScheme.onSurface, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Ver downloads", onClick = onOpen).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            VideoThumbnail(request.metadata?.thumbnailUrl, Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(request.metadata?.title ?: "Vídeo do YouTube", fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(downloadStageLabel(request.status, request.transfer, request.progress), fontSize = 13.sp, lineHeight = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DownloadBar(request, Modifier.padding(top = 3.dp))
                if (count > 1) Text("$count downloads na fila", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onCancel, Modifier.size(48.dp)) { Icon(SpixIcons.Close, "Cancelar download", Modifier.size(22.dp)) }
        }
    }
}
