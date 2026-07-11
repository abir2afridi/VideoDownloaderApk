package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.database.DownloadEntity
import com.example.data.download.MediaUtils
import com.example.ui.viewmodel.MainViewModel

import androidx.compose.foundation.BorderStroke
import com.example.ui.components.TabHeader
import com.example.ui.components.DownloadHealthIndicators

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsTab(viewModel: MainViewModel) {
    val context = LocalContext.current
    val downloads by viewModel.publicDownloads.collectAsState()
    
    val activeQueue = downloads.filter { it.status != "COMPLETED" }
    val completedQueue = downloads.filter { it.status == "COMPLETED" }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                TabHeader(
                    category = "Vortex Pro",
                    title = "Downloads",
                    actionContent = {
                        IconButton(
                            onClick = {
                                viewModel.runImmediateIntegrityCheck()
                                Toast.makeText(context, "Running download integrity & connectivity checks...", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.HealthAndSafety,
                                contentDescription = "Verify All Downloads",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            val isWifiOnly by viewModel.isWifiOnly.collectAsState()
            if (isWifiOnly) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = "WiFi", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Wi-Fi Only enabled. Active downloads pause on cell networks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            if (activeQueue.isEmpty() && completedQueue.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No active downloads", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Search or enter video URLs in the browser tab to download immediately.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 32.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (activeQueue.isNotEmpty()) {
                        item {
                            Text("Active Downloads (${activeQueue.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        items(activeQueue, key = { it.id }) { item ->
                            DownloadItemRow(item, viewModel)
                        }
                    }

                    if (completedQueue.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Recently Downloaded (${completedQueue.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        items(completedQueue, key = { it.id }) { item ->
                            CompletedDownloadItemRow(item, viewModel)
                        }
                    }
                }
            }
        }

    }
}

@Composable
fun DownloadItemRow(item: DownloadEntity, viewModel: MainViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("download_row_${item.id}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = when (item.category) {
                    "Video" -> Icons.Default.Movie
                    "Audio" -> Icons.Default.Audiotrack
                    "Images" -> Icons.Default.Image
                    else -> Icons.Default.Description
                }
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.filename,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = { viewModel.deleteDownload(item.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val progress = if (item.totalBytes > 0) item.downloadedBytes.toFloat() / item.totalBytes.toFloat() else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = when (item.status) {
                    "FAILED" -> Color.Red
                    "PAUSED" -> Color.Gray
                    else -> MaterialTheme.colorScheme.primary
                }
            )

            Spacer(modifier = Modifier.height(4.dp))
            DownloadHealthIndicators(integrityStatus = item.integrityStatus, connectionHealth = item.connectionHealth)

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${MediaUtils.formatBytes(item.downloadedBytes)} / ${if (item.totalBytes > 0) MediaUtils.formatBytes(item.totalBytes) else "Unknown"}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (item.status == "DOWNLOADING" && item.speed > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "•  ${MediaUtils.formatSpeed(item.speed)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row {
                        Text(
                            text = "Status: ${item.status}",
                            style = MaterialTheme.typography.bodySmall,
                            color = when (item.status) {
                                "FAILED" -> Color.Red
                                "DOWNLOADING" -> MaterialTheme.colorScheme.primary
                                else -> Color.Gray
                            }
                        )
                        if (item.status == "DOWNLOADING" && item.totalBytes > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "•  Rem: ${MediaUtils.getEstimatedRemainingTime(item.totalBytes, item.downloadedBytes, item.speed)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val controlIcon = when (item.status) {
                        "DOWNLOADING", "QUEUED" -> Icons.Default.Pause
                        else -> Icons.Default.PlayArrow
                    }
                    IconButton(
                        onClick = {
                            if (item.status == "DOWNLOADING" || item.status == "QUEUED") {
                                viewModel.pauseDownload(item.id)
                            } else {
                                viewModel.resumeDownload(item.id)
                            }
                        },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape)
                            .testTag("pause_resume_btn")
                    ) {
                        Icon(controlIcon, contentDescription = "PlayPause", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun CompletedDownloadItemRow(item: DownloadEntity, viewModel: MainViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("completed_download_row_${item.id}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when (item.category) {
                "Video" -> Icons.Default.Movie
                "Audio" -> Icons.Default.Audiotrack
                "Images" -> Icons.Default.Image
                else -> Icons.Default.Description
            }
            Icon(icon, contentDescription = null, tint = Color.Gray)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.filename}  •  ${MediaUtils.formatBytes(item.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                DownloadHealthIndicators(integrityStatus = item.integrityStatus, connectionHealth = item.connectionHealth)
            }
            IconButton(onClick = { viewModel.deleteDownload(item.id) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
            }
        }
    }
}
