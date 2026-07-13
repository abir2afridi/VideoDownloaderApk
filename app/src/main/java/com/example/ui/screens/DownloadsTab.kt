package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.unit.sp
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "MANAGER",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                        )
                        Text(
                            text = "Downloads",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-1).sp
                            )
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val themeIcon = when (viewModel.selectedThemeMode.collectAsState().value) {
                            "Light" -> Icons.Default.LightMode
                            "Dark" -> Icons.Default.DarkMode
                            else -> Icons.Default.BrightnessAuto
                        }
                        Surface(
                            onClick = {
                                val modes = listOf("System", "Light", "Dark")
                                val current = viewModel.selectedThemeMode.value
                                val next = (modes.indexOf(current) + 1) % modes.size
                                viewModel.selectedThemeMode.value = modes[next]
                            },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = CircleShape
                        ) {
                            Box(modifier = Modifier.padding(10.dp)) {
                                Icon(
                                    imageVector = themeIcon,
                                    contentDescription = "Toggle theme",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                viewModel.runImmediateIntegrityCheck()
                                Toast.makeText(context, "Verifying streams...", Toast.LENGTH_SHORT).show()
                            },
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = CircleShape
                        ) {
                            Box(modifier = Modifier.padding(10.dp)) {
                                Icon(
                                    imageVector = Icons.Default.HealthAndSafety,
                                    contentDescription = "Verify All",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = 24.dp)
        ) {
            // Bulk Actions Bar
            if (downloads.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hasActive = activeQueue.any { it.status == "DOWNLOADING" || it.status == "QUEUED" }
                    val hasPausedOrFailed = downloads.any { it.status == "PAUSED" || it.status == "FAILED" }

                    if (hasActive) {
                        BulkActionButton(
                            onClick = { viewModel.pauseAllDownloads() },
                            icon = Icons.Default.Pause,
                            label = "Pause All",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    if (hasPausedOrFailed) {
                        BulkActionButton(
                            onClick = { viewModel.resumeAllDownloads() },
                            icon = Icons.Default.PlayArrow,
                            label = "Resume All",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (downloads.any { it.status == "FAILED" }) {
                        BulkActionButton(
                            onClick = { viewModel.retryAllFailed() },
                            icon = Icons.Default.Refresh,
                            label = "Retry All",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Surface(
                        onClick = { viewModel.deleteAllDownloads() },
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Delete All", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // Total Queue Summary
            val totalActiveSpeed = activeQueue.sumOf { it.speed }
            if (activeQueue.size > 1 && totalActiveSpeed > 0) {
                val totalRemaining = MediaUtils.getEstimatedRemainingTime(
                    activeQueue.sumOf { it.totalBytes },
                    activeQueue.sumOf { it.downloadedBytes },
                    totalActiveSpeed
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "QUEUE SUMMARY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp
                            ),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "${MediaUtils.formatSpeed(totalActiveSpeed)} total bandwidth • $totalRemaining left",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }


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
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("download_row_${item.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, tint) = when (item.category) {
                "Video" -> Icons.Default.Movie to Color(0xFFE91E63)
                "Audio" -> Icons.Default.Audiotrack to Color(0xFF2196F3)
                "Images" -> Icons.Default.Image to Color(0xFF4CAF50)
                else -> Icons.Default.Description to Color(0xFF9C27B0)
            }
            
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
            }

            Spacer(modifier = Modifier.width(14.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${item.status} • ${if (item.totalBytes > 0) MediaUtils.formatBytes(item.totalBytes) else "Unknown"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
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
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(controlIcon, contentDescription = "PlayPause", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
                
                IconButton(
                    onClick = { viewModel.deleteDownload(item.id) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                }
            }
        }

        if (item.status == "DOWNLOADING" || item.status == "PAUSED" || item.status == "FAILED") {
            Spacer(modifier = Modifier.height(10.dp))
            
            val progress by animateFloatAsState(
                targetValue = if (item.totalBytes > 0) item.downloadedBytes.toFloat() / item.totalBytes.toFloat() else 0f,
                label = "download_progress"
            )
            
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape),
                color = when (item.status) {
                    "FAILED" -> MaterialTheme.colorScheme.error
                    "PAUSED" -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${MediaUtils.formatBytes(item.downloadedBytes)} / ${if (item.totalBytes > 0) MediaUtils.formatBytes(item.totalBytes) else "..."}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                
                if (item.status == "DOWNLOADING") {
                    val speedText = MediaUtils.formatSpeed(item.speed)
                    val timeText = MediaUtils.getEstimatedRemainingTime(item.totalBytes, item.downloadedBytes, item.speed)
                    Text(
                        text = "$speedText • $timeText",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        if (item.integrityStatus != "OK" && item.integrityStatus != null) {
            Spacer(modifier = Modifier.height(6.dp))
            DownloadHealthIndicators(integrityStatus = item.integrityStatus, connectionHealth = item.connectionHealth)
        }
        
        HorizontalDivider(
            modifier = Modifier.padding(top = 12.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    }
}

@Composable
fun CompletedDownloadItemRow(item: DownloadEntity, viewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("completed_download_row_${item.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, tint) = when (item.category) {
                "Video" -> Icons.Default.Movie to Color(0xFFE91E63)
                "Audio" -> Icons.Default.Audiotrack to Color(0xFF2196F3)
                "Images" -> Icons.Default.Image to Color(0xFF4CAF50)
                else -> Icons.Default.Description to Color(0xFF9C27B0)
            }
            
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            }
            
            Spacer(modifier = Modifier.width(14.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = MediaUtils.formatBytes(item.totalBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = " • ${MediaUtils.getRelativeTime(item.timestamp)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            
            IconButton(onClick = { viewModel.deleteDownload(item.id) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )
    }
}



@Composable
fun BulkActionButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color
) {
    Surface(
        onClick = onClick,
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
        }
    }
}
