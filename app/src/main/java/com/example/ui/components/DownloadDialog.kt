package com.example.ui.components

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.download.TikTokVideoData
import com.example.data.download.VideoExtractor
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class DownloadDialogState {
    data object InputUrl : DownloadDialogState()
    data class Loading(val url: String) : DownloadDialogState()
    data class Ready(
        val url: String,
        val info: TikTokVideoData,
        val hasAudio: Boolean,
        val hasVideo: Boolean,
    ) : DownloadDialogState()
    data class Error(val url: String, val message: String) : DownloadDialogState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadDialog(
    initialUrl: String = "",
    showDialog: Boolean,
    onDismiss: () -> Unit,
    viewModel: MainViewModel = viewModel(),
) {
    if (!showDialog) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var state by remember(initialUrl) { mutableStateOf<DownloadDialogState>(DownloadDialogState.InputUrl) }
    var urlText by remember(initialUrl) { mutableStateOf(initialUrl) }
    var isAudioOnly by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var isDownloading by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableIntStateOf(0) }

    LaunchedEffect(initialUrl) {
        if (initialUrl.isNotBlank()) {
            urlText = initialUrl
            state = DownloadDialogState.Loading(initialUrl)
            resolveUrl(initialUrl) { newState ->
                state = newState
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            when (val currentState = state) {
                is DownloadDialogState.InputUrl -> {
                    InputUrlContent(
                        urlText = urlText,
                        onUrlChange = { urlText = it },
                        onProceed = {
                            val url = urlText.trim()
                            if (url.isNotBlank()) {
                                state = DownloadDialogState.Loading(url)
                                scope.launch {
                                    resolveUrl(url) { newState ->
                                        state = newState
                                    }
                                }
                            }
                        }
                    )
                }

                is DownloadDialogState.Loading -> {
                    LoadingContent()
                }

                is DownloadDialogState.Ready -> {
                    ReadyContent(
                        info = currentState.info,
                        url = currentState.url,
                        isAudioOnly = isAudioOnly,
                        selectedQuality = selectedQuality,
                        isDownloading = isDownloading,
                        downloadProgress = downloadProgress,
                        hasAudio = currentState.hasAudio,
                        hasVideo = currentState.hasVideo,
                        onAudioOnlyToggle = { isAudioOnly = it },
                        onQualitySelect = { selectedQuality = it },
                        onDownload = {
                            isDownloading = true
                            scope.launch {
                                try {
                                    val downloadUrl = if (isAudioOnly) {
                                        currentState.info.audioUrl ?: currentState.info.videoUrl
                                    } else {
                                        currentState.info.videoUrlNoWatermark ?: currentState.info.videoUrl
                                    }
                                    if (downloadUrl != null) {
                                        viewModel.addDownload(
                                            url = downloadUrl,
                                            suggestedTitle = currentState.info.title.ifBlank { "Video" },
                                            quality = if (selectedQuality == 0) "Auto" else "HD",
                                            isAudioOnly = isAudioOnly,
                                            customHeaders = currentState.info.httpHeaders,
                                            sourceUrl = currentState.info.sourceUrl
                                        )
                                        onDismiss()
                                        Toast.makeText(context, "Download queued", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "No download URL available", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Log.e("DownloadDialog", "Download failed", e)
                                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isDownloading = false
                                }
                            }
                        },
                        onDismiss = onDismiss
                    )
                }

                is DownloadDialogState.Error -> {
                    ErrorContent(
                        url = currentState.url,
                        message = currentState.message,
                        onRetry = {
                            state = DownloadDialogState.Loading(currentState.url)
                            scope.launch {
                                resolveUrl(currentState.url) { newState ->
                                    state = newState
                                }
                            }
                        },
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

private fun isDirectMediaUrl(url: String): Boolean {
    val path = try { android.net.Uri.parse(url).path?.lowercase() ?: url.lowercase() } catch (_: Exception) { url.lowercase() }
    return path.endsWith(".mp4") || path.endsWith(".m3u8") || path.endsWith(".webm") ||
           path.endsWith(".mkv") || path.endsWith(".avi") || path.endsWith(".mov") ||
           path.endsWith(".flv") || path.endsWith(".3gp") || path.endsWith(".ts") ||
           path.endsWith(".mp3") || path.endsWith(".m4a") || path.endsWith(".aac") ||
           path.endsWith(".ogg") || path.endsWith(".wav") || path.endsWith(".flac") ||
           path.endsWith(".wma") || path.endsWith(".opus")
}

private suspend fun resolveUrl(url: String, onResult: (DownloadDialogState) -> Unit) {
    try {
        if (isDirectMediaUrl(url)) {
            val info = TikTokVideoData(
                id = "",
                title = url.substringAfterLast("/").substringBefore("?").substringBefore("."),
                author = "",
                authorId = "",
                thumbnail = "",
                duration = 0L,
                videoUrl = url,
                videoUrlNoWatermark = url,
                audioUrl = null
            )
            onResult(
                DownloadDialogState.Ready(
                    url = url,
                    info = info,
                    hasAudio = false,
                    hasVideo = true,
                )
            )
            return
        }

        val result = withContext(Dispatchers.IO) {
            VideoExtractor.extract(url)
        }
        result.fold(
            onSuccess = { info ->
                val hasAudio = !info.audioUrl.isNullOrBlank()
                val hasVideo = !info.videoUrl.isNullOrBlank()
                onResult(
                    DownloadDialogState.Ready(
                        url = url,
                        info = info,
                        hasAudio = hasAudio,
                        hasVideo = hasVideo,
                    )
                )
            },
            onFailure = { error ->
                onResult(
                    DownloadDialogState.Error(
                        url = url,
                        message = error.message ?: "Could not extract video info"
                    )
                )
            }
        )
    } catch (e: Throwable) {
        onResult(
            DownloadDialogState.Error(
                url = url,
                message = e.message ?: "Unknown error"
            )
        )
    }
}

@Composable
private fun InputUrlContent(
    urlText: String,
    onUrlChange: (String) -> Unit,
    onProceed: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        Icon(
            imageVector = Icons.Outlined.ContentPaste,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "New Download",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Paste a media link from any supported platform",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = urlText,
            onValueChange = onUrlChange,
            label = { Text("URL") },
            placeholder = { Text("https://example.com/video") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onProceed,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = urlText.isNotBlank(),
        ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Analyze & Download")
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Analyzing link...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Extracting video information",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun ReadyContent(
    info: TikTokVideoData,
    url: String,
    isAudioOnly: Boolean,
    selectedQuality: Int,
    isDownloading: Boolean,
    downloadProgress: Float,
    hasAudio: Boolean,
    hasVideo: Boolean,
    onAudioOnlyToggle: (Boolean) -> Unit,
    onQualitySelect: (Int) -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val qualities = listOf("Auto", "HD")

    Spacer(Modifier.height(8.dp))

    if (info.thumbnail.isNotBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(info.thumbnail)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(16.dp))
    }

    Text(
        text = info.title.ifBlank { "Untitled Video" },
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )

    if (info.author.isNotBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = info.author,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (info.duration > 0) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatDuration(info.duration),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }

    Spacer(Modifier.height(20.dp))

    // Source info
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Source",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    // Type selection
    if (hasAudio || hasVideo) {
        Text(
            text = "Download Type",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hasVideo) {
                DownloadTypeChip(
                    selected = !isAudioOnly,
                    label = "Video",
                    icon = { Icon(Icons.Outlined.VideoFile, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    onClick = { onAudioOnlyToggle(false) },
                )
            }
            if (hasAudio) {
                DownloadTypeChip(
                    selected = isAudioOnly,
                    label = "Audio",
                    icon = { Icon(Icons.Outlined.Audiotrack, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    onClick = { onAudioOnlyToggle(true) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    // Quality selection
    Text(
        text = "Quality",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        qualities.forEachIndexed { index, quality ->
            DownloadTypeChip(
                selected = selectedQuality == index,
                label = quality,
                icon = {
                    Icon(
                        if (quality == "Auto") Icons.Default.PlayCircle else Icons.Default.VideoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                onClick = { onQualitySelect(index) },
            )
        }
    }

    Spacer(Modifier.height(24.dp))

    // Progress indicator
    AnimatedVisibility(visible = isDownloading) {
        Column(modifier = Modifier.animateContentSize()) {
            LinearProgressIndicator(
                progress = { downloadProgress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                strokeCap = StrokeCap.Round,
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    // Download button
    Button(
        onClick = onDownload,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        enabled = !isDownloading,
    ) {
        Icon(
            if (isAudioOnly) Icons.Default.MusicNote else Icons.Default.Download,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(if (isAudioOnly) "Download Audio" else "Download Video")
    }

    Spacer(Modifier.height(8.dp))

    TextButton(
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Cancel")
    }
}

@Composable
private fun ErrorContent(
    url: String,
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Extraction Failed",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Retry")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDismiss) {
            Text("Cancel")
        }
    }
}

@Composable
private fun DownloadTypeChip(
    selected: Boolean,
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val containerColor = if (selected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceContainerHigh

    val contentColor = if (selected)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        icon()
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}
