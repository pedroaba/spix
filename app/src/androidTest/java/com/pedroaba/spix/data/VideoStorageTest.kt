package com.pedroaba.spix.data

import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun defaultDestinationWritesAndReadsBytesWithoutOverwriting() = runBlocking {
        val preferences = SettingsRepository(context)
        val original = preferences.settings.value.videoFolderUri
        val created = mutableListOf<Uri>()
        try {
            preferences.useDefaultFolder()
            val storage = VideoStorage(context, preferences)
            repeat(2) { created += storage.createVideo("storage-test.mp4") }
            assertNotEquals(created[0], created[1])
            assertEquals(preferences.defaultDirectory(), File(requireNotNull(created[0].path)).parentFile)
            assertRoundTrip(storage, created[0])
        } finally {
            created.forEach { File(requireNotNull(it.path)).delete() }
            if (original != null) preferences.selectFolder(Uri.parse(original))
        }
    }

    /** Choose a folder in Settings before this test to exercise a real persisted SAF grant. */
    @Test fun selectedFolderWritesAndReadsThroughThePersistedGrant() = runBlocking {
        val preferences = SettingsRepository(context)
        assumeNotNull(preferences.settings.value.videoFolderUri)
        val storage = VideoStorage(context, preferences)
        val uri = storage.createVideo("spix-storage-test.mp4")
        try {
            assertEquals("content", uri.scheme)
            assertRoundTrip(storage, uri)
        } finally {
            assertTrue(DocumentsContract.deleteDocument(context.contentResolver, uri))
        }
    }

    private suspend fun assertRoundTrip(storage: VideoStorage, uri: Uri) {
        val content = "Spix storage smoke test".toByteArray()
        storage.writeVideo(uri) { it.write(content) }
        val actual = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
        assertArrayEquals(content, actual)
    }
}
