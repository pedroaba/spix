package com.pedroaba.spix.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pedroaba.spix.ui.components.*
import com.pedroaba.spix.ui.theme.SpixColors
import com.pedroaba.spix.domain.VideoPreviewState

@Composable
fun HomeScreen(link: String, error: String?, busy: Boolean, preview: VideoPreviewState, motion: Boolean, libraryCount: Int, onLink: (String) -> Unit, onSearch: () -> Unit, onSubmit: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val loading = preview is VideoPreviewState.Loading
    fun submit() {
        keyboard?.hide()
        if (preview is VideoPreviewState.Ready) onSubmit() else onSearch()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(8.dp))
        Column {
            Text("Sua música, sem internet.", fontSize = 25.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold)
            Text("Cole um link e leve com você.", Modifier.padding(top = 5.dp), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = link, onValueChange = onLink, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
            shape = CircleShape, placeholder = { Text("Cole o link do YouTube", fontSize = 14.sp) },
            isError = error != null,
            supportingText = if (error != null) ({ Text(error) }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (!loading && !busy) submit() }),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.secondary),
        )
        VideoPreview(preview, motion, onSearch)
        Button(onClick = ::submit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            enabled = !busy && preview is VideoPreviewState.Ready,
            colors = ButtonDefaults.buttonColors(containerColor = SpixColors.Red, contentColor = androidx.compose.ui.graphics.Color.White)) {
            Icon(SpixIcons.Download, null, Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("Baixar vídeo", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Sua biblioteca", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("$libraryCount ${if (libraryCount == 1) "item" else "itens"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        LibraryFilters()
        if (libraryCount == 0) EmptyState(SpixIcons.Headphones, "Sua biblioteca começa aqui", "Cole um link do YouTube acima para adicionar seu primeiro vídeo.")
        else Text("Seus vídeos estão disponíveis na aba Biblioteca.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
