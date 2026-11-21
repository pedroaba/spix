package com.pedroaba.spix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pedroaba.spix.ui.theme.SpixColors

@Composable
fun BrandHeader(onSearch: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp, 26.dp).background(SpixColors.Red, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.size(15.dp)) {
                drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * .2f, 0f); lineTo(size.width, size.height / 2)
                    lineTo(size.width * .2f, size.height); close()
                }, Color.White)
            }
        }
        Text("Spix", Modifier.padding(start = 8.dp).weight(1f), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        IconButton(onClick = onSearch) { Icon(SpixIcons.Search, "Buscar músicas") }
        IconButton(onClick = onSettings) { Icon(SpixIcons.Settings, "Abrir configurações") }
    }
}

@Composable
fun PageTitle(title: String, subtitle: String? = null) {
    Text(title, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
    if (subtitle != null) Text(subtitle, Modifier.padding(top = 5.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
}

@Composable
fun EmptyState(icon: ImageVector, title: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(112.dp).background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(46.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(title, Modifier.padding(top = 24.dp), fontSize = 20.sp, lineHeight = 28.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(description, Modifier.padding(top = 12.dp).widthIn(max = 320.dp), fontSize = 14.sp,
            lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun LibraryFilters(onSelected: (String) -> Unit = {}) {
    var selected by rememberSaveable { mutableStateOf("Todas") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Todas", "Músicas", "Vídeos").forEach { label ->
            FilterChip(selected = selected == label, onClick = { selected = label; onSelected(label) }, label = { Text(label) }, shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.inverseSurface, selectedLabelColor = MaterialTheme.colorScheme.inverseOnSurface))
        }
    }
}
