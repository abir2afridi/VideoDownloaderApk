package com.example.ui.screens

import android.annotation.SuppressLint
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

private const val FORMAT_COMPATIBILITY = 1
private const val FORMAT_QUALITY = 2

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatSettingsScreen(
    onBack: () -> Unit,
    viewModel: com.example.ui.viewmodel.MainViewModel
) {
    var showSubtitle by remember { mutableStateOf(false) }

    if (showSubtitle) {
        SubtitleSettingsScreen(onBack = { showSubtitle = false }, viewModel = viewModel)
        return
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val isExtractAudio by viewModel.isExtractAudio.collectAsState()
    val isAudioConvert by viewModel.isAudioConvert.collectAsState()
    val audioConvertFormat by viewModel.audioConvertFormat.collectAsState()
    val audioFormat by viewModel.audioFormat.collectAsState()
    val audioQuality by viewModel.audioQuality.collectAsState()
    val isEmbedMetadata by viewModel.isEmbedMetadata.collectAsState()
    val isCropArtwork by viewModel.isCropArtwork.collectAsState()
    val videoFormat by viewModel.videoFormat.collectAsState()
    val videoQuality by viewModel.videoQuality.collectAsState()
    val isMergeToMkv by viewModel.isMergeToMkv.collectAsState()
    val isFormatSorting by viewModel.isFormatSorting.collectAsState()
    val formatSortingFields by viewModel.formatSortingFields.collectAsState()
    val isFormatSelection by viewModel.isFormatSelection.collectAsState()
    val isVideoClip by viewModel.isVideoClip.collectAsState()
    val isMergeMultiAudio by viewModel.isMergeMultiAudio.collectAsState()
    val isSubtitle by viewModel.isSubtitle.collectAsState()
    val isEmbedSubtitle by viewModel.isEmbedSubtitle.collectAsState()

    var showAudioFormatDialog by remember { mutableStateOf(false) }
    var showAudioQualityDialog by remember { mutableStateOf(false) }
    var showAudioConvertDialog by remember { mutableStateOf(false) }
    var showVideoFormatDialog by remember { mutableStateOf(false) }
    var showVideoQualityDialog by remember { mutableStateOf(false) }
    var showFormatSorterDialog by remember { mutableStateOf(false) }
    var showVideoClipDialog by remember { mutableStateOf(false) }
    var showMergeAudioDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Format",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // ─── Audio Section ──────────────────────────────────────────────
                SectionHeader(text = "Audio")

                SettingsCard {
                    // Extract Audio
                    FormatSwitchRow(
                        icon = Icons.Outlined.MusicNote,
                        iconTint = Color(0xFFE91E63),
                        title = "Save as audio",
                        subtitle = "Extract audio from video files",
                        checked = isExtractAudio,
                        onCheckedChange = { viewModel.isExtractAudio.value = it }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Convert audio format
                    FormatSwitchWithDetailRow(
                        icon = Icons.Outlined.Sync,
                        iconTint = Color(0xFFFF9800),
                        title = "Convert audio format",
                        subtitle = when (audioConvertFormat) {
                            0 -> "Convert to MP3"
                            1 -> "Convert to M4A"
                            else -> "Not specified"
                        },
                        checked = isAudioConvert,
                        enabled = isExtractAudio,
                        onCheckedChange = { viewModel.isAudioConvert.value = it },
                        onClick = { showAudioConvertDialog = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Embed metadata
                    FormatSwitchRow(
                        icon = Icons.Outlined.ArtTrack,
                        iconTint = Color(0xFF9C27B0),
                        title = "Embed metadata",
                        subtitle = "Embed metadata and thumbnail to audio file",
                        checked = isEmbedMetadata,
                        enabled = isExtractAudio,
                        onCheckedChange = { viewModel.isEmbedMetadata.value = it }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Crop artwork
                    FormatSwitchRow(
                        icon = Icons.Outlined.Crop,
                        iconTint = Color(0xFF4CAF50),
                        title = "Crop artwork",
                        subtitle = "Remove borders from cover art",
                        checked = isCropArtwork,
                        enabled = isEmbedMetadata && isExtractAudio,
                        onCheckedChange = { viewModel.isCropArtwork.value = it }
                    )
                }

                // ─── Video Section ──────────────────────────────────────────────
                SectionHeader(text = "Video")

                SettingsCard {
                    // Preferred video format
                    FormatDetailRow(
                        icon = Icons.Outlined.VideoFile,
                        iconTint = Color(0xFF2196F3),
                        title = "Preferred video format",
                        subtitle = if (videoFormat == FORMAT_COMPATIBILITY) "MP4 (H.264) for sharing"
                                   else "AV1, VP9 or H.265 for quality",
                        enabled = !isExtractAudio && !isFormatSorting,
                        onClick = { showVideoFormatDialog = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Video quality
                    FormatDetailRow(
                        icon = Icons.Outlined.HighQuality,
                        iconTint = Color(0xFFFF5722),
                        title = "Video quality",
                        subtitle = videoQualityLabel(videoQuality),
                        enabled = !isExtractAudio && !isFormatSorting,
                        onClick = { showVideoQualityDialog = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Remux to MKV
                    FormatSwitchRow(
                        icon = Icons.Outlined.Movie,
                        iconTint = Color(0xFF795548),
                        title = "Remux to MKV",
                        subtitle = "Merge video and audio to MKV container",
                        checked = isMergeToMkv || (isSubtitle && isEmbedSubtitle),
                        enabled = !((isSubtitle && isEmbedSubtitle) || isExtractAudio),
                        onCheckedChange = { viewModel.isMergeToMkv.value = it }
                    )
                }

                // ─── Advanced Section ───────────────────────────────────────────
                SectionHeader(text = "Advanced")

                SettingsCard {
                    // Subtitle
                    FormatDetailRow(
                        icon = Icons.Outlined.Subtitles,
                        iconTint = Color(0xFF607D8B),
                        title = "Subtitle",
                        subtitle = "Subtitle languages, embed, auto captions",
                        onClick = { showSubtitle = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Format sorting
                    FormatSwitchWithDetailRow(
                        icon = Icons.Outlined.Sort,
                        iconTint = Color(0xFF673AB7),
                        title = "Format sorting",
                        subtitle = if (formatSortingFields.isNotEmpty()) formatSortingFields
                                   else "Sort formats using yt-dlp -S option",
                        checked = isFormatSorting,
                        onCheckedChange = { viewModel.isFormatSorting.value = it },
                        onClick = { showFormatSorterDialog = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Format selection
                    FormatSwitchRow(
                        icon = Icons.Outlined.VideoSettings,
                        iconTint = Color(0xFF009688),
                        title = "Format selection",
                        subtitle = "Select format before download",
                        checked = isFormatSelection,
                        onCheckedChange = { viewModel.isFormatSelection.value = it }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Clip video
                    FormatSwitchRow(
                        icon = Icons.Outlined.ContentCut,
                        iconTint = Color(0xFFE91E63),
                        title = "Clip video (Experimental)",
                        subtitle = "Make video clips in format selection",
                        checked = isVideoClip,
                        enabled = isFormatSelection,
                        onCheckedChange = {
                            if (!isVideoClip) showVideoClipDialog = true
                            else viewModel.isVideoClip.value = false
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Merge multiple audio streams
                    FormatSwitchRow(
                        icon = Icons.Outlined.SpatialAudioOff,
                        iconTint = Color(0xFF3F51B5),
                        title = "Merge audio streams (Experimental)",
                        subtitle = "Merge multiple audio streams into one file",
                        checked = isMergeMultiAudio,
                        enabled = isFormatSelection,
                        onCheckedChange = {
                            if (!isMergeMultiAudio) showMergeAudioDialog = true
                            else viewModel.isMergeMultiAudio.value = false
                        }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    )

    // ─── Dialogs ─────────────────────────────────────────────────────────────
    if (showAudioFormatDialog) AudioFormatDialog { showAudioFormatDialog = false }
    if (showAudioQualityDialog) AudioQualityDialog { showAudioQualityDialog = false }
    if (showAudioConvertDialog) {
        AudioConversionDialog(
            onDismiss = { showAudioConvertDialog = false },
            currentFormat = audioConvertFormat,
            onConfirm = { viewModel.audioConvertFormat.value = it }
        )
    }
    if (showVideoFormatDialog) {
        VideoFormatDialog(
            currentFormat = videoFormat,
            onDismiss = { showVideoFormatDialog = false },
            onConfirm = { viewModel.videoFormat.value = it }
        )
    }
    if (showVideoQualityDialog) {
        VideoQualityDialog(
            currentQuality = videoQuality,
            onDismiss = { showVideoQualityDialog = false },
            onConfirm = { viewModel.videoQuality.value = it }
        )
    }
    if (showFormatSorterDialog) {
        FormatSortingDialog(
            fields = formatSortingFields,
            onDismiss = { showFormatSorterDialog = false },
            onConfirm = { viewModel.formatSortingFields.value = it }
        )
    }
    if (showVideoClipDialog) {
        AlertDialog(
            onDismissRequest = { showVideoClipDialog = false },
            icon = { Icon(Icons.Outlined.ContentCut, null) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.isVideoClip.value = true
                    showVideoClipDialog = false
                }) { Text("Enable") }
            },
            dismissButton = {
                TextButton(onClick = { showVideoClipDialog = false }) { Text("Cancel") }
            },
            title = { Text("Enable experimental feature") },
            text = { Text("Downloads using this feature will be delegated to FFmpeg. Videos with multiple audio streams may experience issues.") }
        )
    }
    if (showMergeAudioDialog) {
        AlertDialog(
            onDismissRequest = { showMergeAudioDialog = false },
            icon = { Icon(Icons.Outlined.SpatialAudioOff, null) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.isMergeMultiAudio.value = true
                    showMergeAudioDialog = false
                }) { Text("Enable") }
            },
            dismissButton = {
                TextButton(onClick = { showMergeAudioDialog = false }) { Text("Cancel") }
            },
            title = { Text("Enable experimental feature") },
            text = { Text("Merge multiple audio streams into a single file with FFmpeg.") }
        )
    }
}

private fun videoQualityLabel(quality: Int): String = when (quality) {
    0 -> "Best quality"
    1 -> "2160p (4K)"
    2 -> "1440p (2K)"
    3 -> "1080p (Full HD)"
    4 -> "720p (HD)"
    5 -> "480p"
    6 -> "360p"
    7 -> "Lowest quality"
    else -> "Not specified"
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) { Column(modifier = Modifier.padding(vertical = 4.dp), content = content) }
}

@Composable
private fun FormatSwitchRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.7f else 0.3f)
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun FormatSwitchWithDetailRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.7f else 0.3f)
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "Configure",
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            )
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForwardIos,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun FormatDetailRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.7f else 0.3f)
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ─── Dialogs ────────────────────────────────────────────────────────────────

@Composable
private fun AudioFormatDialog(onDismiss: () -> Unit) {
    SimpleChoiceDialog(
        title = "Audio format",
        options = listOf("Not specified", "Opus", "M4A"),
        onDismiss = onDismiss
    )
}

@Composable
private fun AudioQualityDialog(onDismiss: () -> Unit) {
    SimpleChoiceDialog(
        title = "Audio quality",
        options = listOf("Best quality", "192 Kbps", "128 Kbps", "64 Kbps", "32 Kbps", "Lowest bitrate"),
        onDismiss = onDismiss
    )
}

@Composable
private fun AudioConversionDialog(
    onDismiss: () -> Unit,
    currentFormat: Int,
    onConfirm: (Int) -> Unit
) {
    var selected by remember { mutableIntStateOf(currentFormat) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Convert audio format") },
        text = {
            Column {
                AudioConversionOption(0, "Convert to MP3", selected) { selected = 0 }
                AudioConversionOption(1, "Convert to M4A", selected) { selected = 1 }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AudioConversionOption(value: Int, label: String, current: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        if (value == current) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun VideoFormatDialog(
    currentFormat: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selected by remember { mutableIntStateOf(currentFormat) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video format preference") },
        text = {
            Column {
                VideoFormatOption(FORMAT_COMPATIBILITY, "MP4 (H.264) for sharing", selected) { selected = FORMAT_COMPATIBILITY }
                VideoFormatOption(FORMAT_QUALITY, "AV1, VP9 or H.265 for quality", selected) { selected = FORMAT_QUALITY }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun VideoFormatOption(value: Int, label: String, current: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.bodyMedium) }
        if (value == current) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun VideoQualityDialog(
    currentQuality: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selected by remember { mutableIntStateOf(currentQuality) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video quality") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                val qualities = listOf(
                    0 to "Best quality", 1 to "2160p (4K)", 2 to "1440p (2K)",
                    3 to "1080p (Full HD)", 4 to "720p (HD)", 5 to "480p",
                    6 to "360p", 7 to "Lowest quality"
                )
                qualities.forEach { (value, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { selected = value }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                        if (value == selected) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun FormatSortingDialog(
    fields: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(fields) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Format sorting") },
        text = {
            Column {
                Text(
                    "Enter yt-dlp format sorting fields (-S option)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g., res,codec:av1") }
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SimpleChoiceDialog(
    title: String,
    options: List<String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Text(option, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}
