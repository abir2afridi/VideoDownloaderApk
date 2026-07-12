package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                TabHeader(
                    category = "Vortex Pro",
                    title = "File Library"
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = selectedCategoryFilter == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategoryFilter = cat },
                        label = { Text(cat) },
                        modifier = Modifier.testTag("filter_chip_$cat")
                    )
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
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No completed downloads", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Downloaded videos and audios in this category will display here.",
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
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(filteredFiles, key = { it.id }) { item ->
                        val file = File(item.filepath)
                        val fileExists = file.exists()

                         Card(
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
                                .testTag("file_item_${item.id}"),
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
                                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                                    if (!fileExists) {
                                        Text(
                                            text = "⚠️ File missing on disk",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Red,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                var showMenu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { showMenu = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Actions")
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("View Details") },
                                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                            onClick = {
                                                selectedMetadataItem = item
                                                showMenu = false
                                            }
                                        )
                                        if (fileExists) {
                                            DropdownMenuItem(
                                                text = { Text("Share File") },
                                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                                onClick = {
                                                    shareFile(context, file, item.mimeType)
                                                    showMenu = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Move to Private Vault") },
                                                leadingIcon = { Icon(Icons.Default.EnhancedEncryption, contentDescription = null) },
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
                                        DropdownMenuItem(
                                            text = { Text("Delete", color = Color.Red) },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                                            onClick = {
                                                viewModel.deleteDownload(item.id)
                                                Toast.makeText(context, "File deleted", Toast.LENGTH_SHORT).show()
                                                showMenu = false
                                            }
                                        )
                                    }
                                }
                            }
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
