package com.pedroaba.spix.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

@Composable
fun OpenVideoButton(value: String) {
    val context = LocalContext.current
    TextButton(onClick = {
        try {
            val source = Uri.parse(value)
            val uri = if (source.scheme == "file") FileProvider.getUriForFile(context, "${context.packageName}.files", File(requireNotNull(source.path))) else source
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Instale um player de vídeo para abrir o arquivo.", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(context, "Não foi possível abrir o vídeo. Verifique se o arquivo ainda existe.", Toast.LENGTH_LONG).show()
        }
    }) { Text("Abrir") }
}
