package com.pedroaba.spix.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.pedroaba.spix.domain.AppSettings
import com.pedroaba.spix.domain.StorageLocation
import com.pedroaba.spix.domain.ThemeMode
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SettingsRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences("spix_settings", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutableSettings = MutableStateFlow(AppSettings(
        theme = ThemeMode.entries.firstOrNull { it.name == preferences.getString("theme", null) } ?: ThemeMode.System,
        navigationAnimations = preferences.getBoolean("navigation_animations", true),
        videoFolderUri = preferences.getString("video_folder", null),
    ))
    val settings = mutableSettings.asStateFlow()
    private val mutableLocation = MutableStateFlow(defaultLocation())
    val location = mutableLocation.asStateFlow()

    suspend fun setTheme(theme: ThemeMode) = update { it.copy(theme = theme) }
    suspend fun setAnimations(enabled: Boolean) = update { it.copy(navigationAnimations = enabled) }

    suspend fun selectFolder(uri: Uri) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val resolver = context.contentResolver
            val alreadyGranted = resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }
            resolver.takePersistableUriPermission(uri, grantFlags)
            try {
                val location = inspectFolder(uri)
                check(location.available) { "Escolha uma pasta que permita salvar arquivos." }
                persist(mutableSettings.value.copy(videoFolderUri = uri.toString()))
                mutableLocation.value = location
            } catch (error: Exception) {
                if (!alreadyGranted) runCatching { resolver.releasePersistableUriPermission(uri, grantFlags) }
                throw error
            }
        }
    }

    suspend fun useDefaultFolder() = withContext(Dispatchers.IO) {
        mutex.withLock {
            persist(mutableSettings.value.copy(videoFolderUri = null))
            mutableLocation.value = defaultLocation()
        }
        // Existing document grants are retained: changing the destination must not revoke access to older media.
    }

    suspend fun refreshLocation() = withContext(Dispatchers.IO) {
        mutex.withLock {
            mutableLocation.value = mutableSettings.value.videoFolderUri?.let { value ->
                runCatching { inspectFolder(Uri.parse(value)) }.getOrElse {
                    StorageLocation("Pasta indisponível", "Selecione a pasta novamente para recuperar o acesso.", true, false)
                }
            } ?: defaultLocation()
        }
    }

    @Suppress("DEPRECATION")
    fun defaultDirectory(): File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Spix")

    private fun defaultLocation() = StorageLocation("Downloads/Spix", "Armazenamento do dispositivo · Downloads/Spix", false)

    private fun inspectFolder(uri: Uri): StorageLocation {
        val granted = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }
        require(granted) { "O acesso a esta pasta foi revogado. Escolha a pasta novamente." }
        val document = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.COLUMN_MIME_TYPE)
        context.contentResolver.query(document, columns, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val writable = cursor.getInt(1) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0
                val directory = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                return StorageLocation(cursor.getString(0) ?: "Pasta escolhida", "Pasta escolhida no dispositivo", true, writable && directory)
            }
        }
        throw IOException("Não foi possível abrir esta pasta.")
    }

    private suspend fun update(transform: (AppSettings) -> AppSettings) = withContext(Dispatchers.IO) {
        mutex.withLock { persist(transform(mutableSettings.value)) }
    }

    private fun persist(value: AppSettings) {
        check(preferences.edit().putString("theme", value.theme.name)
            .putBoolean("navigation_animations", value.navigationAnimations)
            .putString("video_folder", value.videoFolderUri).commit()) { "Não foi possível salvar as configurações." }
        mutableSettings.value = value
    }

    companion object {
        private const val grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
