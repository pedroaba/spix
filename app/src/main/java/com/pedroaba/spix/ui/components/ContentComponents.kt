package com.pedroaba.spix.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pedroaba.spix.domain.Track

@Composable
fun LinkInput(value: String, onChange: (String) -> Unit, error: String?, enabled: Boolean, onDone: () -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
        singleLine = true, enabled = enabled, shape = CircleShape,
        placeholder = { Text("Link de vídeo ou playlist do YouTube", fontSize = 14.sp) },
        leadingIcon = { Icon(SpixIcons.Search, null, Modifier.size(22.dp)) },
        isError = error != null,
        supportingText = if (error != null) ({ Text(error) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.secondary),
    )
}

@Composable
fun FilterPills(labels: List<String>, selected: String, onSelected: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach { label ->
            // The visual pill is 36 dp; its surrounding hit area stays at least 48 dp high.
            FilterChip(selected = selected == label, onClick = { onSelected(label) },
                label = { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                modifier = Modifier.height(36.dp), shape = CircleShape, border = null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                    selectedLabelColor = MaterialTheme.colorScheme.inverseOnSurface))
        }
    }
}

@Composable
fun SecondaryAction(label: String, onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, enabled: Boolean = true) {
    Button(onClick = onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled, shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface)) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)) }
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Shared action used by home, search, library and playlist detail. */
val LocalSaveToPlaylist = staticCompositionLocalOf<(Track) -> Unit> { {} }

internal val CompactTrackRowShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackRow(
    track: Track,
    thumbnail: String?,
    expanded: Boolean = false,
    onPlay: (() -> Unit)? = null,
    moves: PlaylistTrackMoves? = null,
    selected: Boolean = false,
    onToggleSelection: (() -> Unit)? = null,
) {
    var options by remember(track.id) { mutableStateOf(false) }
    val interaction = when {
        onToggleSelection != null -> Modifier.toggleable(selected, role = Role.Checkbox) { onToggleSelection() }
        onPlay != null -> Modifier.clickable(onClickLabel = "Reproduzir", onClick = onPlay)
        else -> Modifier.playVideoOnClick(track.localUri)
    }
    val more: @Composable () -> Unit = {
        IconButton(onClick = { options = true }) {
            Icon(SpixIcons.MoreVertical, "Opções de ${track.title}", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (expanded) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().then(interaction)) {
                Box {
                    VideoThumbnail(thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    if (track.duration.isNotBlank()) Surface(Modifier.align(Alignment.BottomEnd).padding(8.dp),
                        shape = RoundedCornerShape(4.dp), color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f), contentColor = androidx.compose.ui.graphics.Color.White) {
                        Text(track.duration, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    more()
                }
            }
        }
    } else Row(
        Modifier.fillMaxWidth().clip(CompactTrackRowShape)
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
            .then(interaction)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VideoThumbnail(thumbnail, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
        Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(track.title, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(track.artist, track.duration).filter { it.isNotBlank() }.joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onToggleSelection != null) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Checkbox(checked = selected, onCheckedChange = null)
            }
        } else {
            more()
        }
    }
    if (options) TrackOptionsSheet(track, { options = false }, onPlay, moves)
}

@Composable
fun SearchInput(value: String, onChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    BasicTextField(value = value, onValueChange = onChange, singleLine = true, interactionSource = interactions,
        textStyle = TextStyle(color = colors.onSurface, fontSize = 15.sp), cursorBrush = SolidColor(colors.secondary),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Buscar na biblioteca" },
        decorationBox = { field ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(CircleShape).background(colors.surfaceContainer)
                .then(if (focused) Modifier.border(2.dp, colors.secondary, CircleShape) else Modifier)
                .padding(start = 16.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(SpixIcons.Search, null, Modifier.size(22.dp), tint = colors.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text("Buscar na biblioteca", fontSize = 15.sp, color = colors.onSurfaceVariant)
                    field()
                }
                IconButton(onClick = { onChange("") }, enabled = value.isNotEmpty()) { Icon(SpixIcons.Close, "Limpar busca", Modifier.size(22.dp)) }
            }
        })
}
