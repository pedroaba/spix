package com.pedroaba.spix.download

import android.content.Context
import com.pedroaba.spix.R
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class YouTubeDownloader(private val context: Context) {
    private val gate = Mutex()

    suspend fun download(url: String, directory: File, processId: String, progress: (Int) -> Unit): File = gate.withLock {
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
        coroutineScope {
            val execution = async(Dispatchers.IO) {
                check(directory.isDirectory || directory.mkdirs())
                val request = YoutubeDLRequest(url).apply {
                    addOption("--no-playlist")
                    addOption("--newline")
                    addOption("--progress")
                    addOption("--ignore-config")
                    addOption("--no-mtime")
                    addOption("--socket-timeout", 20)
                    addOption("--retries", 3)
                    addOption("--fragment-retries", 3)
                    addOption("--max-filesize", "2G")
                    addOption("--match-filters", "!is_live")
                    addOption("-f", "bv*[height<=720][ext=mp4]+ba[ext=m4a]/b[height<=720][ext=mp4]/b[height<=720]")
                    addOption("--merge-output-format", "mp4")
                    addOption("--remux-video", "mp4")
                    addOption("-o", File(directory, "video.%(ext)s").absolutePath)
                }
                YoutubeDL.getInstance().execute(request, processId) { value, _, _ ->
                    progress(value.toInt().coerceIn(0, 99))
                }
                File(directory, "video.mp4").also {
                    check(it.isFile && it.length() > 0) { "O download não gerou um vídeo válido." }
                    check(it.length() <= 2L * 1024 * 1024 * 1024) { "max-filesize exceeded" }
                }
            }
            try { execution.await() }
            finally {
                val cancelled = !currentCoroutineContext().isActive
                withContext(NonCancellable + Dispatchers.IO) {
                    if (cancelled) YoutubeDL.getInstance().destroyProcessById(processId)
                    terminateNativeChildren()
                }
            }
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
