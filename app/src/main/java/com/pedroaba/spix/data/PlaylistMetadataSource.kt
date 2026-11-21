package com.pedroaba.spix.data

import com.pedroaba.spix.domain.PlaylistMetadata
import com.pedroaba.spix.domain.YouTubePlaylistLink

fun interface PlaylistMetadataSource {
    suspend fun fetchPlaylist(link: YouTubePlaylistLink): PlaylistMetadata
    suspend fun fetchForSync(link: YouTubePlaylistLink): PlaylistMetadata = fetchPlaylist(link)
}

internal class PlaylistUnavailableException(message: String, cause: Throwable? = null) : java.io.IOException(message, cause)
