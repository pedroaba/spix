package com.pedroaba.spix

import android.app.Application
import okio.Path.Companion.toOkioPath
import kotlinx.coroutines.launch
import com.pedroaba.spix.data.LocalSpixRepository
import com.pedroaba.spix.data.SettingsRepository
import com.pedroaba.spix.data.VideoStorage
import com.pedroaba.spix.data.YouTubeMetadataRepository

class SpixApplication : Application(), coil3.SingletonImageLoader.Factory {
    val processScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        if (!packageName.endsWith(".benchmark")) {
            processScope.launchSafely { repository.awaitReady(); downloadScheduler.bootstrap() }
            processScope.launchSafely { com.pedroaba.spix.download.PlaylistSyncSchedule(this@SpixApplication, localPlaylists).observePreferences() }
        }
    }
    override fun newImageLoader(context: android.content.Context): coil3.ImageLoader = coil3.ImageLoader.Builder(context)
        .memoryCache { coil3.memory.MemoryCache.Builder().maxSizeBytes(16L * 1024 * 1024).build() }
        .diskCache { coil3.disk.DiskCache.Builder().directory(java.io.File(context.cacheDir, "artwork-cache").toOkioPath()).maxSizeBytes(128L * 1024 * 1024).build() }
        .fetcherCoroutineContext(kotlinx.coroutines.Dispatchers.IO.limitedParallelism(2))
        .decoderCoroutineContext(kotlinx.coroutines.Dispatchers.IO.limitedParallelism(1))
        .build()

    val downloader by lazy { com.pedroaba.spix.download.YouTubeDownloader(this) }
    val downloadScheduler by lazy { com.pedroaba.spix.download.WorkDownloadScheduler(this, repository) }
    val localPlaylists by lazy { com.pedroaba.spix.data.LocalPlaylistRepository(repository) }
    val playlistSync by lazy {
        val network = com.pedroaba.spix.download.DownloadNetwork(this)
        com.pedroaba.spix.download.PlaylistSync(
            localPlaylists, downloader, network::isOnline, downloadScheduler::wakeQueuedDownloads,
            prepareDownloads = downloadScheduler::awaitStartup,
        )
    }
    val repository by lazy { LocalSpixRepository(this) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val videoStorage by lazy { VideoStorage(this, settingsRepository) }
    val metadataRepository by lazy { YouTubeMetadataRepository() }
}

private fun kotlinx.coroutines.CoroutineScope.launchSafely(block: suspend () -> Unit) = launch {
    try { block() } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (failure: Exception) { android.util.Log.w("SpixStorage", "Initialization retained original data", failure) }
}
