package com.pedroaba.spix.data

import com.pedroaba.spix.domain.VideoMetadata
import com.pedroaba.spix.domain.YouTubeLink

fun interface VideoMetadataSource {
    suspend fun fetch(link: YouTubeLink): VideoMetadata
}
