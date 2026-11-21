package com.pedroaba.spix.download

import android.content.Context
import com.pedroaba.spix.R
import com.pedroaba.spix.data.PlaylistMetadataSource
import com.pedroaba.spix.data.PlaylistUnavailableException
import com.pedroaba.spix.data.parsePlaylist
import com.pedroaba.spix.domain.PlaylistMetadata
import com.pedroaba.spix.domain.YouTubePlaylistLink
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class YouTubeDownloader(private val context: Context) : PlaylistMetadataSource {
    private val gate = Mutex()

    internal suspend fun download(url: String, directory: File, processId: String, progress: (DownloadUpdate) -> Unit): File = gate.withLock {
        progress(DownloadUpdate.Preparing)
        initialize()
        coroutineScope {
            val execution = async(Dispatchers.IO) {
                check(directory.isDirectory || directory.mkdirs())
                val request = YoutubeDLRequest(url).apply {
                    addOption("--no-playlist")
                    addOption("--newline")
                    addOption("--progress")
                    addOption("--progress-delta", "0.25")
                    addOption("--no-colors")
                    addOption("--output-na-placeholder", "null")
                    addOption("--progress-template", DownloadProgressParser.DOWNLOAD_TEMPLATE)
                    addOption("--progress-template", DownloadProgressParser.PROCESSING_TEMPLATE)
                    addOption("--ignore-config")
                    addOption("--no-mtime")
                    addOption("--socket-timeout", 10)
                    addOption("--retries", 1)
                    addOption("--fragment-retries", 1)
                    addOption("--max-filesize", "2G")
                    addOption("--match-filters", "!is_live")
                    addOption("-f", "bv*[height<=720][ext=mp4]+ba[ext=m4a]/b[height<=720][ext=mp4]/b[height<=720]")
                    addOption("--merge-output-format", "mp4")
                    addOption("--remux-video", "mp4")
                    addOption("-o", File(directory, "video.%(ext)s").absolutePath)
                }
                // Both output channels must reach the callback; ignore the wrapper's terminal-text percentage parser.
                val lastError = AtomicReference<String?>(null)
                try {
                    YoutubeDL.getInstance().execute(request, processId, redirectErrorStream = true) { _, _, line ->
                        if (line.trimStart().startsWith("ERROR:")) lastError.set(line.take(1200))
                        DownloadProgressParser.parse(line)?.let(progress)
                    }
                } catch (error: YoutubeDLException) {
                    // The wrapper reads its error message from stderr, which is empty when streams are merged.
                    throw IOException(lastError.get() ?: error.message ?: "Falha no download.", error)
                }
                File(directory, "video.mp4").also {
                    check(it.isFile && it.length() > 0) { "O download não gerou um vídeo válido." }
                    check(it.length() <= 2L * 1024 * 1024 * 1024) { "max-filesize exceeded" }
                }
            }
            try { execution.await() }
            finally { finishProcess(processId, execution) }
        }
    }
    private suspend fun initialize() {
        withContext(Dispatchers.IO) {
            // Pin the extractor separately from the Android native runtime bundled by the wrapper.
            val extractor = File(context.noBackupFilesDir, "${YoutubeDL.baseName}/${YoutubeDL.ytdlpDirName}/${YoutubeDL.ytdlpBin}")
            val version = context.getSharedPreferences("spix_engine", Context.MODE_PRIVATE)
            if (version.getString("extractor", null) != "2026.10.26" || !extractor.isFile) {
                check(extractor.parentFile!!.isDirectory || extractor.parentFile!!.mkdirs())
                val temporary = File(extractor.parentFile, "yt-dlp.tmp")
                context.resources.openRawResource(R.raw.ytdlp).use { input -> temporary.outputStream().use { input.copyTo(it) } }
                check(temporary.renameTo(extractor))
                check(version.edit().putString("extractor", "2026.10.26").commit())
            }
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
        }
    }

    override suspend fun fetchPlaylist(link: YouTubePlaylistLink): PlaylistMetadata = fetchPlaylist(link, allowEmpty = false)

    override suspend fun fetchForSync(link: YouTubePlaylistLink): PlaylistMetadata = fetchPlaylist(link, allowEmpty = true)

    private suspend fun fetchPlaylist(link: YouTubePlaylistLink, allowEmpty: Boolean): PlaylistMetadata = gate.withLock {
        initialize()
        val processId = "playlist-${java.util.UUID.randomUUID()}"
        withTimeout(120_000) {
            coroutineScope {
                val execution = async(Dispatchers.IO) {
                    val request = YoutubeDLRequest(link.url).apply {
                        addOption("--ignore-config")
                        addOption("--flat-playlist")
                        addOption("--dump-single-json")
                        addOption("--yes-playlist")
                        addOption("--skip-download")
                        addOption("--no-warnings")
                        addOption("--socket-timeout", 10)
                        addOption("--retries", 1)
                        addOption("--extractor-retries", 1)
                    }
                    val response = try {
                        YoutubeDL.getInstance().execute(request, processId)
                    } catch (error: YoutubeDLException) {
                        val message = error.message.orEmpty().lowercase()
                        if (allowEmpty && listOf("playlist does not exist", "playlist is private", "playlist is unavailable",
                                "playlist is not available", "playlist not found", "this playlist is private").any(message::contains)) {
                            throw PlaylistUnavailableException("A playlist do YouTube está inacessível.", error)
                        }
                        throw error
                    }
                    parsePlaylist(link, response.out, allowEmpty)
                }
                try { execution.await() }
                finally { finishProcess(processId, execution) }
            }
        }
    }

    private suspend fun finishProcess(processId: String, execution: Job) {
        val cancelled = !currentCoroutineContext().isActive
        withContext(NonCancellable + Dispatchers.IO) {
            if (cancelled) {
                // execute() may register its native process just after cancellation.
                do {
                    YoutubeDL.getInstance().destroyProcessById(processId)
                    terminateNativeChildren()
                    if (!execution.isCompleted) delay(100)
                } while (!execution.isCompleted)
            } else terminateNativeChildren()
        }
    }

    /** The upstream cancellation helper may leave qjs/ffmpeg children orphaned.
     * The gate guarantees no other download owns these binaries while cleaning up. */
    private fun terminateNativeChildren() {
        val binaries = setOf("libpython.so", "libqjs.so", "libffmpeg.so", "libffprobe.so")
            .map { File(context.applicationInfo.nativeLibraryDir, it).absolutePath }.toSet()
        File("/proc").listFiles()?.forEach { process ->
            val pid = process.name.toIntOrNull() ?: return@forEach
            runCatching {
                val executable = File(process, "cmdline").readText().substringBefore('\u0000')
                if (executable in binaries) {
                    val uid = File(process, "status").readLines().first { it.startsWith("Uid:") }
                        .substringAfter(':').trim().split(Regex("\\s+"))[0].toInt()
                    if (uid == android.os.Process.myUid()) android.os.Process.killProcess(pid)
                }
            }
        }
    }

}
