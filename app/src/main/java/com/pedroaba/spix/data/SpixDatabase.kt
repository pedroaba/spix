package com.pedroaba.spix.data

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "media", indices = [Index("addedAt"), Index("searchTitle"), Index("localUri")])
data class MediaRecord(
    @PrimaryKey val videoId: String, val url: String, val addedAt: Long,
    val title: String?, val author: String?, val thumbnailUrl: String?, val localUri: String?,
    val duration: String, val kind: String = "Video",
    val searchTitle: String = title.orEmpty().lowercase(java.util.Locale.ROOT),
    val searchAuthor: String = author.orEmpty().lowercase(java.util.Locale.ROOT),
)
@Entity(tableName = "jobs", foreignKeys = [ForeignKey(entity = MediaRecord::class, parentColumns = ["videoId"], childColumns = ["videoId"], onDelete = ForeignKey.CASCADE)], indices = [Index("status")])
data class JobRecord(@PrimaryKey val videoId: String, val status: String, val progress: Int = 0, val error: String? = null, val transfer: String? = null)
@Entity(tableName = "checkpoints", foreignKeys = [ForeignKey(entity = MediaRecord::class, parentColumns = ["videoId"], childColumns = ["videoId"], onDelete = ForeignKey.CASCADE)])
data class ProgressRecord(@PrimaryKey val videoId: String, val progress: Int, val transfer: String?)
data class DownloadSnapshot(@Embedded val media: MediaRecord, val status: String, val progress: Int, val error: String?, val transfer: String?)
@Entity(tableName = "playlists")
data class PlaylistRecord(@PrimaryKey val id: String, val name: String, val position: Long, val coverUri: String? = null)
@Entity(tableName = "playlist_members", primaryKeys = ["playlistId", "videoId"], indices = [Index("videoId")], foreignKeys = [ForeignKey(entity = PlaylistRecord::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE)])
data class PlaylistMemberRecord(val playlistId: String, val videoId: String, val position: Int)
@Entity(tableName = "playlist_sync", foreignKeys = [ForeignKey(entity = PlaylistRecord::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE)])
data class PlaylistSyncRecord(
    @PrimaryKey val playlistId: String,
    val generation: String,
    val automatic: Boolean,
    val lastSyncedAt: Long? = null,
)
@Entity(tableName = "playlist_sync_seen", primaryKeys = ["playlistId", "videoId"], foreignKeys = [ForeignKey(entity = PlaylistSyncRecord::class, parentColumns = ["playlistId"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE)])
data class PlaylistSyncSeenRecord(val playlistId: String, val videoId: String)
data class PlaylistCoverOption(val videoId: String, val title: String?, val thumbnailUrl: String?)
data class PlaylistSummary(val id: String, val name: String, val count: Int, val coverUri: String? = null, val total: Int = count)
@Entity(tableName = "download_batches", indices = [Index("groupId")], foreignKeys = [ForeignKey(entity = MediaRecord::class, parentColumns = ["videoId"], childColumns = ["videoId"], onDelete = ForeignKey.CASCADE)])
data class DownloadBatchRecord(@PrimaryKey val videoId: String, val groupId: String, val title: String, val itemIndex: Int, val total: Int, val createdAt: Long)
@Entity(tableName = "imports")
data class ImportRecord(@PrimaryKey val key: String)
data class CatalogCounts(val library: Int, val active: Int, val interrupted: Int, val completed: Int)

@Dao
interface CatalogDao {
    @RawQuery(observedEntities = [MediaRecord::class, PlaylistMemberRecord::class])
    fun libraryPages(query: SupportSQLiteQuery): PagingSource<Int, MediaRecord>
    @RawQuery(observedEntities = [MediaRecord::class, PlaylistMemberRecord::class])
    fun libraryCount(query: SupportSQLiteQuery): Flow<Int>
    @RawQuery(observedEntities = [MediaRecord::class, JobRecord::class])
    fun downloadPages(query: SupportSQLiteQuery): PagingSource<Int, DownloadSnapshot>
    @Query("SELECT m.*, j.status, j.progress, j.error, j.transfer FROM media m JOIN jobs j USING(videoId) WHERE m.videoId=:id")
    suspend fun get(id: String): DownloadSnapshot?
    @Query("SELECT m.*, j.status, j.progress, j.error, j.transfer FROM media m JOIN jobs j USING(videoId) WHERE m.localUri=:uri LIMIT 1")
    suspend fun getByUri(uri: String): DownloadSnapshot?
    @Query("SELECT * FROM checkpoints WHERE videoId=:id") suspend fun checkpoint(id: String): ProgressRecord?
    @Query("SELECT * FROM jobs WHERE videoId IN (:ids)") suspend fun statuses(ids: List<String>): List<JobRecord>
    @Query("SELECT m.*, j.status, j.progress, j.error, j.transfer FROM media m JOIN jobs j USING(videoId) WHERE j.status IN ('Queued','Preparing','Downloading','Processing','Saving') ORDER BY CASE WHEN j.status='Queued' THEN 1 ELSE 0 END, m.addedAt, m.videoId LIMIT 1")
    fun active(): Flow<DownloadSnapshot?>
    @Query("SELECT DISTINCT localUri FROM media WHERE localUri IS NOT NULL")
    suspend fun savedMediaUris(): List<String>
    @Query("SELECT videoId FROM media WHERE videoId IN (:ids) AND localUri IS NOT NULL")
    suspend fun savedMediaIds(ids: List<String>): List<String>
    @Query("UPDATE jobs SET status='Cancelled', progress=0, error=NULL, transfer=NULL WHERE status IN ('Queued','Preparing','Downloading','Processing','Saving')")
    suspend fun cancelActiveDownloads()
    @Query("DELETE FROM checkpoints WHERE videoId IN (SELECT videoId FROM jobs WHERE status='Cancelled')")
    suspend fun clearCancelledCheckpoints()
    @Query("SELECT m.videoId FROM media m JOIN jobs j USING(videoId) LEFT JOIN download_batches b ON b.videoId=m.videoId WHERE j.status='Queued' ORDER BY COALESCE(b.createdAt,m.addedAt), COALESCE(b.itemIndex,0), m.addedAt, m.videoId LIMIT 1")
    suspend fun nextQueued(): String?
    @Query("SELECT EXISTS(SELECT 1 FROM jobs WHERE status='Queued')") suspend fun hasQueued(): Boolean
    @Query("SELECT status FROM jobs WHERE videoId=:id") fun observeStatus(id: String): Flow<String?>
    @Query("SELECT (SELECT COUNT(*) FROM media WHERE localUri IS NOT NULL) AS library, COALESCE(SUM(CASE WHEN status IN ('Queued','Preparing','Downloading','Processing','Saving') THEN 1 ELSE 0 END), 0) AS active, COALESCE(SUM(CASE WHEN status IN ('Failed','Cancelled') THEN 1 ELSE 0 END), 0) AS interrupted, COALESCE(SUM(CASE WHEN status='Completed' THEN 1 ELSE 0 END), 0) AS completed FROM jobs")
    fun counts(): Flow<CatalogCounts>
    @Query("SELECT m.*, j.status, j.progress, j.error, j.transfer FROM media m JOIN jobs j USING(videoId) WHERE m.localUri IS NOT NULL AND j.status='Completed' ORDER BY m.addedAt DESC, m.videoId")
    suspend fun playbackQueue(): List<DownloadSnapshot>
    @Upsert suspend fun saveBatch(rows: List<DownloadBatchRecord>)
    @Query("DELETE FROM download_batches WHERE videoId IN (:ids)") suspend fun clearBatch(ids: List<String>)
    @Query("SELECT * FROM download_batches WHERE videoId=:id") suspend fun batch(id: String): DownloadBatchRecord?
    @Query("SELECT COUNT(*) FROM media") suspend fun mediaCount(): Int
    @Query("SELECT EXISTS(SELECT 1 FROM imports WHERE `key`=:key)") suspend fun imported(key: String): Boolean
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMedia(rows: List<MediaRecord>)
    @Update suspend fun updateMedia(row: MediaRecord)
    @Upsert suspend fun jobs(rows: List<JobRecord>)
    @Upsert suspend fun checkpoint(row: ProgressRecord)
    @Query("SELECT m.videoId FROM media m JOIN jobs j USING(videoId) WHERE j.status=:status AND m.localUri IS NULL ORDER BY m.addedAt, m.videoId")
    suspend fun retryIds(status: String): List<String>
    @Query("SELECT m.videoId FROM media m JOIN jobs j USING(videoId) WHERE j.status=:status AND m.localUri IS NULL AND m.videoId IN (:ids) ORDER BY m.addedAt, m.videoId")
    suspend fun selectedRetryIds(status: String, ids: List<String>): List<String>
    @Query("UPDATE jobs SET status='Queued', progress=0, error=NULL, transfer=NULL WHERE videoId IN (:ids) AND status=:status")
    suspend fun requeue(status: String, ids: List<String>)
    @Query("DELETE FROM checkpoints WHERE videoId IN (:ids)") suspend fun clearRetryCheckpoints(ids: List<String>)
    @Query("DELETE FROM checkpoints WHERE videoId=:id") suspend fun clearCheckpoint(id: String)
    @Insert suspend fun markImported(row: ImportRecord)
    @Query("UPDATE playlists SET coverUri=NULL WHERE coverUri=(SELECT thumbnailUrl FROM media WHERE videoId=:id)")
    suspend fun clearVideoCovers(id: String)
    @Query("UPDATE playlists SET coverUri=NULL WHERE id=:playlistId AND coverUri=(SELECT thumbnailUrl FROM media WHERE videoId=:videoId)")
    suspend fun clearMemberCover(playlistId: String, videoId: String)
    @Query("DELETE FROM playlist_members WHERE videoId=:id") suspend fun removeFromPlaylists(id: String)
    @Query("DELETE FROM media WHERE videoId=:id") suspend fun remove(id: String)
    @Query("UPDATE jobs SET status='Queued', progress=0, error=NULL, transfer=NULL WHERE status IN ('Preparing','Downloading','Processing','Saving')") suspend fun recoverRunning()
    @Query("SELECT p.id, p.name, COALESCE((SELECT chosen.thumbnailUrl FROM playlist_members chosenMember JOIN media chosen ON chosen.videoId=chosenMember.videoId WHERE chosenMember.playlistId=p.id AND chosen.thumbnailUrl=p.coverUri LIMIT 1), (SELECT v.thumbnailUrl FROM playlist_members pm JOIN media v ON v.videoId=pm.videoId WHERE pm.playlistId=p.id AND v.thumbnailUrl IS NOT NULL ORDER BY pm.position LIMIT 1)) AS coverUri, COUNT(m.videoId) AS count, COUNT(x.videoId) AS total FROM playlists p LEFT JOIN playlist_members x ON x.playlistId=p.id LEFT JOIN media m ON m.videoId=x.videoId AND m.localUri IS NOT NULL GROUP BY p.id ORDER BY p.position, p.id")
    fun playlistPages(): PagingSource<Int, PlaylistSummary>
    @Query("SELECT COALESCE((SELECT chosen.thumbnailUrl FROM playlist_members chosenMember JOIN media chosen ON chosen.videoId=chosenMember.videoId WHERE chosenMember.playlistId=p.id AND chosen.thumbnailUrl=p.coverUri LIMIT 1), (SELECT v.thumbnailUrl FROM playlist_members pm JOIN media v ON v.videoId=pm.videoId WHERE pm.playlistId=p.id AND v.thumbnailUrl IS NOT NULL ORDER BY pm.position LIMIT 1)) FROM playlists p WHERE p.id=:id")
    fun playlistArtwork(id: String): Flow<String?>
    @Query("SELECT videoId, title, thumbnailUrl FROM media WHERE videoId IN (:ids)")
    suspend fun coverOptions(ids: List<String>): List<PlaylistCoverOption>
    @Query("SELECT COUNT(*) FROM playlists") fun playlistCount(): Flow<Int>
    @Query("SELECT * FROM playlists WHERE id=:id") fun playlistHeader(id: String): Flow<PlaylistRecord?>
    @Query("SELECT m.localUri FROM playlist_members x JOIN media m ON m.videoId=x.videoId WHERE x.playlistId=:id AND m.localUri IS NOT NULL ORDER BY x.position LIMIT 1")
    suspend fun firstPlaylistUri(id: String): String?
    @Query("SELECT * FROM playlists WHERE id=:id") suspend fun playlist(id: String): PlaylistRecord?
    @Query("SELECT videoId FROM playlist_members WHERE playlistId=:id ORDER BY position") fun playlistIds(id: String): Flow<List<String>>
    @Query("SELECT videoId FROM playlist_members WHERE playlistId=:id ORDER BY position") suspend fun getPlaylistIds(id: String): List<String>
    @Upsert suspend fun savePlaylist(row: PlaylistRecord)
    @Insert suspend fun saveMembers(rows: List<PlaylistMemberRecord>)
    @Update suspend fun updateMembers(rows: List<PlaylistMemberRecord>)
    @Query("SELECT EXISTS(SELECT 1 FROM playlist_members WHERE playlistId=:id AND videoId=:videoId)") suspend fun hasMember(id: String, videoId: String): Boolean
    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_members WHERE playlistId=:id") suspend fun maxMemberPosition(id: String): Int
    @Query("SELECT playlistId FROM playlist_members WHERE videoId=:videoId") fun memberships(videoId: String): Flow<List<String>>
    @Query("DELETE FROM playlist_members WHERE playlistId=:id AND videoId=:videoId") suspend fun removeMember(id: String, videoId: String)
    @Query("DELETE FROM playlist_members WHERE playlistId=:id") suspend fun clearMembers(id: String)
    @Query("DELETE FROM playlists WHERE id=:id") suspend fun deletePlaylist(id: String)
    @Query("SELECT * FROM playlist_sync WHERE playlistId=:id") suspend fun playlistSync(id: String): PlaylistSyncRecord?
    @Query("SELECT * FROM playlist_sync WHERE playlistId=:id") fun observePlaylistSync(id: String): Flow<PlaylistSyncRecord?>
    @Query("SELECT playlistId FROM playlist_sync WHERE automatic ORDER BY playlistId") fun automaticPlaylistIds(): Flow<List<String>>
    @Upsert suspend fun savePlaylistSync(row: PlaylistSyncRecord)
    @Query("UPDATE playlist_sync SET automatic=:enabled WHERE playlistId=:id")
    suspend fun setPlaylistSyncAutomatic(id: String, enabled: Boolean)
    @Query("SELECT videoId FROM playlist_sync_seen WHERE playlistId=:id") suspend fun playlistSyncSeen(id: String): List<String>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun savePlaylistSyncSeen(rows: List<PlaylistSyncSeenRecord>)
}

@Database(entities = [MediaRecord::class, JobRecord::class, ProgressRecord::class, PlaylistRecord::class, PlaylistMemberRecord::class, ImportRecord::class, DownloadBatchRecord::class, PlaylistSyncRecord::class, PlaylistSyncSeenRecord::class], version = 4, exportSchema = true)
abstract class SpixDatabase : RoomDatabase() {
    abstract fun catalog(): CatalogDao
    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN coverUri TEXT")
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS download_batches (videoId TEXT NOT NULL, groupId TEXT NOT NULL, title TEXT NOT NULL, itemIndex INTEGER NOT NULL, total INTEGER NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(videoId), FOREIGN KEY(videoId) REFERENCES media(videoId) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_download_batches_groupId ON download_batches(groupId)")
            }
        }
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS playlist_sync (playlistId TEXT NOT NULL PRIMARY KEY, generation TEXT NOT NULL, automatic INTEGER NOT NULL, lastSyncedAt INTEGER, FOREIGN KEY(playlistId) REFERENCES playlists(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE TABLE IF NOT EXISTS playlist_sync_seen (playlistId TEXT NOT NULL, videoId TEXT NOT NULL, PRIMARY KEY(playlistId, videoId), FOREIGN KEY(playlistId) REFERENCES playlist_sync(playlistId) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("INSERT INTO playlist_sync (playlistId, generation, automatic) SELECT id, lower(hex(randomblob(16))), 0 FROM playlists WHERE id LIKE 'youtube:%'")
            }
        }
        fun open(context: Context) = Room.databaseBuilder(context.applicationContext, SpixDatabase::class.java, "spix.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    }
}
