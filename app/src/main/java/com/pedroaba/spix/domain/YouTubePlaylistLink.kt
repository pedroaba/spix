package com.pedroaba.spix.domain

import java.net.URI

/** A list parameter takes precedence over a video ID, including shared watch URLs. */
@JvmInline
value class YouTubePlaylistLink private constructor(val playlistId: String) {
    val url: String get() = "https://www.youtube.com/playlist?list=$playlistId"

    companion object {
        private val idPattern = Regex("[A-Za-z0-9_-]{2,150}")
        internal fun fromId(id: String): YouTubePlaylistLink? =
            id.takeIf(idPattern::matches)?.let(::YouTubePlaylistLink)
        fun parse(input: String): YouTubePlaylistLink? = runCatching {
            val uri = URI(input.trim())
            if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.userInfo != null) return null
            val host = uri.host?.lowercase() ?: return null
            val path = uri.path.orEmpty()
            val supported = when (host) {
                "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" ->
                    path == "/playlist" || path == "/watch"
                "youtu.be" -> YouTubeLink.parse(input) != null
                else -> false
            }
            if (!supported) return null
            uri.rawQuery?.split('&')?.firstOrNull { it.substringBefore('=') == "list" }
                ?.substringAfter('=')?.let(::fromId)
        }.getOrNull()
    }
}
