package com.pedroaba.spix.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.remember
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pedroaba.spix.domain.Track
import com.pedroaba.spix.domain.MediaKind
import com.pedroaba.spix.ui.components.*

@Composable
fun LibraryScreen(tracks: List<Track>) {
    var filter by rememberSaveable { mutableStateOf("Todas") }
    val visibleTracks = remember(tracks, filter) {
        tracks.filter { filter == "Todas" || (filter == "Músicas" && it.kind == MediaKind.Music) || (filter == "Vídeos" && it.kind == MediaKind.Video) }
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "title", contentType = "header") { PageTitle("Sua biblioteca", "${tracks.size} ${if (tracks.size == 1) "item disponível" else "itens disponíveis"} offline") }
        item(key = "filters", contentType = "filters") { LibraryFilters { filter = it } }
        if (tracks.isEmpty()) item(key = "empty", contentType = "empty") {
            EmptyState(SpixIcons.Headphones, "Sua biblioteca começa aqui", "Os vídeos baixados aparecerão aqui.")
        } else if (visibleTracks.isEmpty()) item(key = "empty-filter", contentType = "empty") {
            EmptyState(SpixIcons.Library, "Nenhum item neste filtro", "Escolha outro filtro para ver seu conteúdo.")
        } else items(visibleTracks, key = { "track:${it.id}" }, contentType = { "track" }) { track ->
            ListItem(headlineContent = { Text(track.title) }, supportingContent = { Text(listOf(track.artist, track.duration).filter { it.isNotBlank() }.joinToString(" · ")) }, trailingContent = { OpenVideoButton(track.localUri) })
        }
    }
}

@Composable
fun SearchScreen(query: String, tracks: List<Track>, onQuery: (String) -> Unit, onBack: () -> Unit) {
    val results = remember(query, tracks) { tracks.filter { query.isNotBlank() && (it.title.contains(query, true) || it.artist.contains(query, true)) } }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "header", contentType = "header") {
            Row {
                IconButton(onClick = onBack) { Icon(SpixIcons.Back, "Voltar") }
                Text("Buscar", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        item(key = "input", contentType = "input") {
            OutlinedTextField(value = query, onValueChange = onQuery, singleLine = true, shape = CircleShape,
                modifier = Modifier.fillMaxWidth(), placeholder = { Text("Buscar na biblioteca") },
                leadingIcon = { Icon(SpixIcons.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(SpixIcons.Close, "Limpar busca") } },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.secondary))
        }
        if (results.isEmpty()) item(key = "empty", contentType = "empty") { EmptyState(SpixIcons.Search,
            if (query.isBlank()) "Encontre suas músicas" else "Nenhuma música encontrada",
            if (query.isBlank()) "Busque pelo nome da música ou do artista na sua biblioteca." else "Não há resultados para “$query”. Tente outro nome.") }
        else items(results, key = { "track:${it.id}" }, contentType = { "track" }) { ListItem(headlineContent = { Text(it.title) }, supportingContent = { Text(it.artist) }) }
    }
}
