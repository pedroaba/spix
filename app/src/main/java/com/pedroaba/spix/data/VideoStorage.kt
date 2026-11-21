package com.pedroaba.spix.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The future downloader uses this adapter so its output follows the selected destination. */
class VideoStorage(private val context: Context, private val settings: SettingsRepository) {
    suspend fun createVideo(fileName: String, mimeType: String = "video/mp4"): Uri = withContext(Dispatchers.IO) {
        require(fileName.isNotBlank() && fileName !in setOf(".", "..") && fileName.none { it == '/' || it == '\\' || it.isISOControl() })
        val selected = settings.settings.value.videoFolderUri
        if (selected == null) {
            val directory = settings.defaultDirectory()
            check(directory.isDirectory || directory.mkdirs()) { "Não foi possível criar a pasta do Spix." }
            val file = File(directory, "${UUID.randomUUID()}_$fileName")
            check(file.createNewFile()) { "Não foi possível criar o arquivo." }
            Uri.fromFile(file)
        } else {
            val tree = Uri.parse(selected)
            val granted = context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
            check(granted) { "Escolha novamente a pasta dos vídeos nas configurações." }
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            DocumentsContract.createDocument(context.contentResolver, parent, mimeType, fileName)
                ?: throw IOException("Não foi possível criar o arquivo na pasta escolhida.")
        }
    }

    suspend fun writeVideo(uri: Uri, write: (OutputStream) -> Unit) = withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(uri, "w") ?: throw IOException("Não foi possível abrir o arquivo para gravação.")
        output.use(write)
    }
}
