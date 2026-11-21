package com.pedroaba.spix.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Publishes completed downloads to the public Downloads/Spix folder or a persisted SAF tree. */
class VideoStorage(private val context: Context, private val settings: SettingsRepository) {
    suspend fun createVideo(fileName: String, mimeType: String = "video/mp4"): Uri = withContext(Dispatchers.IO) {
        require(fileName.isNotBlank() && fileName !in setOf(".", "..") && fileName.none { it == '/' || it == '\\' || it.isISOControl() })
        val suffix = UUID.randomUUID().toString().take(8)
        val dot = fileName.lastIndexOf('.')
        val uniqueName = if (dot > 0) "${fileName.take(dot)}_$suffix${fileName.substring(dot)}" else "${fileName}_$suffix"
        val selected = settings.settings.value.videoFolderUri
        if (selected != null) {
            val tree = Uri.parse(selected)
            check(context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }) {
                "Escolha novamente a pasta dos vídeos nas configurações."
            }
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            DocumentsContract.createDocument(context.contentResolver, parent, mimeType, uniqueName)
                ?: throw IOException("Não foi possível criar o arquivo na pasta escolhida.")
        } else if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Spix")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Não foi possível criar o vídeo em Downloads/Spix.")
        } else {
            val directory = settings.defaultDirectory()
            check(directory.isDirectory || directory.mkdirs()) { "Não foi possível criar Downloads/Spix." }
            val file = File(directory, uniqueName)
            check(file.createNewFile()) { "Não foi possível criar o arquivo." }
            Uri.fromFile(file)
        }
    }

    /** A failed copy never leaves a broken file in the public destination. */
    suspend fun writeVideo(uri: Uri, write: (OutputStream) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val output = context.contentResolver.openOutputStream(uri, "w") ?: throw IOException("Não foi possível abrir o arquivo para gravação.")
            output.use(write)
            if (Build.VERSION.SDK_INT >= 29 && uri.authority == MediaStore.AUTHORITY) {
                check(context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) > 0)
            }
        } catch (error: Exception) {
            runCatching { deleteVideo(uri) }
            throw error
        }
    }

    /** Delete disk content first; callers only remove catalog rows after this succeeds. */
    suspend fun deleteSavedVideo(value: String) = withContext(Dispatchers.IO) {
        val uri = Uri.parse(value)
        try {
            if (!deleteVideo(uri) && exists(uri)) throw IOException("Não foi possível excluir o arquivo.")
        } catch (missing: java.io.FileNotFoundException) {
            // A file already removed outside Spix may also be removed from the catalog.
        } catch (denied: SecurityException) {
            val sender = when {
                Build.VERSION.SDK_INT >= 30 && uri.authority == MediaStore.AUTHORITY ->
                    MediaStore.createDeleteRequest(context.contentResolver, listOf(uri)).intentSender
                Build.VERSION.SDK_INT == 29 && denied is android.app.RecoverableSecurityException -> denied.userAction.actionIntent.intentSender
                else -> throw denied
            }
            throw FileDeletionConsent(sender)
        }
    }

    private fun exists(uri: Uri): Boolean = when {
        uri.scheme == "file" -> File(requireNotNull(uri.path)).exists()
        else -> context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { it.moveToFirst() } ?: true
    }

    suspend fun deleteVideo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        when {
            uri.scheme == "file" -> File(requireNotNull(uri.path)).delete()
            DocumentsContract.isDocumentUri(context, uri) -> DocumentsContract.deleteDocument(context.contentResolver, uri)
            else -> context.contentResolver.delete(uri, null, null) > 0
        }
    }
}

class FileDeletionConsent(val sender: android.content.IntentSender) : Exception("Confirme a exclusão no Android.")
