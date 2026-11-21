package com.pedroaba.spix.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pedroaba.spix.domain.AppSettings
import com.pedroaba.spix.domain.StorageLocation
import com.pedroaba.spix.domain.ThemeMode
import com.pedroaba.spix.ui.components.SpixIcons

@Composable
fun SettingsScreen(
    settings: AppSettings,
    location: StorageLocation,
    busy: Boolean,
    onBack: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onAnimations: (Boolean) -> Unit,
    onFolder: (Uri) -> Unit,
    onDefaultFolder: () -> Unit,
    onRefresh: () -> Unit,
    onPickerUnavailable: () -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onFolder(uri)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) onRefresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(SpixIcons.Back, "Voltar") }
                Text("Configurações", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Text("Deixe o Spix do seu jeito.", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SectionLabel("APARÊNCIA")
            SettingsCard {
                ThemeSelector(selected = settings.theme, enabled = !busy, onSelect = onTheme)
            }
        }
        item {
            SectionLabel("ARMAZENAMENTO")
            SettingsCard {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(SpixIcons.Folder, null, tint = if (location.available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Onde salvar os vídeos", fontWeight = FontWeight.SemiBold)
                        Text(location.name, color = if (location.available) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                        Text(location.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), enabled = !busy,
                    onClick = {
                        runCatching { picker.launch(settings.videoFolderUri?.let(Uri::parse)) }
                            .onFailure { onPickerUnavailable() }
                    },
                ) { Text(if (location.available) "Escolher pasta" else "Selecionar pasta novamente") }
                if (location.isCustom) TextButton(onClick = onDefaultFolder, enabled = !busy, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("Usar Downloads/Spix")
                }
                Text(
                    if (location.isCustom) "Os próximos downloads usarão esta pasta. Arquivos existentes não serão movidos."
                    else "Os vídeos ficam em Downloads/Spix e permanecem no celular ao desinstalar o app.",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SectionLabel("NAVEGAÇÃO")
            SettingsCard {
                Row(
                    Modifier.fillMaxWidth().toggleable(value = settings.navigationAnimations, enabled = !busy, role = Role.Switch, onValueChange = onAnimations).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(SpixIcons.Motion, null)
                    Column(Modifier.weight(1f)) {
                        Text("Animações de navegação", fontWeight = FontWeight.Medium)
                        Text("Transições suaves entre as telas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = settings.navigationAnimations, onCheckedChange = null, enabled = !busy)
                }
            }
        }
        item {
            SectionLabel("SOBRE")
            SettingsCard {
                ListItem(
                    headlineContent = { Text("Spix") }, supportingContent = { Text("Versão 0.1.0 · Sua música, sem internet.") },
                    leadingContent = { Icon(SpixIcons.Info, null) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                )
                Text("Baixe vídeos, acompanhe o progresso e abra os arquivos em um player do celular.",
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, Modifier.padding(bottom = 10.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun ThemeSelector(selected: ThemeMode, enabled: Boolean, onSelect: (ThemeMode) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tema do aplicativo", fontWeight = FontWeight.SemiBold)
        Surface(shape = RoundedCornerShape(14.dp), color = colors.surfaceContainer) {
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(4.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ThemeMode.entries.forEach { theme ->
                    val active = selected == theme
                    Surface(
                        modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 72.dp)
                            .selectable(selected = active, enabled = enabled, role = Role.RadioButton,
                                onClick = { if (!active) onSelect(theme) }),
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) colors.primaryContainer else Color.Transparent,
                        contentColor = if (active) colors.onPrimaryContainer else colors.onSurfaceVariant,
                        border = if (active) BorderStroke(1.dp, colors.onPrimaryContainer.copy(alpha = 0.3f)) else null,
                    ) {
                        Column(
                            Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(when (theme) {
                                ThemeMode.System -> SpixIcons.System
                                ThemeMode.Light -> SpixIcons.Sun
                                ThemeMode.Dark -> SpixIcons.Moon
                            }, null, Modifier.size(22.dp))
                            Text(theme.label, fontSize = 14.sp, textAlign = TextAlign.Center,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium)
                        }
                    }
                }
            }
        }
        Text(when (selected) {
            ThemeMode.System -> "Usa o tema definido no Android."
            ThemeMode.Light -> "Mantém a aparência clara."
            ThemeMode.Dark -> "Mantém a aparência escura."
        }, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}
