package com.pedroaba.spix.data

import android.content.Context
import android.net.Uri
import java.io.File

/** Cleans up app-owned cover files from the former gallery-based implementation. */
object PlaylistCoverStore {
    fun delete(context: Context, source: String?) {
        if (source == null) return
        runCatching {
            val uri = Uri.parse(source)
            val directory = File(context.filesDir, "playlist-covers").canonicalFile
            if (uri.scheme == "file") {
                val file = File(requireNotNull(uri.path)).canonicalFile
                if (file.parentFile == directory) file.delete()
            }
        }
    }
}
