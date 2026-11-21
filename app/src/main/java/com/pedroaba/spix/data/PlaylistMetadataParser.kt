package com.pedroaba.spix.data

import com.pedroaba.spix.domain.PlaylistMetadata
import com.pedroaba.spix.domain.VideoMetadata
import com.pedroaba.spix.domain.YouTubeLink
import com.pedroaba.spix.domain.YouTubePlaylistLink
import java.io.IOException
import org.json.JSONObject

internal fun parsePlaylist(link: YouTubePlaylistLink, json: String, allowEmpty: Boolean = false): PlaylistMetadata {
    val data = JSONObject(json)
    val entries = data.optJSONArray("entries") ?: throw IOException("Playlist indisponível.")
    val videos = linkedMapOf<String, VideoMetadata>()
    var unavailable = 0
    for (index in 0 until entries.length()) {
        val entry = entries.optJSONObject(index)
        val id = entry?.optString("id").orEmpty()
        val video = YouTubeLink.parse("https://www.youtube.com/watch?v=$id")
        val availability = entry?.text("availability")
        val title = entry?.text("title")
        if (video == null || availability in setOf("private", "premium_only", "subscriber_only", "needs_auth") ||
            title in setOf("[Private video]", "[Deleted video]")) {
            unavailable++
            continue
        }
        videos.putIfAbsent(id, VideoMetadata(id, video.url, title ?: "Vídeo · $id",
            entry?.text("channel") ?: entry?.text("uploader") ?: "YouTube",
            "https://i.ytimg.com/vi/$id/hqdefault.jpg"))
    }
    if (videos.isEmpty() && !allowEmpty) throw IOException("A playlist está vazia ou não contém vídeos públicos disponíveis.")
    return PlaylistMetadata(link.playlistId, data.text("title") ?: "Playlist do YouTube", videos.values.toList(), unavailable)
}

private fun JSONObject.text(key: String): String? = (opt(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }
