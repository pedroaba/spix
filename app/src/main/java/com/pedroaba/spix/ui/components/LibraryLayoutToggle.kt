package com.pedroaba.spix.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun LibraryLayoutToggle(cards: Boolean, onCards: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(10.dp), color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.7f))) {
        Row(Modifier.selectableGroup().padding(2.dp)) {
            listOf(true, false).forEach { cardMode ->
                val selected = cards == cardMode
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(7.dp))
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onCards(cardMode) })
                    .padding(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) colors.surfaceContainer else Color.Transparent),
                    contentAlignment = Alignment.Center) {
                    Icon(if (cardMode) SpixIcons.GridView else SpixIcons.ListView,
                        contentDescription = if (cardMode) "Exibir em cards" else "Exibir lista compacta",
                        modifier = Modifier.size(20.dp), tint = if (selected) colors.onSurface else colors.onSurfaceVariant)
                }
            }
        }
    }
}
