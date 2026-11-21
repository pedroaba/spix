package com.pedroaba.spix.data

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroaba.spix.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MediaFeaturesTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun versionOneMigrationPreservesMediaPlaylistAndOrder() = runBlocking {
        val name = "migration-media-features.db"
        context.deleteDatabase(name)
        val schema = JSONObject(instrumentation.context.assets.open("catalog-v1.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            repeat(entities.length()) { index ->
                val entity = entities.getJSONObject(index)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                repeat(indices.length()) { j -> old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName"))) }
            }
            val setup = schema.getJSONArray("setupQueries")
            repeat(setup.length()) { old.execSQL(setup.getString(it)) }
            old.execSQL("INSERT INTO media(videoId,url,addedAt,title,author,thumbnailUrl,localUri,duration,kind,searchTitle,searchAuthor) VALUES('kept','https://example.com',1,'Título','Canal',NULL,'content://kept','0:02','Video','título','canal')")
            old.execSQL("INSERT INTO jobs(videoId,status,progress,error,transfer) VALUES('kept','Completed',100,NULL,NULL)")
            old.execSQL("INSERT INTO playlists(id,name,position) VALUES('old','Minha playlist',1)")
            old.execSQL("INSERT INTO playlist_members(playlistId,videoId,position) VALUES('old','kept',0)")
            old.execSQL("INSERT INTO playlists(id,name,position) VALUES('youtube:PLold','Importada',2)")
            old.execSQL("INSERT INTO playlist_members(playlistId,videoId,position) VALUES('youtube:PLold','kept',0)")
            old.version = 1
        }
        val db = Room.databaseBuilder(context, SpixDatabase::class.java, name)
            .addMigrations(SpixDatabase.MIGRATION_1_2, SpixDatabase.MIGRATION_2_3, SpixDatabase.MIGRATION_3_4).build()
        try {
            assertEquals(1, db.catalog().mediaCount())
            assertEquals("content://kept", db.catalog().get("kept")?.media?.localUri)
            assertEquals(listOf("kept"), db.catalog().getPlaylistIds("old"))
            assertNull(db.catalog().playlist("old")?.coverUri)
            assertNull(db.catalog().playlistSync("old"))
            val imported = requireNotNull(db.catalog().playlistSync("youtube:PLold"))
            assertFalse(imported.automatic)
            assertNull(imported.lastSyncedAt)
            assertEquals(listOf("kept"), db.catalog().getPlaylistIds("youtube:PLold"))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun importedPlaylistKeepsOrderIncludesExistingVideosAndPreservesCustomCover() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, SpixDatabase::class.java).build()
        val repo = LocalSpixRepository(context, db, importLegacy = false)
        try {
            repo.awaitReady()
            val videos = listOf("one", "two").map { VideoMetadata(it, "https://example.com/$it", it, "Canal", "https://example.com/$it.jpg") }
            repo.enqueueBatch(videos)
            repo.updateDownload("one") { it.copy(status = DownloadStatus.Completed, localUri = "content://one") }
            val playlists = LocalPlaylistRepository(repo)
            val source = PlaylistMetadata("source", "Nome escolhido", videos)
            playlists.importPlaylist(source)
            val id = "youtube:source"
            assertEquals(listOf("one", "two"), playlists.get(id)?.videoIds)
            assertEquals(listOf("one", "two"), playlists.covers(listOf("one", "two")).map { it.videoId })
            val cover = videos[1].thumbnailUrl
            playlists.save(id, "Com capa", listOf("one", "two"), cover, updateCover = true)
            assertEquals(cover, playlists.get(id)?.coverUri)
            playlists.importPlaylist(source.copy(title = "Atualizada", videos = videos.reversed()))
            val reopened = LocalPlaylistRepository(repo)
            assertEquals(cover, reopened.get(id)?.coverUri)
            assertEquals(listOf("two", "one"), reopened.get(id)?.videoIds)
            assertEquals(1, playlists.count.first())
            val pages = db.catalog().playlistPages().load(PagingSource.LoadParams.Refresh(null, 20, false)) as PagingSource.LoadResult.Page
            assertEquals(cover, pages.data.single().coverUri)
            playlists.setMembership(id, "two", false)
            assertNull(reopened.get(id)?.coverUri)
            assertEquals(videos[0].thumbnailUrl, playlists.artwork(id).first())
            playlists.setMembership(id, "two", true)
            playlists.save(id, "Com capa", listOf("one", "two"), cover, updateCover = true)
            repo.removeRequest("two")
            assertNull(reopened.get(id)?.coverUri)
            assertEquals(videos[0].thumbnailUrl, playlists.artwork(id).first())
            playlists.save(id, "Sem capa externa", listOf("one"), "file:///gallery.png", updateCover = true)
            assertNull(reopened.get(id)?.coverUri)
            playlists.delete(id)
        } finally { repo.close(); db.close() }
    }

    @Test fun savedFileDetailsHaveRealSizeAndDeletionRemovesPublicBytes() = runBlocking {
        val settings = SettingsRepository(context)
        val storage = VideoStorage(context, settings)
        val uri = storage.createVideo("media-features-test.mp4")
        try {
            val bytes = instrumentation.context.assets.open("media-fixture.mp4").use { it.readBytes() }
            storage.writeVideo(uri) { it.write(bytes) }
            val track = Track("test", "Vídeo", "Canal", "", uri.toString())
            val details = readMediaFileDetails(context, track, null)
            assertFalse(details.unavailable)
            assertTrue(details.rows.toMap().getValue("Tamanho no celular").contains(java.text.NumberFormat.getIntegerInstance().format(bytes.size)))
            assertTrue(details.rows.toMap().containsKey("Resolução"))
            assertTrue(details.rows.toMap().containsKey("Codec de vídeo"))
            storage.deleteSavedVideo(uri.toString())
            assertTrue(runCatching { context.contentResolver.openInputStream(uri)?.use { it.read() } }.isFailure)
            storage.deleteSavedVideo(uri.toString()) // An already missing file is safe to reconcile.
        } finally { runCatching { storage.deleteVideo(uri) } }
    }
}
