package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.database.DownloadEntity
import com.example.data.download.MediaUtils
import com.example.ui.viewmodel.MainViewModel
import java.io.File
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import com.example.ui.components.TabHeader
import com.example.ui.components.DownloadHealthIndicators
import com.example.ui.components.VideoPlayerDialog
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DownloadsTab(viewModel: MainViewModel, onNavigateToHome: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloads by viewModel.publicDownloads.collectAsState()

    val activeQueue = downloads.filter { it.status != "COMPLETED" }
    val completedQueue = downloads.filter { it.status == "COMPLETED" }
    val completedFiles = downloads.filter { it.status == "COMPLETED" }

    val tabTitles = listOf("Downloads", "Files")
    val pagerState = rememberPagerState(pageCount = { tabTitles.size })

    // Downloads selection
    var isDownloadsSelectionMode by remember { mutableStateOf(false) }
    val downloadSelectedIds = remember { mutableStateListOf<Int>() }

    // Files selection & state
    var isFilesSelectionMode by remember { mutableStateOf(false) }
    val fileSelectedIds = remember { mutableStateListOf<Int>() }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var activePlayingFilePath by remember { mutableStateOf<String?>(null) }
    var selectedMetadataItem by remember { mutableStateOf<DownloadEntity?>(null) }

    val categories = listOf("All", "Video", "Audio", "Images", "Other")
    val filteredFiles = if (selectedCategoryFilter == "All") completedFiles else {
        completedFiles.filter { it.category == selectedCategoryFilter }
    }

    val isSelectionMode = isDownloadsSelectionMode || isFilesSelectionMode

    // Back: selection → unselect → Files tab → Downloads tab → Home
    BackHandler(enabled = isSelectionMode || pagerState.currentPage == 1) {
        if (isSelectionMode) {
            isDownloadsSelectionMode = false
            isFilesSelectionMode = false
            downloadSelectedIds.clear()
            fileSelectedIds.clear()
        } else if (pagerState.currentPage == 1) {
            scope.launch { pagerState.animateScrollToPage(0) }
        }
    }
    BackHandler(enabled = pagerState.currentPage == 0 && !isSelectionMode) {
        onNavigateToHome()
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                Surface(
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                ) {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        TopAppBar(
                            title = {
                                Text(
                                    "${(downloadSelectedIds + fileSelectedIds).size} Selected",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            },
                            navigationIcon = {
                                Row {
                                    IconButton(onClick = {
                                        isDownloadsSelectionMode = false
                                        isFilesSelectionMode = false
                                        downloadSelectedIds.clear()
                                        fileSelectedIds.clear()
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                                    }
                                    // Select All / Deselect All
                                    val allIds = when {
                                        isDownloadsSelectionMode -> (downloads.map { it.id })
                                        isFilesSelectionMode -> (completedFiles.map { it.id })
                                        else -> emptyList()
                                    }
                                    val selectedIds = downloadSelectedIds + fileSelectedIds
                                    val allSelected = allIds.isNotEmpty() && selectedIds.containsAll(allIds)
                                    val selectAllLabel = if (allSelected) "Deselect All" else "Select All"
                                    val selectAllIcon = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll
                                    IconButton(onClick = {
                                        if (allSelected) {
                                            downloadSelectedIds.clear()
                                            fileSelectedIds.clear()
                                        } else {
                                            if (isDownloadsSelectionMode) {
                                                downloadSelectedIds.clear()
                                                downloadSelectedIds.addAll(downloads.map { it.id })
                                            }
                                            if (isFilesSelectionMode) {
                                                fileSelectedIds.clear()
                                                fileSelectedIds.addAll(completedFiles.map { it.id })
                                            }
                                        }
                                    }) {
                                        Icon(selectAllIcon, contentDescription = selectAllLabel)
                                    }
                                }
                            },
                            actions = {
                                // Share — works for both tabs
                                if ((isDownloadsSelectionMode && downloadSelectedIds.isNotEmpty()) ||
                                    (isFilesSelectionMode && fileSelectedIds.isNotEmpty())
                                ) {
                                    val shareIds = (downloadSelectedIds + fileSelectedIds).toList()
                                    IconButton(onClick = {
                                        val allItems = downloads + completedFiles
                                        val filesToShare = allItems.filter { it.id in shareIds }.map { File(it.filepath) }.filter { it.exists() }
                                        if (filesToShare.isNotEmpty()) shareMultipleFiles(context, filesToShare)
                                        isDownloadsSelectionMode = false
                                        isFilesSelectionMode = false
                                        downloadSelectedIds.clear()
                                        fileSelectedIds.clear()
                                    }) {
                                        Icon(Icons.Default.Share, contentDescription = "Share Selected")
                                    }
                                }
                                IconButton(onClick = {
                                    (downloadSelectedIds + fileSelectedIds).forEach { id -> viewModel.deleteDownload(id) }
                                    isDownloadsSelectionMode = false
                                    isFilesSelectionMode = false
                                    downloadSelectedIds.clear()
                                    fileSelectedIds.clear()
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            } else {
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
                            val headerLabel = when (pagerState.currentPage) {
                                0 -> "MANAGER"
                                1 -> "LIBRARY"
                                else -> "MANAGER"
                            }
                            val headerTitle = when (pagerState.currentPage) {
                                0 -> "Downloads"
                                1 -> "Files"
                                else -> "Downloads"
                            }
                            Text(
                                text = headerLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.sp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                )
                            )
                            Text(
                                text = headerTitle,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-1).sp
                                )
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val showChecklist = when (pagerState.currentPage) {
                                0 -> downloads.isNotEmpty()
                                1 -> completedFiles.isNotEmpty()
                                else -> false
                            }
                            if (showChecklist) {
                                IconButton(onClick = {
                                    if (pagerState.currentPage == 0) isDownloadsSelectionMode = true
                                    else isFilesSelectionMode = true
                                }) {
                                    Icon(Icons.Default.Checklist, contentDescription = "Select items", tint = MaterialTheme.colorScheme.primary)
                                }
                            }

                            val themeIcon = when (viewModel.selectedThemeMode.collectAsState().value) {
                                "Light" -> Icons.Default.LightMode
                                else -> Icons.Default.DarkMode
                            }
                            Surface(
                                onClick = {
                                    val modes = listOf("Light", "Dark")
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

                            if (pagerState.currentPage == 0) {
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
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val libComposition by rememberLottieComposition(
                    LottieCompositionSpec.Url("https://lottie.host/80a92798-0341-4b0d-809a-9e1bca596503/Go2hkL4uF9.lottie")
                )
                LottieAnimation(
                    composition = libComposition,
                    iterations = LottieConstants.IterateForever,
                    speed = 1f,
                    modifier = Modifier.size(80.dp)
                )
            }

            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                            isDownloadsSelectionMode = false
                            isFilesSelectionMode = false
                            downloadSelectedIds.clear()
                            fileSelectedIds.clear()
                        },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> DownloadsContent(
                        context = context,
                        activeQueue = activeQueue,
                        completedQueue = completedQueue,
                        isSelectionMode = isDownloadsSelectionMode,
                        selectedIds = downloadSelectedIds,
                        onToggleSelectionMode = { isDownloadsSelectionMode = it },
                        viewModel = viewModel
                    )
                    1 -> FilesContent(
                        context = context,
                        scope = scope,
                        completedFiles = completedFiles,
                        filteredFiles = filteredFiles,
                        categories = categories,
                        selectedCategoryFilter = selectedCategoryFilter,
                        onCategoryFilterChange = { selectedCategoryFilter = it },
                        isSelectionMode = isFilesSelectionMode,
                        selectedIds = fileSelectedIds,
                        onToggleSelectionMode = { isFilesSelectionMode = it },
                        activePlayingFilePath = activePlayingFilePath,
                        onPlayVideo = { activePlayingFilePath = it },
                        selectedMetadataItem = selectedMetadataItem,
                        onShowMetadata = { selectedMetadataItem = it },
                        viewModel = viewModel
                    )
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
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Button(onClick = { selectedMetadataItem = null }) { Text("Close") }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadsContent(
    context: android.content.Context,
    activeQueue: List<DownloadEntity>,
    completedQueue: List<DownloadEntity>,
    isSelectionMode: Boolean,
    selectedIds: MutableList<Int>,
    onToggleSelectionMode: (Boolean) -> Unit,
    viewModel: MainViewModel
) {
    val allDownloads = activeQueue + completedQueue
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        // Bulk Actions Bar
        if (allDownloads.isNotEmpty() && !isSelectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasActive = activeQueue.any { it.status == "DOWNLOADING" || it.status == "QUEUED" }
                val hasPausedOrFailed = allDownloads.any { it.status == "PAUSED" || it.status == "FAILED" }

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

                if (allDownloads.any { it.status == "FAILED" }) {
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
        val totalRemaining = if (totalActiveSpeed > 0) {
            MediaUtils.getEstimatedRemainingTime(
                activeQueue.sumOf { it.totalBytes },
                activeQueue.sumOf { it.downloadedBytes },
                totalActiveSpeed
            )
        } else "..."

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
                    text = "${MediaUtils.formatSpeed(totalActiveSpeed)} total bandwidth \u2022 $totalRemaining left",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
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
                        textAlign = TextAlign.Center
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
                        val isSelected = selectedIds.contains(item.id)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            if (isSelected) selectedIds.remove(item.id)
                                            else selectedIds.add(item.id)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            onToggleSelectionMode(true)
                                            selectedIds.add(item.id)
                                        }
                                    }
                                )
                        ) {
                            if (isSelectionMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        if (it) selectedIds.add(item.id)
                                        else selectedIds.remove(item.id)
                                    },
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            DownloadItemRow(item, viewModel)
                        }
                    }
                }

                if (completedQueue.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Recently Downloaded (${completedQueue.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(completedQueue, key = { it.id }) { item ->
                        val isSelected = selectedIds.contains(item.id)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            if (isSelected) selectedIds.remove(item.id)
                                            else selectedIds.add(item.id)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            onToggleSelectionMode(true)
                                            selectedIds.add(item.id)
                                        }
                                    }
                                )
                        ) {
                            if (isSelectionMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        if (it) selectedIds.add(item.id)
                                        else selectedIds.remove(item.id)
                                    },
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            CompletedDownloadItemRow(item, viewModel)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilesContent(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    completedFiles: List<DownloadEntity>,
    filteredFiles: List<DownloadEntity>,
    categories: List<String>,
    selectedCategoryFilter: String,
    onCategoryFilterChange: (String) -> Unit,
    isSelectionMode: Boolean,
    selectedIds: MutableList<Int>,
    onToggleSelectionMode: (Boolean) -> Unit,
    activePlayingFilePath: String?,
    onPlayVideo: (String) -> Unit,
    selectedMetadataItem: DownloadEntity?,
    onShowMetadata: (DownloadEntity) -> Unit,
    viewModel: MainViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
                    onClick = { onCategoryFilterChange(cat) },
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
                    val isSelected = selectedIds.contains(item.id)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        if (isSelected) selectedIds.remove(item.id)
                                        else selectedIds.add(item.id)
                                    } else {
                                        if (fileExists) {
                                            if (item.category == "Video") {
                                                onPlayVideo(item.filepath)
                                            } else {
                                                onShowMetadata(item)
                                            }
                                        } else {
                                            Toast.makeText(context, "File was moved or deleted outside the app.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        onToggleSelectionMode(true)
                                        selectedIds.add(item.id)
                                    }
                                }
                            )
                            .padding(vertical = 8.dp, horizontal = if (isSelectionMode) 8.dp else 0.dp)
                            .testTag("file_item_${item.id}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelectionMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        if (it) selectedIds.add(item.id)
                                        else selectedIds.remove(item.id)
                                    },
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }

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
                                    text = "${MediaUtils.formatBytes(item.totalBytes)} \u2022 ${item.mimeType.split("/").last().uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )

                                if (!fileExists) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "\u26A0\uFE0F File missing",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (!isSelectionMode) {
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
                                                onShowMetadata(item)
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
                val displayTitle = if (item.title.isNotBlank() && item.title != "Stream Socket Link") {
                    item.title
                } else {
                    item.filename
                }
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${item.status} \u2022 ${if (item.totalBytes > 0) MediaUtils.formatBytes(item.totalBytes) else "Unknown"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.status == "FAILED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                if (item.status == "FAILED" && !item.errorMessage.isNullOrBlank()) {
                    Text(
                        text = item.errorMessage!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                        text = "$speedText \u2022 $timeText",
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
                val displayTitle = if (item.title.isNotBlank() && item.title != "Stream Socket Link") {
                    item.title
                } else {
                    item.filename
                }
                Text(
                    text = displayTitle,
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
                        text = " \u2022 ${MediaUtils.getRelativeTime(item.timestamp)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            val context = LocalContext.current
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Actions", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            val file = File(item.filepath)
                            if (file.exists()) {
                                shareFile(context, file, item.mimeType)
                            } else {
                                Toast.makeText(context, "File missing on disk", Toast.LENGTH_SHORT).show()
                            }
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            viewModel.deleteDownload(item.id)
                            showMenu = false
                        }
                    )
                }
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

private fun shareMultipleFiles(context: android.content.Context, files: List<File>) {
    try {
        val uris = ArrayList<Uri>()
        files.forEach { file ->
            uris.add(FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            ))
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Media Files"))
    } catch (e: Exception) {
        Toast.makeText(context, "Sharing failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}


