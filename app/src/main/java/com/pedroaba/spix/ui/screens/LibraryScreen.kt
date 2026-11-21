package com.pedroaba.spix.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
    val visibleTracks = tracks.filter { filter == "Todas" || (filter == "Músicas" && it.kind == MediaKind.Music) || (filter == "Vídeos" && it.kind == MediaKind.Video) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageTitle("Sua biblioteca", "${tracks.size} músicas · disponíveis offline")
        LibraryFilters { filter = it }
        if (tracks.isEmpty()) EmptyState(SpixIcons.Headphones, "Sua biblioteca começa aqui", "Suas músicas aparecerão aqui quando os downloads estiverem disponíveis.")
        else if (visibleTracks.isEmpty()) EmptyState(SpixIcons.Library, "Nenhum item neste filtro", "Escolha outro filtro para ver seu conteúdo.")
        else visibleTracks.forEach { track -> ListItem(headlineContent = { Text(track.title) }, supportingContent = { Text("${track.artist} · ${track.duration}") }, trailingContent = { Icon(SpixIcons.Offline, "Disponível offline") }) }
    }
}

@Composable
fun SearchScreen(query: String, tracks: List<Track>, onQuery: (String) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row {
            IconButton(onClick = onBack) { Icon(SpixIcons.Back, "Voltar") }
            Text("Buscar", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        OutlinedTextField(value = query, onValueChange = onQuery, singleLine = true, shape = CircleShape,
            modifier = Modifier.fillMaxWidth(), placeholder = { Text("Buscar na biblioteca") },
            leadingIcon = { Icon(SpixIcons.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(SpixIcons.Close, "Limpar busca") } },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.secondary))
        val results = tracks.filter { query.isNotBlank() && (it.title.contains(query, true) || it.artist.contains(query, true)) }
        if (results.isEmpty()) EmptyState(SpixIcons.Search,
            if (query.isBlank()) "Encontre suas músicas" else "Nenhuma música encontrada",
            if (query.isBlank()) "Busque pelo nome da música ou do artista na sua biblioteca." else "Não há resultados para “$query”. Tente outro nome.")
        else results.forEach { ListItem(headlineContent = { Text(it.title) }, supportingContent = { Text(it.artist) }) }
    }
}
