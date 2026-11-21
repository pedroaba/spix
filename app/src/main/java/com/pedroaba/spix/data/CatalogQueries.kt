package com.pedroaba.spix.data

import androidx.paging.PagingConfig
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.pedroaba.spix.domain.*

/** UI paging is bounded independently of the number of stored records. */
internal val catalogPagingConfig = PagingConfig(pageSize = 40, initialLoadSize = 80, prefetchDistance = 12, maxSize = 160, enablePlaceholders = false)
data class LibraryQuery(val search: String = "", val alphabetical: Boolean = false, val playlistId: String? = null, val searchOnly: Boolean = false)
internal fun librarySql(query: LibraryQuery, count: Boolean = false): SupportSQLiteQuery {
    val args = mutableListOf<Any>()
    val where = mutableListOf("m.localUri IS NOT NULL")
    if (query.searchOnly && query.search.isBlank()) where += "0"
    if (query.search.isNotBlank()) {
        where += "(m.searchTitle LIKE ? ESCAPE '\\' OR m.searchAuthor LIKE ? ESCAPE '\\')"
        val pattern = "%" + query.search.lowercase(java.util.Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        args += pattern; args += pattern
    }
    if (query.playlistId != null) { where += "m.videoId IN (SELECT videoId FROM playlist_members WHERE playlistId=?)"; args += query.playlistId }
    val order = when {
        query.playlistId != null -> { args += query.playlistId; "(SELECT position FROM playlist_members WHERE playlistId=? AND videoId=m.videoId), m.videoId" }
        query.alphabetical -> "m.searchTitle, m.videoId"
        else -> "m.addedAt DESC, m.videoId"
    }
    if (count && query.playlistId != null) args.removeAt(args.lastIndex)
    return SimpleSQLiteQuery("SELECT ${if (count) "COUNT(*)" else "m.*"} FROM media m WHERE ${where.joinToString(" AND ")}" + if (count) "" else " ORDER BY $order", args.toTypedArray())
}
enum class DownloadFilter(val label: String) {
    Active("Em andamento"), Failed("Com erro"), Completed("Concluídos"), Cancelled("Cancelados")
}
internal fun downloadsSql(completed: Boolean): SupportSQLiteQuery = downloadsSql(if (completed) DownloadFilter.Completed else DownloadFilter.Active)
internal fun downloadsSql(filter: DownloadFilter): SupportSQLiteQuery {
    val status = when (filter) {
        DownloadFilter.Active -> "IN ('Queued','Preparing','Downloading','Processing','Saving')"
        DownloadFilter.Failed -> "= 'Failed'"
        DownloadFilter.Completed -> "= 'Completed'"
        DownloadFilter.Cancelled -> "= 'Cancelled'"
    }
    return SimpleSQLiteQuery("SELECT m.*, j.status, j.progress, j.error, j.transfer FROM media m JOIN jobs j USING(videoId) WHERE j.status $status ORDER BY m.addedAt DESC, m.videoId")
}
internal fun planEnqueue(videos: List<VideoMetadata>, existing: Map<String, DownloadStatus>): List<VideoMetadata> =
    videos.distinctBy { it.videoId }.filter { existing[it.videoId] == null || existing[it.videoId] in setOf(DownloadStatus.Failed, DownloadStatus.Cancelled) }
