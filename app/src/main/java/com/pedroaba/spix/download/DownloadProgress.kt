package com.pedroaba.spix.download

import com.pedroaba.spix.domain.DownloadStatus
import com.pedroaba.spix.domain.DownloadTransfer
import com.pedroaba.spix.domain.TransferKind
import org.json.JSONObject

internal sealed interface DownloadUpdate {
    data object Preparing : DownloadUpdate
    data class Transfer(val data: DownloadTransfer, val finished: Boolean = false) : DownloadUpdate
    data object Processing : DownloadUpdate
}

/** Machine-readable hooks avoid depending on terminal colors, locale, percentage text or ETA format. */
internal object DownloadProgressParser {
    const val PREFIX = "SPIX_TRANSFER:"
    const val PROCESSING_PREFIX = "SPIX_PROCESS:"
    const val DOWNLOAD_TEMPLATE = """download:SPIX_TRANSFER:{"status":%(progress.status)j,"downloaded":%(progress.downloaded_bytes)j,"total":%(progress.total_bytes)j,"estimate":%(progress.total_bytes_estimate)j,"speed":%(progress.speed)j,"eta":%(progress.eta)j,"vcodec":%(info.vcodec)j,"acodec":%(info.acodec)j}"""
    const val PROCESSING_TEMPLATE = "postprocess:SPIX_PROCESS:%(progress.status)s"

    fun parse(line: String): DownloadUpdate? {
        val text = line.trim()
        if (text.startsWith(PROCESSING_PREFIX)) return DownloadUpdate.Processing
        if (!text.startsWith(PREFIX)) return null
        return runCatching {
            val data = JSONObject(text.removePrefix(PREFIX))
            if (data.optString("status") !in setOf("downloading", "finished")) return null
            val downloaded = data.number("downloaded")?.takeIf { it >= 0 }?.toLong() ?: return null
            val exact = data.number("total")?.takeIf { it > 0 }?.toLong()
            val estimate = data.number("estimate")?.takeIf { it > 0 }?.toLong()
            val video = data.optString("vcodec").isCodec()
            val audio = data.optString("acodec").isCodec()
            DownloadUpdate.Transfer(DownloadTransfer(
                downloadedBytes = downloaded,
                totalBytes = exact ?: estimate,
                totalIsEstimate = exact == null && estimate != null,
                speedBytesPerSecond = data.number("speed")?.takeIf { it > 0 },
                etaSeconds = data.number("eta")?.takeIf { it >= 0 }?.let { kotlin.math.ceil(it).toLong() },
                kind = when {
                    video && !audio -> TransferKind.Video
                    audio && !video -> TransferKind.Audio
                    else -> TransferKind.Media
                },
            ), finished = data.optString("status") == "finished")
        }.getOrNull()
    }

    private fun JSONObject.number(key: String): Double? = (opt(key) as? Number)?.toDouble()?.takeIf { it.isFinite() }
    private fun String.isCodec() = isNotBlank() && lowercase() !in setOf("none", "na", "null")
}

/** Always publish the first event, changes of stage/stream and the final bytes, even inside the interval. */
internal class ProgressUpdateGate(private val intervalMillis: Long = 400) {
    private var previous: DownloadUpdate? = null
    private var lastTime = 0L

    fun accept(update: DownloadUpdate, now: Long): Boolean {
        if (update == previous) return false
        val old = previous
        val urgent = old == null || old::class != update::class ||
            (old is DownloadUpdate.Transfer && update is DownloadUpdate.Transfer &&
                (old.data.kind != update.data.kind || (update.finished && !old.finished) ||
                    (update.data.percent == 100 && old.data.percent != 100)))
        if (!urgent && now - lastTime < intervalMillis) return false
        previous = update
        lastTime = now
        return true
    }
}

internal fun downloadStageLabel(status: DownloadStatus, transfer: DownloadTransfer?, legacyPercent: Int = 0): String = when (status) {
    DownloadStatus.Queued -> "Na fila · aguardando conexão ou início"
    DownloadStatus.Preparing -> "Preparando download…"
    DownloadStatus.Downloading -> {
        val media = when (transfer?.kind) {
            TransferKind.Video -> "vídeo"
            TransferKind.Audio -> "áudio"
            else -> "mídia"
        }
        val percent = transfer?.percent ?: legacyPercent.takeIf { transfer == null && it > 0 }
        "Baixando $media" + if (percent != null) " · ${if (transfer?.totalIsEstimate == true) "≈" else ""}$percent%" else "…"
    }
    DownloadStatus.Processing -> "Finalizando arquivo…"
    DownloadStatus.Saving -> "Salvando no dispositivo…"
    DownloadStatus.Completed -> "Concluído · disponível offline"
    DownloadStatus.Cancelled -> "Download cancelado"
    DownloadStatus.Failed -> "Não foi possível baixar. Tente novamente."
}
