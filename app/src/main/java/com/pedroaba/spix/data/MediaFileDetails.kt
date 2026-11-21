package com.pedroaba.spix.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.format.Formatter
import com.pedroaba.spix.domain.DownloadRequest
import com.pedroaba.spix.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

data class MediaFileDetails(val rows: List<Pair<String, String>>, val unavailable: Boolean)

/** Reads the saved file on IO; missing metadata never becomes an invented value. */
suspend fun readMediaFileDetails(context: Context, track: Track, request: DownloadRequest?): MediaFileDetails = withContext(Dispatchers.IO) {
    val rows = linkedMapOf<String, String>()
    val uri = Uri.parse(track.localUri)
    var readable = false
    var size: Long? = null
    var name: String? = null
    fun put(label: String, value: String?) { if (!value.isNullOrBlank()) rows[label] = value }
    if (uri.scheme == "file") {
        val file = File(requireNotNull(uri.path))
        readable = file.isFile
        name = file.name
        size = file.takeIf { it.isFile }?.length()
        put("Pasta", file.parent)
        if (file.exists()) put("Modificado em", DateFormat.getDateTimeInstance().format(Date(file.lastModified())))
    } else {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    readable = true
                    name = c.getString(0)
                    size = if (c.isNull(1)) null else c.getLong(1).takeIf { it >= 0 }
                }
            }
        }
        if (uri.authority == MediaStore.AUTHORITY) runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DATE_MODIFIED), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    put("Pasta", c.getString(0))
                    if (!c.isNull(1)) put("Modificado em", DateFormat.getDateTimeInstance().format(Date(c.getLong(1) * 1000)))
                }
            }
        }
        if (size == null) runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { d ->
            readable = true
            size = d.length.takeIf { it >= 0 } ?: d.parcelFileDescriptor.statSize.takeIf { it >= 0 }
        } }
    }
    put("Arquivo", name)
    put("Tamanho no celular", size?.let { "${Formatter.formatFileSize(context, it)} (${NumberFormat.getIntegerInstance().format(it)} bytes)" } ?: "Não foi possível consultar")
    put("Formato", runCatching { context.contentResolver.getType(uri) }.getOrNull())
    put("Duração", track.duration)
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        readable = true
        fun metadata(key: Int) = retriever.extractMetadata(key)
        val ms = metadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        if (ms != null) put("Duração", "${ms / 60000}:${(ms / 1000 % 60).toString().padStart(2, '0')} · ${ms} ms")
        val width = metadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val height = metadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        if (width != null && height != null) put("Resolução", "$width × $height px")
        put("Quadros por segundo", metadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE))
        put("Taxa de bits", metadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { "${it / 1000} kbps" })
        put("Título no arquivo", metadata(MediaMetadataRetriever.METADATA_KEY_TITLE))
        put("Artista no arquivo", metadata(MediaMetadataRetriever.METADATA_KEY_ARTIST))
        put("Álbum", metadata(MediaMetadataRetriever.METADATA_KEY_ALBUM))
        put("Gênero", metadata(MediaMetadataRetriever.METADATA_KEY_GENRE))
        put("Ano", metadata(MediaMetadataRetriever.METADATA_KEY_YEAR))
        put("Formato", metadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE))
    } catch (_: Exception) { /* Keep filesystem details even when the container has no readable tags. */ }
    finally { runCatching { retriever.release() } }
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(context, uri, null)
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) {
                put("Codec de vídeo", mime)
                if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) put("Quadros por segundo", runCatching { format.getInteger(MediaFormat.KEY_FRAME_RATE).toString() }.getOrElse { format.getFloat(MediaFormat.KEY_FRAME_RATE).toString() })
            }
            if (mime.startsWith("audio/")) {
                put("Codec de áudio", mime)
                if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) put("Amostragem", "${format.getInteger(MediaFormat.KEY_SAMPLE_RATE)} Hz")
                if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) put("Canais de áudio", format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).toString())
            }
        }
    } catch (_: Exception) { }
    finally { extractor.release() }
    request?.let {
        put("Adicionado em", DateFormat.getDateTimeInstance().format(Date(it.addedAt)))
        put("Link original", it.url)
    }
    put("ID do vídeo", track.id)
    put("Local do arquivo", track.localUri)
    val priority = listOf("Tamanho no celular", "Arquivo", "Formato", "Duração", "Resolução", "Quadros por segundo", "Taxa de bits", "Codec de vídeo", "Codec de áudio", "Amostragem", "Canais de áudio")
    MediaFileDetails(rows.toList().sortedBy { (label, _) -> priority.indexOf(label).takeIf { it >= 0 } ?: priority.size }, !readable)
}
