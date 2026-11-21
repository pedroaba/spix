package com.pedroaba.spix.domain

import java.net.URI

@JvmInline
value class YouTubeLink private constructor(val videoId: String) {
    val url: String get() = "https://www.youtube.com/watch?v=$videoId"

    companion object {
        private val idPattern = Regex("[A-Za-z0-9_-]{11}")
        fun parse(input: String): YouTubeLink? = runCatching {
            val uri = URI(input.trim())
            if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.userInfo != null) return null
            val host = uri.host?.lowercase() ?: return null
            val parts = uri.path.orEmpty().trim('/').split('/')
            val id = when (host) {
                "youtu.be" -> parts.singleOrNull()
                "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" -> when {
                    uri.path == "/watch" -> uri.rawQuery?.split('&')
                        ?.firstOrNull { it.substringBefore('=') == "v" }?.substringAfter('=')
                    parts.size == 2 && parts[0] in setOf("shorts", "embed", "live") -> parts[1]
                    else -> null
                }
                else -> null
            }
            id?.takeIf(idPattern::matches)?.let(::YouTubeLink)
        }.getOrNull()
    }
}
