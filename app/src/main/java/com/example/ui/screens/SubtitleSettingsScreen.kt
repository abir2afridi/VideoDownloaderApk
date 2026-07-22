package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSettingsScreen(
    onBack: () -> Unit,
    viewModel: com.example.ui.viewmodel.MainViewModel
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val isSubtitle by viewModel.isSubtitle.collectAsState()
    val subtitleLanguage by viewModel.subtitleLanguage.collectAsState()
    val isAutoSubtitle by viewModel.isAutoSubtitle.collectAsState()
    val convertSubtitle by viewModel.convertSubtitle.collectAsState()
    val isAutoTranslatedSubs by viewModel.isAutoTranslatedSubs.collectAsState()
    val isEmbedSubtitle by viewModel.isEmbedSubtitle.collectAsState()
    val isKeepSubtitleFiles by viewModel.isKeepSubtitleFiles.collectAsState()

    var showLangDialog by remember { mutableStateOf(false) }
    var showConvertDialog by remember { mutableStateOf(false) }
    var showEmbedConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Subtitle", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                scrollBehavior = scrollBehavior,
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SectionHeader("Subtitle Settings")

                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        SubtitleSwitchRow(
                            icon = Icons.Outlined.Subtitles,
                            iconTint = Color(0xFF607D8B),
                            title = "Download subtitles",
                            subtitle = "Download subtitles with video",
                            checked = isSubtitle,
                            onCheckedChange = { viewModel.isSubtitle.value = it }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        SubtitleDetailRow(
                            icon = Icons.Outlined.Language,
                            iconTint = Color(0xFF1565C0),
                            title = "Subtitle languages",
                            subtitle = subtitleLanguage,
                            enabled = isSubtitle,
                            onClick = { showLangDialog = true }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        SubtitleDetailRow(
                            icon = Icons.Outlined.SwapHoriz,
                            iconTint = Color(0xFFE65100),
                            title = "Convert subtitles",
                            subtitle = when (convertSubtitle) {
                                0 -> "Not convert"
                                1 -> "ASS"
                                2 -> "LRC"
                                3 -> "SRT"
                                4 -> "VTT"
                                else -> "Not specified"
                            },
                            enabled = isSubtitle,
                            onClick = { showConvertDialog = true }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        SubtitleSwitchRow(
                            icon = Icons.Outlined.ClosedCaption,
                            iconTint = Color(0xFF00838F),
                            title = "Automatic captions",
                            subtitle = "Download automatic captions when available",
                            checked = isAutoSubtitle,
                            enabled = isSubtitle,
                            onCheckedChange = { viewModel.isAutoSubtitle.value = it }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        SubtitleSwitchRow(
                            icon = Icons.Outlined.Translate,
                            iconTint = Color(0xFF4A148C),
                            title = "Auto-translated subtitles (Experimental)",
                            subtitle = "Translate captions to your language",
                            checked = isAutoTranslatedSubs,
                            enabled = isAutoSubtitle,
                            onCheckedChange = { viewModel.isAutoTranslatedSubs.value = it }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        SubtitleSwitchRow(
                            icon = Icons.Outlined.Code,
                            iconTint = Color(0xFF1B5E20),
                            title = "Embed subtitles (Experimental)",
                            subtitle = "Embed subtitles into video file",
                            checked = isEmbedSubtitle,
                            enabled = isSubtitle,
                            onCheckedChange = {
                                if (!isEmbedSubtitle) showEmbedConfirm = true
                                else viewModel.isEmbedSubtitle.value = false
                            }
                        )

                        if (isEmbedSubtitle) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                            SubtitleSwitchRow(
                                icon = Icons.Outlined.Folder,
                                iconTint = Color(0xFF5D4037),
                                title = "Keep subtitle files",
                                subtitle = "Keep subtitle files after embedding",
                                checked = isKeepSubtitleFiles,
                                enabled = isEmbedSubtitle,
                                onCheckedChange = { viewModel.isKeepSubtitleFiles.value = it }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    )

    if (showLangDialog) {
        SubtitleLanguageDialog(
            current = subtitleLanguage,
            onDismiss = { showLangDialog = false },
            onConfirm = { viewModel.subtitleLanguage.value = it }
        )
    }
    if (showConvertDialog) {
        SubtitleConvertDialog(
            current = convertSubtitle,
            onDismiss = { showConvertDialog = false },
            onConfirm = { viewModel.convertSubtitle.value = it }
        )
    }
    if (showEmbedConfirm) {
        AlertDialog(
            onDismissRequest = { showEmbedConfirm = false },
            icon = { Icon(Icons.Outlined.Code, null) },
            confirmButton = {
                TextButton(onClick = { viewModel.isEmbedSubtitle.value = true; showEmbedConfirm = false }) { Text("Enable") }
            },
            dismissButton = { TextButton(onClick = { showEmbedConfirm = false }) { Text("Cancel") } },
            title = { Text("Enable experimental feature") },
            text = { Text("Embedding subtitles requires FFmpeg and may increase processing time.") }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

@Composable
private fun SubtitleSwitchRow(
    icon: ImageVector, iconTint: Color, title: String, subtitle: String,
    checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.7f else 0.3f))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SubtitleDetailRow(
    icon: ImageVector, iconTint: Color, title: String, subtitle: String,
    enabled: Boolean = true, onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.7f else 0.3f))
        }
        Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SubtitleLanguageDialog(
    current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subtitle languages") },
        text = {
            Column {
                Text("Language codes separated by commas. Default: en.*,.*-orig",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SubtitleConvertDialog(
    current: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit
) {
    var selected by remember { mutableIntStateOf(current) }
    val options = listOf(0 to "Not convert", 1 to "ASS", 2 to "LRC", 3 to "SRT", 4 to "VTT")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Convert subtitles") },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { selected = value }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label)
                        if (value == selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
