package com.pedroaba.spix.ui.components

import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pedroaba.spix.domain.DownloadTransfer

@Composable
fun DownloadTransferDetails(transfer: DownloadTransfer) {
    val context = LocalContext.current
    val downloaded = Formatter.formatShortFileSize(context, transfer.downloadedBytes)
    val amount = transfer.totalBytes?.let { total ->
        "$downloaded de ${if (transfer.totalIsEstimate) "≈ " else ""}${Formatter.formatShortFileSize(context, total)}"
    } ?: "$downloaded transferidos"
    val speed = transfer.speedBytesPerSecond?.let { "${Formatter.formatShortFileSize(context, it.toLong())}/s" }
    val eta = transfer.etaSeconds?.let { seconds ->
        when {
            seconds < 1 -> "menos de 1 s restante"
            seconds < 60 -> "$seconds s restantes"
            seconds < 3600 -> "${seconds / 60} min ${seconds % 60} s restantes"
            else -> "${seconds / 3600} h ${(seconds % 3600) / 60} min restantes"
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(amount, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val timing = listOfNotNull(speed, eta).joinToString(" · ")
        if (timing.isNotEmpty()) Text(timing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    key(transfer.kind) {
        if (transfer.percent != null) {
            val fraction = (transfer.downloadedBytes.toDouble() / requireNotNull(transfer.totalBytes)).toFloat().coerceIn(0f, 1f)
            val animated = animateFloatAsState(fraction, tween(250), label = "transferProgress")
            LinearProgressIndicator(progress = { if (fraction >= 1f) 1f else animated.value }, modifier = Modifier.fillMaxWidth())
        } else LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
