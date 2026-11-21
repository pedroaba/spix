package com.pedroaba.spix.data

import com.pedroaba.spix.domain.*
import org.json.JSONArray

/** Fail the transaction on malformed input; the original preferences are retained. */
internal fun decodeLegacyRequests(source: String, allowPrivateArtwork: Boolean = false): List<DownloadRequest> {
        val json = JSONArray(source)
        return List(json.length()) { index ->
            val item = json.getJSONObject(index)
            val id = item.getString("id")
            val url = item.getString("url")
            val metadata = item.optJSONObject("metadata")?.let { data ->
                val title = data.optString("title").trim()
                val author = data.optString("author").trim()
                if (title.isBlank() || author.isBlank()) null else VideoMetadata(id, url, title, author,
                    data.optString("thumbnail").takeIf { it.startsWith("https://") || (allowPrivateArtwork && it.startsWith("file://")) })
            }
            DownloadRequest(id, url, item.getLong("at"), metadata,
                status = DownloadStatus.entries.firstOrNull { it.name == item.optString("status") } ?: DownloadStatus.Queued,
                progress = item.optInt("progress").coerceIn(0, 100),
                error = item.optString("error").takeIf { it.isNotBlank() && it != "null" },
                localUri = item.optString("localUri").takeIf { it.isNotBlank() && it != "null" },
                duration = item.optString("duration"),
                transfer = item.optJSONObject("transfer")?.let { data ->
                    DownloadTransfer(data.optLong("bytes").coerceAtLeast(0),
                        totalBytes = data.optLong("total").takeIf { it > 0 },
                        totalIsEstimate = data.optBoolean("estimate"),
                        speedBytesPerSecond = data.optDouble("speed").takeIf { it.isFinite() && it > 0 },
                        etaSeconds = if (data.has("eta") && !data.isNull("eta")) data.optLong("eta").takeIf { it >= 0 } else null,
                        kind = TransferKind.entries.firstOrNull { it.name == data.optString("kind") } ?: TransferKind.Media)
                })
        }
}
