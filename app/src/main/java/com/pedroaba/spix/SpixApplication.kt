package com.pedroaba.spix

import android.app.Application
import com.pedroaba.spix.data.LocalSpixRepository
import com.pedroaba.spix.data.SettingsRepository
import com.pedroaba.spix.data.VideoStorage
import com.pedroaba.spix.data.YouTubeMetadataRepository

class SpixApplication : Application() {
    val downloader by lazy { com.pedroaba.spix.download.YouTubeDownloader(this) }
    val downloadScheduler by lazy { com.pedroaba.spix.download.WorkDownloadScheduler(this) }
    val repository by lazy { LocalSpixRepository(this) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val videoStorage by lazy { VideoStorage(this, settingsRepository) }
    val metadataRepository by lazy { YouTubeMetadataRepository() }
}
