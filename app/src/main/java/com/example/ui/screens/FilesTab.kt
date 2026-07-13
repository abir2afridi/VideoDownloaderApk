package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.core.content.FileProvider
import com.example.data.database.DownloadEntity
import com.example.data.download.MediaUtils
import com.example.ui.components.VideoPlayerDialog
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

import androidx.compose.foundation.BorderStroke
import com.example.ui.components.TabHeader
import com.example.ui.components.DownloadHealthIndicators

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesTab(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloads by viewModel.publicDownloads.collectAsState()
    
    val completedFiles = downloads.filter { it.status == "COMPLETED" }

    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var activePlayingFilePath by remember { mutableStateOf<String?>(null) }
    var selectedMetadataItem by remember { mutableStateOf<DownloadEntity?>(null) }

    val categories = listOf("All", "Video", "Audio", "Images", "Other")
    val filteredFiles = if (selectedCategoryFilter == "All") {
        completedFiles
    } else {
        completedFiles.filter { it.category == selectedCategoryFilter }
    }

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
                            text = "LIBRARY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                        )
                        Text(
                            text = "File Library",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-1).sp
                            )
                        )
                    }

                    // Theme Toggle
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = selectedCategoryFilter == cat
                    Surface(
                        onClick = { selectedCategoryFilter = cat },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.testTag("filter_chip_$cat")
                    ) {
                        Text(
                            text = cat,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (filteredFiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No items found", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp)
                ) {
                    items(filteredFiles, key = { it.id }) { item ->
                        val file = File(item.filepath)
                        val fileExists = file.exists()

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (fileExists) {
                                        if (item.category == "Video") {
                                            activePlayingFilePath = item.filepath
                                        } else {
                                            selectedMetadataItem = item
                                        }
                                    } else {
                                        Toast.makeText(context, "File was moved or deleted outside the app.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(vertical = 8.dp)
                                .testTag("file_item_${item.id}")
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
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(tint.copy(alpha = 0.05f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(icon, contentDescription = null, tint = tint.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${MediaUtils.formatBytes(item.totalBytes)} • ${item.mimeType.split("/").last().uppercase()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                    
                                    if (!fileExists) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "⚠️ File missing",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                var showMenu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Actions", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Details") },
                                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                            onClick = {
                                                selectedMetadataItem = item
                                                showMenu = false
                                            }
                                        )
                                        if (fileExists) {
                                            DropdownMenuItem(
                                                text = { Text("Share") },
                                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                                onClick = {
                                                    shareFile(context, file, item.mimeType)
                                                    showMenu = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Private Vault") },
                                                leadingIcon = { Icon(Icons.Default.EnhancedEncryption, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                                onClick = {
                                                    scope.launch {
                                                        val dbInst = com.example.data.database.AppDatabase.getDatabase(context)
                                                        val daoInst = dbInst.downloadDao()
                                                        
                                                        val vaultDir = File(context.filesDir, "vault")
                                                        vaultDir.mkdirs()
                                                        val targetFile = File(vaultDir, item.filename)
                                                        
                                                        val moved = withContext(Dispatchers.IO) {
                                                            file.renameTo(targetFile)
                                                        }
                                                        
                                                        if (moved) {
                                                            daoInst.updateDownload(item.copy(isPrivate = true, filepath = targetFile.absolutePath))
                                                            Toast.makeText(context, "Moved to Secure Vault!", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            Toast.makeText(context, "Move failed", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                    showMenu = false
                                                }
                                            )
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        DropdownMenuItem(
                                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                                            onClick = {
                                                viewModel.deleteDownload(item.id)
                                                Toast.makeText(context, "File deleted", Toast.LENGTH_SHORT).show()
                                                showMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 12.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            }


        }

        activePlayingFilePath?.let { path ->
            VideoPlayerDialog(filePath = path, onDismiss = { activePlayingFilePath = null })
        }

        selectedMetadataItem?.let { item ->
            AlertDialog(
                onDismissRequest = { selectedMetadataItem = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Media Information Panel")
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetadataRow(label = "Title", value = item.title)
                        MetadataRow(label = "File Name", value = item.filename)
                        MetadataRow(label = "Format/Mime", value = item.mimeType)
                        MetadataRow(label = "Size", value = MediaUtils.formatBytes(item.totalBytes))
                        MetadataRow(label = "Local Path", value = item.filepath)
                        MetadataRow(label = "Source URL", value = item.url)
                        MetadataRow(label = "Threads count", value = "${item.threads} threads")
                        MetadataRow(label = "Quality", value = item.quality ?: "Auto")
                    }
                },
                confirmButton = {
                    Button(onClick = { selectedMetadataItem = null }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
fun MetadataRow(label: String, value: String) {
    Column {
        Text(text = label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun shareFile(context: android.content.Context, file: File, mimeType: String) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Media File"))
    } catch (e: Exception) {
        Toast.makeText(context, "Sharing failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
