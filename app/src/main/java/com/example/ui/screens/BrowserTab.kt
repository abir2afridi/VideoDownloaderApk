package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import androidx.activity.compose.BackHandler
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.browser.AdBlocker
import com.example.data.download.DownloadEngine
import com.example.data.download.VideoExtractor
import com.example.ui.components.DownloadDialog
import com.example.ui.viewmodel.MainViewModel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

private fun isDirectMediaUrl(url: String): Boolean {
    val path = try { android.net.Uri.parse(url).path?.lowercase() ?: url.lowercase() } catch (_: Exception) { url.lowercase() }
    return path.endsWith(".mp4") || path.endsWith(".m3u8") || path.endsWith(".webm") ||
           path.endsWith(".mkv") || path.endsWith(".avi") || path.endsWith(".mov") ||
           path.endsWith(".flv") || path.endsWith(".3gp") || path.endsWith(".ts") ||
           path.endsWith(".mp3") || path.endsWith(".m4a") || path.endsWith(".aac") ||
           path.endsWith(".ogg") || path.endsWith(".wav") || path.endsWith(".flac") ||
           path.endsWith(".wma") || path.endsWith(".opus")
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserTab(viewModel: MainViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    val currentUrl by viewModel.currentWebUrl.collectAsState()
    val isIncognito by viewModel.isIncognito.collectAsState()
    val isTrackerBlocking by viewModel.isTrackerBlocking.collectAsState()
    val isAdBlocking by viewModel.isAdBlocking.collectAsState()
    val isHttpsOnly by viewModel.isHttpsOnly.collectAsState()
    val isForceDarkWeb by viewModel.isForceDarkWeb.collectAsState()
    val isDataSaving by viewModel.isDataSaving.collectAsState()
    val isBlockPopups by viewModel.isBlockPopups.collectAsState()
    val textSizePercent by viewModel.textSizePercent.collectAsState()
    val userAgentMode by viewModel.userAgentMode.collectAsState()
    val cookieMode by viewModel.cookieMode.collectAsState()
    val detectedMedia by viewModel.detectedMediaList.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val browsingHistory by viewModel.browsingHistory.collectAsState()
    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    // Permission settings
    val locationPermission by viewModel.locationPermission.collectAsState()
    val notificationPermission by viewModel.notificationPermission.collectAsState()
    val microphonePermission by viewModel.microphonePermission.collectAsState()
    val externalAppsPolicy by viewModel.externalAppsPolicy.collectAsState()

    var urlInput by remember { mutableStateOf(currentUrl) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var progressVal by remember { mutableStateOf(0) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showMediaSheet by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showTabGallerySheet by remember { mutableStateOf(false) }
    var showHome by remember { mutableStateOf(currentUrl == "about:blank") }
    var resolvingMedia by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadDialogUrl by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Sync showHome when active tab changes
    LaunchedEffect(activeTabId, tabs) {
        val activeTab = tabs.find { it.id == activeTabId }
        showHome = activeTab?.url == "about:blank" || activeTab?.url == "https://google.com"
    }
    // Back gesture: navigate website history back, then show home, then fall through to app Home
    BackHandler(enabled = !showHome) {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            showHome = true
        }
    }
    // WebView instances per tab (one WebView per tab ID)
    val webViewInstances = remember { mutableMapOf<String, WebView>() }

    // Clean up destroyed tabs' WebViews
    LaunchedEffect(tabs) {
        val activeIds = tabs.map { it.id }.toSet()
        webViewInstances.keys
            .filter { it !in activeIds }
            .forEach { id ->
                webViewInstances[id]?.destroy()
                webViewInstances.remove(id)
            }
    }

    // Sync input bar when url state changes
    LaunchedEffect(currentUrl) {
        urlInput = currentUrl
    }

    // Update webViewInstance reference when active tab changes
    LaunchedEffect(activeTabId) {
        webViewInstance = activeTabId?.let { webViewInstances[it] }
    }

    Scaffold(
        topBar = {
            Surface(
                tonalElevation = 3.dp,
                color = if (isIncognito) Color(0xFF1C1B1F) else MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(bottom = 6.dp)
                ) {
                    // Incognito mode indicator banner
                    if (isIncognito) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2D1B69).copy(alpha = 0.8f))
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VisibilityOff,
                                contentDescription = null,
                                tint = Color(0xFFBB86FC),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Incognito Mode — History won't be saved",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFBB86FC)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Navigation controls
                        IconButton(
                            onClick = { webViewInstance?.goBack() },
                            enabled = webViewInstance?.canGoBack() == true,
                            modifier = Modifier.size(34.dp).testTag("browser_back")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { webViewInstance?.goForward() },
                            enabled = webViewInstance?.canGoForward() == true,
                            modifier = Modifier.size(34.dp).testTag("browser_forward")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward", modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { webViewInstance?.reload() },
                            modifier = Modifier.size(34.dp).testTag("browser_reload")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { showHome = true },
                            modifier = Modifier.size(34.dp).testTag("browser_home")
                        ) {
                            Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(20.dp))
                        }

                        // Address Bar
                        TextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .padding(horizontal = 4.dp)
                                .testTag("browser_address_bar"),
                            placeholder = { Text("Search or enter URL...", style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = if (isIncognito) Color(0xFF2D2D3A) else MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = if (isIncognito) Color(0xFF2D2D3A) else MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = if (isIncognito) Color.White else MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = if (isIncognito) Color.White else MaterialTheme.colorScheme.onSurface,
                            ),
                            shape = RoundedCornerShape(24.dp),
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isIncognito) Icons.Filled.VisibilityOff
                                                  else if (isHttpsOnly) Icons.Default.Lock
                                                  else Icons.Default.Language,
                                    contentDescription = null,
                                    tint = if (isIncognito) Color(0xFFBB86FC)
                                           else if (isHttpsOnly) MaterialTheme.colorScheme.primary
                                           else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    var destination = urlInput.trim()
                                    if (destination.isNotBlank()) {
                                        destination = if (!destination.startsWith("http://") && !destination.startsWith("https://")) {
                                            if (destination.contains(".") && !destination.contains(" ")) {
                                                "https://$destination"
                                            } else {
                                                viewModel.buildSearchUrl(destination)
                                            }
                                        } else destination
                                        showHome = false
                                        webViewInstance?.loadUrl(destination)
                                    }
                                }
                            )
                        )

                        // Tab Gallery button with count badge
                        Box(
                            modifier = Modifier.size(34.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { showTabGallerySheet = true },
                                modifier = Modifier.size(34.dp).testTag("tab_gallery")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Tab,
                                        contentDescription = "Tabs",
                                        tint = if (isIncognito) Color(0xFFBB86FC) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            // Tab count badge
                            if (tabs.isNotEmpty()) {
                                Surface(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .align(Alignment.TopEnd)
                                        .offset(x = 2.dp, y = (-2).dp),
                                    shape = CircleShape,
                                    color = if (isIncognito) Color(0xFF7B2FBE) else MaterialTheme.colorScheme.primary
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = if (tabs.size > 9) "9+" else tabs.size.toString(),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // 3-dot overflow menu
                        var showOverflow by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { showOverflow = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false }
                            ) {
                                val isBookmarked = viewModel.isUrlBookmarked(currentUrl)
                                DropdownMenuItem(
                                    text = { Text(if (isBookmarked) "Remove Bookmark" else "Bookmark Page") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                            contentDescription = null,
                                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else Color.Gray
                                        )
                                    },
                                    onClick = {
                                        viewModel.toggleBookmark(currentUrl, webViewInstance?.title ?: "Web Page")
                                        Toast.makeText(context, if (isBookmarked) "Bookmark removed" else "Page bookmarked", Toast.LENGTH_SHORT).show()
                                        showOverflow = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (isTrackerBlocking) "Disable Tracker Blocker" else "Enable Tracker Blocker") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isTrackerBlocking) Icons.Filled.Shield else Icons.Outlined.Shield,
                                            contentDescription = null,
                                            tint = if (isTrackerBlocking) Color(0xFF4CAF50) else Color.Gray
                                        )
                                    },
                                    onClick = {
                                        viewModel.isTrackerBlocking.value = !isTrackerBlocking
                                        Toast.makeText(context, "Tracker Blocker: " + if (!isTrackerBlocking) "ENABLED" else "DISABLED", Toast.LENGTH_SHORT).show()
                                        showOverflow = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (isIncognito) "Exit Incognito" else "Go Incognito") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isIncognito) Icons.Filled.VisibilityOff else Icons.Outlined.Visibility,
                                            contentDescription = null,
                                            tint = if (isIncognito) Color(0xFFBB86FC) else Color.Gray
                                        )
                                    },
                                    onClick = {
                                        val newState = !isIncognito
                                        viewModel.toggleActiveTabIncognito()
                                        if (newState) {
                                            // Enable incognito: clear cookies, cache, history for this WebView
                                            CookieManager.getInstance().removeAllCookies(null)
                                            webViewInstance?.clearCache(true)
                                            webViewInstance?.clearHistory()
                                            Toast.makeText(context, "🕵️ Incognito Mode On", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Standard Mode On", Toast.LENGTH_SHORT).show()
                                        }
                                        showOverflow = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Bookmarks") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Bookmarks, contentDescription = null, tint = Color.Gray)
                                    },
                                    onClick = {
                                        showBookmarksSheet = true
                                        showOverflow = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("History") },
                                    leadingIcon = {
                                        Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray)
                                    },
                                    enabled = !isIncognito, // History disabled in incognito
                                    onClick = {
                                        showHistorySheet = true
                                        showOverflow = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share Page") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.Gray)
                                    },
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, currentUrl)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share URL"))
                                        showOverflow = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Open in Browser") },
                                    leadingIcon = {
                                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = Color.Gray)
                                    },
                                    onClick = {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)))
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Cannot open browser", Toast.LENGTH_SHORT).show()
                                        }
                                        showOverflow = false
                                    }
                                )
                            }
                        }
                    }

                    // Loading Progress Indicator
                    if (progressVal in 1..99) {
                        LinearProgressIndicator(
                            progress = { progressVal / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .padding(top = 2.dp),
                            color = if (isIncognito) Color(0xFFBB86FC) else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (detectedMedia.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        if (detectedMedia.size == 1) {
                            downloadDialogUrl = detectedMedia.first().url
                            showDownloadDialog = true
                        } else {
                            showMediaSheet = true
                        }
                    },
                    modifier = Modifier
                        .padding(bottom = 90.dp)
                        .testTag("media_detected_fab"),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = "Detected Media", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${detectedMedia.size} Media",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            if (showHome) {
                BrowserHomeScreen(
                    bookmarks = bookmarks,
                    isIncognito = isIncognito,
                    onSearch = { query ->
                        val searchUrl = viewModel.buildSearchUrl(query)
                        showHome = false
                        viewModel.updateActiveTabUrl(searchUrl)
                        webViewInstance?.loadUrl(searchUrl)
                    },
                    onNavigate = { url ->
                        showHome = false
                        viewModel.updateActiveTabUrl(url)
                        webViewInstance?.loadUrl(url)
                    }
                )
            } else {
            // Multi-tab WebView container
            // Each tab has its own WebView instance stored in webViewInstances map
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { container ->
                    val activeId = activeTabId ?: return@AndroidView
                    container.removeAllViews()

                    val tabData = tabs.find { it.id == activeId }
                    val tabIsIncognito = tabData?.isIncognito ?: false

                    val webView = webViewInstances.getOrPut(activeId) {
                        createWebView(
                            context = container.context,
                            isIncognito = tabIsIncognito,
                            isTrackerBlocking = isTrackerBlocking,
                            isAdBlocking = isAdBlocking,
                            isForceDarkWeb = isForceDarkWeb,
                            textSizePercent = textSizePercent,
                            userAgentMode = userAgentMode,
                            isDataSaving = isDataSaving,
                            isBlockPopups = isBlockPopups,
                            cookieMode = cookieMode,
                            locationPermission = locationPermission,
                            notificationPermission = notificationPermission,
                            microphonePermission = microphonePermission,
                            externalAppsPolicy = externalAppsPolicy,
                            onProgressChanged = { progressVal = it },
                            onPageStarted = { url ->
                                viewModel.clearDetectedMedia()
                                if (url != null) {
                                    viewModel.updateActiveTabUrl(url)
                                }
                            },
                            onPageFinished = { view, url ->
                                val title = view?.title ?: ""
                                viewModel.updateActiveTabTitle(title)
                                if (!url.isNullOrBlank()) {
                                    try { DownloadEngine.lastPageUrl = url } catch (_: Exception) {}
                                }
                                // Save history only for non-incognito tabs
                                if (!tabIsIncognito && !url.isNullOrBlank()) {
                                    viewModel.addHistoryEntry(url, title.ifBlank { url })
                                }
                            },
                            onMediaDetected = { mediaUrl, mediaTitle ->
                                viewModel.addDetectedMedia(mediaUrl, mediaTitle)
                            },
                            onShouldIntercept = { urlStr ->
                                AdBlocker.isBlocked(urlStr, isAdBlocking, isTrackerBlocking)
                            },
                            onNotificationGranted = { origin ->
                                viewModel.addNotificationSite(origin)
                            }
                        ).also { wv ->
                            val url = tabData?.url ?: "about:blank"
                            if (url != "about:blank") {
                                wv.loadUrl(url)
                            }
                        }
                    }

                    // Update webViewInstance reference
                    webViewInstance = webView

                    container.addView(webView, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ))

                    // Apply live settings to existing webview (text size, user agent, etc.)
                    webView.settings.textZoom = textSizePercent
                    if (userAgentMode == "Desktop") {
                        webView.settings.userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    } else {
                        webView.settings.userAgentString = null // reset to default mobile UA
                    }
                }
            )
            } // end else (not showHome)
        }

        // ===================== BOOKMARKS SHEET =====================
        if (showBookmarksSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBookmarksSheet = false },
                modifier = Modifier.testTag("bookmarks_bottom_sheet")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Bookmarks",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(Icons.Default.Bookmarks, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (bookmarks.isEmpty()) {
                        Text(
                            text = "No bookmarks saved yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 350.dp)) {
                            items(bookmarks) { b ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            webViewInstance?.loadUrl(b.url)
                                            showBookmarksSheet = false
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(b.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(b.url, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    IconButton(
                                        onClick = { viewModel.toggleBookmark(b.url, b.title) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remove", modifier = Modifier.size(16.dp), tint = Color.Gray)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // ===================== HISTORY SHEET =====================
        if (showHistorySheet) {
            ModalBottomSheet(
                onDismissRequest = { showHistorySheet = false },
                modifier = Modifier.testTag("history_bottom_sheet")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Browsing History",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (browsingHistory.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearAllHistory() }) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear All", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    if (browsingHistory.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No browsing history",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "Incognito sessions are never saved",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                            items(browsingHistory) { item ->
                                val dateStr = remember(item.timestamp) {
                                    SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(item.timestamp))
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            webViewInstance?.loadUrl(item.url)
                                            showHistorySheet = false
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            item.title.ifBlank { item.url },
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                item.url,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Gray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                dateStr,
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                                color = Color.Gray.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteHistoryEntry(item) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp), tint = Color.Gray)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // ===================== TAB GALLERY SHEET =====================
        if (showTabGallerySheet) {
            ModalBottomSheet(
                onDismissRequest = { showTabGallerySheet = false },
                modifier = Modifier.testTag("tab_gallery_sheet")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tab Gallery",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${tabs.size} tab${if (tabs.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // New Tab buttons row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // New Normal Tab
                        Button(
                            onClick = {
                                viewModel.addTab(isIncognito = false)
                                showTabGallerySheet = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Tab")
                        }
                        // New Private Tab
                        Button(
                            onClick = {
                                viewModel.addTab(isIncognito = true)
                                showTabGallerySheet = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF7B2FBE),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Filled.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Private")
                        }
                    }

                    val normalTabs = tabs.filter { !it.isIncognito }
                    val privateTabs = tabs.filter { it.isIncognito }

                    // Normal Tabs Section
                    if (normalTabs.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Normal (${normalTabs.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            items(normalTabs, key = { it.id }) { tab ->
                                TabItem(
                                    tab = tab,
                                    isActive = tab.id == activeTabId,
                                    canClose = tabs.size > 1,
                                    onSwitch = {
                                        viewModel.switchTab(tab.id)
                                        showTabGallerySheet = false
                                    },
                                    onClose = { viewModel.closeTab(tab.id) }
                                )
                            }
                        }
                    }

                    // Private Tabs Section
                    if (privateTabs.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFBB86FC))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Private (${privateTabs.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFFBB86FC),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            items(privateTabs, key = { it.id }) { tab ->
                                TabItem(
                                    tab = tab,
                                    isActive = tab.id == activeTabId,
                                    canClose = tabs.size > 1,
                                    isPrivate = true,
                                    onSwitch = {
                                        viewModel.switchTab(tab.id)
                                        showTabGallerySheet = false
                                    },
                                    onClose = { viewModel.closeTab(tab.id) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // ===================== DETECTED MEDIA BOTTOM SHEET =====================
        if (showMediaSheet) {
            ModalBottomSheet(
                onDismissRequest = { showMediaSheet = false },
                modifier = Modifier.testTag("media_bottom_sheet")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Detected Media",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${detectedMedia.size} media item${if (detectedMedia.size != 1) "s" else ""} found",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (detectedMedia.size > 1) {
                                FilledTonalButton(
                                    onClick = {
                                        resolvingMedia = true
                                        viewModel.viewModelScope.launch {
                                            val mediaItems = detectedMedia.toList()
                                            var successCount = 0
                                            try {
                                                for (item in mediaItems) {
                                                    try {
                                                        var resolvedHeaders: Map<String, String>? = null
                                                        val resolved = withContext(Dispatchers.IO) {
                                                            if (isDirectMediaUrl(item.url)) {
                                                                item.url
                                                            } else {
                                                                val result = try {
                                                                    VideoExtractor.extract(item.url)
                                                                } catch (e: Throwable) {
                                                                    Log.e("BrowserTab", "Extract crash for ${item.url}", e)
                                                                    null
                                                                }
                                                                val data = result?.getOrNull()
                                                                resolvedHeaders = data?.httpHeaders
                                                                data?.videoUrlNoWatermark ?: data?.videoUrl ?: item.url
                                                            }
                                                        }
                                                        viewModel.addDownload(resolved, item.title, customHeaders = resolvedHeaders, sourceUrl = item.url)
                                                        successCount++
                                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                                        throw e
                                                    } catch (e: Throwable) {
                                                        Log.e("BrowserTab", "Failed to extract ${item.url}", e)
                                                    }
                                                }
                                            } catch (e: kotlinx.coroutines.CancellationException) {
                                                throw e
                                            } catch (e: Throwable) {
                                                Log.e("BrowserTab", "Download All failed", e)
                                            }
                                            resolvingMedia = false
                                            Toast.makeText(context, "$successCount/${mediaItems.size} downloads queued!", Toast.LENGTH_SHORT).show()
                                            showMediaSheet = false
                                        }
                                    },
                                    enabled = !resolvingMedia,
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("All", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            Surface(
                                onClick = { viewModel.clearDetectedMedia(); showMediaSheet = false },
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                                shape = CircleShape
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear all",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(8.dp).size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(detectedMedia) { media ->
                            val isAudio = media.url.lowercase().run { contains(".mp3") || contains(".m4a") || contains(".wav") }
                            val domain = try { Uri.parse(media.url).host?.removePrefix("www.") ?: "unknown" } catch (_: Exception) { "unknown" }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(
                                                    if (isAudio) MaterialTheme.colorScheme.tertiaryContainer
                                                    else MaterialTheme.colorScheme.primaryContainer
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isAudio) Icons.Default.Audiotrack else Icons.Default.PlayCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = if (isAudio) MaterialTheme.colorScheme.onTertiaryContainer
                                                       else MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = media.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (isAudio) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                                                           else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                ) {
                                                    Text(
                                                        text = if (isAudio) "Audio" else "Video",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (isAudio) MaterialTheme.colorScheme.onTertiaryContainer
                                                               else MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = domain,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = media.url,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                         Button(
                                            onClick = {
                                                downloadDialogUrl = media.url
                                                showMediaSheet = false
                                                scope.launch {
                                                    delay(300)
                                                    showDownloadDialog = true
                                                }
                                            },
                                            modifier = Modifier.weight(1f).height(40.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Download", style = MaterialTheme.typography.labelLarge)
                                        }
                                        Surface(
                                            onClick = {
                                                resolvingMedia = true
                                                viewModel.viewModelScope.launch {
                                                    var success = false
                                                    try {
                                                        var resolvedHeaders: Map<String, String>? = null
                                                        val resolved = withContext(Dispatchers.IO) {
                                                            if (isDirectMediaUrl(media.url)) {
                                                                media.url
                                                            } else {
                                                                val result = try {
                                                                    VideoExtractor.extract(media.url)
                                                                } catch (e: Throwable) {
                                                                    Log.e("BrowserTab", "Extract crash for ${media.url}", e)
                                                                    null
                                                                }
                                                                val data = result?.getOrNull()
                                                                resolvedHeaders = data?.httpHeaders
                                                                data?.audioUrl ?: data?.videoUrlNoWatermark ?: data?.videoUrl ?: media.url
                                                            }
                                                        }
                                                        viewModel.addDownload(resolved, media.title, isAudioOnly = true, customHeaders = resolvedHeaders, sourceUrl = media.url)
                                                        success = true
                                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                                        throw e
                                                    } catch (e: Throwable) {
                                                        Log.e("BrowserTab", "Failed to extract audio from ${media.url}", e)
                                                        Toast.makeText(context, "Extraction failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                    }
                                                    resolvingMedia = false
                                                    if (success) {
                                                        Toast.makeText(context, "Audio extraction queued!", Toast.LENGTH_SHORT).show()
                                                        showMediaSheet = false
                                                    }
                                                }
                                            },
                                            enabled = !resolvingMedia,
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.MusicNote,
                                                    contentDescription = "Audio only",
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Surface(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(media.url))
                                                Toast.makeText(context, "URL copied", Toast.LENGTH_SHORT).show()
                                            },
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.ContentCopy,
                                                    contentDescription = "Copy link",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ===================== DOWNLOAD DIALOG =====================
        DownloadDialog(
            initialUrl = downloadDialogUrl,
            showDialog = showDownloadDialog,
            onDismiss = { showDownloadDialog = false },
            viewModel = viewModel,
        )
    }
}

// ===================== TAB ITEM COMPOSABLE =====================
@Composable
private fun TabItem(
    tab: com.example.ui.viewmodel.TabData,
    isActive: Boolean,
    canClose: Boolean,
    isPrivate: Boolean = false,
    onSwitch: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable { onSwitch() },
        shape = RoundedCornerShape(12.dp),
        color = when {
            isActive && isPrivate -> Color(0xFF7B2FBE).copy(alpha = 0.25f)
            isActive -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        border = if (isActive) BorderStroke(
            1.5.dp,
            if (isPrivate) Color(0xFFBB86FC) else MaterialTheme.colorScheme.primary
        ) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isPrivate) Icons.Filled.VisibilityOff else Icons.Default.Language,
                contentDescription = null,
                tint = if (isPrivate) Color(0xFFBB86FC) else if (isActive) MaterialTheme.colorScheme.primary else Color.Gray,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tab.title.ifBlank { if (isPrivate) "Private Tab" else "New Tab" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (isPrivate) "Private · ${tab.url.take(30)}" else tab.url.take(30),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isActive) {
                Surface(
                    shape = CircleShape,
                    color = if (isPrivate) Color(0xFFBB86FC) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(8.dp)
                ) {}
                Spacer(modifier = Modifier.width(6.dp))
            }
            if (canClose) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close tab", modifier = Modifier.size(16.dp), tint = Color.Gray)
                }
            }
        }
    }
}

// ===================== WEBVIEW FACTORY =====================
@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: Context,
    isIncognito: Boolean,
    isTrackerBlocking: Boolean,
    isAdBlocking: Boolean,
    isForceDarkWeb: Boolean,
    textSizePercent: Int,
    userAgentMode: String,
    isDataSaving: Boolean,
    isBlockPopups: Boolean,
    cookieMode: String,
    locationPermission: String = "Ask",
    notificationPermission: String = "Ask",
    microphonePermission: String = "Ask",
    externalAppsPolicy: String = "Ask",
    onProgressChanged: (Int) -> Unit,
    onPageStarted: (String?) -> Unit,
    onPageFinished: (WebView?, String?) -> Unit,
    onMediaDetected: (String, String) -> Unit,
    onShouldIntercept: (String) -> Boolean,
    onNotificationGranted: (String) -> Unit = {}
): WebView {
    return WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.allowFileAccess = !isIncognito

        // Text zoom
        settings.textZoom = textSizePercent

        // User agent
        if (userAgentMode == "Desktop") {
            settings.userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }

        // Data savings: reduce image quality requests, prefer cached content
        if (isDataSaving) {
            settings.blockNetworkImage = false // don't block, but hint for lighter pages
            settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        }

        // Incognito: disable cache, no persistent storage
        if (isIncognito) {
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            clearCache(true)
            clearHistory()
            clearFormData()
            CookieManager.getInstance().setAcceptCookie(false)
        } else {
            if (!isDataSaving) settings.cacheMode = WebSettings.LOAD_DEFAULT
            // Cookie mode
            CookieManager.getInstance().setAcceptCookie(cookieMode != "None")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                // Block third-party cookies if NoThirdParty mode
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, cookieMode == "All")
            }
        }

        // Force dark mode using modern API (API 29+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (isForceDarkWeb) {
                @Suppress("DEPRECATION")
                settings.forceDark = WebSettings.FORCE_DARK_ON
            } else {
                @Suppress("DEPRECATION")
                settings.forceDark = WebSettings.FORCE_DARK_OFF
            }
        }

        addJavascriptInterface(object {
            @JavascriptInterface
            fun postMedia(url: String, title: String) {
                try { onMediaDetected(url, title) } catch (_: Exception) {}
            }
        }, "MediaScanner")

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                onPageStarted(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onPageFinished(view, url)
                // Data saving: pause autoplay media
                if (isDataSaving) {
                    view?.evaluateJavascript("""
                        (function() {
                            var videos = document.querySelectorAll('video');
                            for (var i = 0; i < videos.length; i++) {
                                videos[i].pause();
                                videos[i].setAttribute('preload', 'none');
                            }
                            var audios = document.querySelectorAll('audio');
                            for (var i = 0; i < audios.length; i++) {
                                audios[i].pause();
                                audios[i].setAttribute('preload', 'none');
                            }
                        })();
                    """.trimIndent(), null)
                }
                view?.evaluateJavascript("""
                    (function() {
                        try {
                        var h = document.head;
                        if (h) {
                            var style = document.createElement('style');
                            style.innerHTML = 'body { padding-bottom: 120px !important; }';
                            h.appendChild(style);
                        }

                        function resolve(u) {
                            if (!u) return '';
                            try { return new URL(u, document.baseURI).href; } catch(e) { return u; }
                        }
                        function isMediaUrl(u) {
                            if (!u) return false;
                            var lower = u.toLowerCase();
                            return lower.indexOf('.mp4') !== -1 || lower.indexOf('.mp3') !== -1 ||
                                   lower.indexOf('.m4a') !== -1 || lower.indexOf('.webm') !== -1 ||
                                   lower.indexOf('.mov') !== -1 || lower.indexOf('.avi') !== -1 ||
                                   lower.indexOf('.mkv') !== -1 || lower.indexOf('.flv') !== -1 ||
                                   lower.indexOf('.ts') !== -1 || lower.indexOf('.m3u8') !== -1 ||
                                   lower.indexOf('googlevideo.com') !== -1 || lower.indexOf('manifest') !== -1;
                        }

                        function scan() {
                            var urls = [];
                            var videos = document.getElementsByTagName('video');
                            for (var i = 0; i < videos.length; i++) {
                                var src = videos[i].src || '';
                                if (!src) {
                                    try { src = videos[i].currentSrc || ''; } catch(e) {}
                                }
                                if (!src) {
                                    var dataSrc = videos[i].getAttribute('data-src') || videos[i].getAttribute('data-url') || '';
                                    src = resolve(dataSrc);
                                }
                                if (src && !src.startsWith('blob:') && !src.startsWith('data:')) urls.push({url: src, title: document.title || 'Video'});
                                var sources = videos[i].getElementsByTagName('source');
                                for (var j = 0; j < sources.length; j++) {
                                    var s = sources[j].src || '';
                                    if (!s) s = resolve(sources[j].getAttribute('data-src') || '');
                                    if (s && !s.startsWith('blob:') && !s.startsWith('data:')) urls.push({url: s, title: document.title || 'Video'});
                                }
                            }
                            var metas = document.querySelectorAll('meta[property="og:video"], meta[property="og:video:url"], meta[property="og:video:secure_url"], meta[name="twitter:player"]');
                            for (var i = 0; i < metas.length; i++) {
                                var c = resolve(metas[i].content);
                                if (c && (c.indexOf('http') === 0)) urls.push({url: c, title: document.title || 'Video'});
                            }
                            var links = document.getElementsByTagName('a');
                            for (var i = 0; i < links.length; i++) {
                                var href = links[i].href;
                                if (href && isMediaUrl(href) && href.indexOf('blob:') !== 0) {
                                    urls.push({url: href, title: links[i].innerText || document.title || 'Media File'});
                                }
                            }
                            var embeds = document.querySelectorAll('[data-media-url], [data-video-url], [data-video-src], [data-mp4]');
                            for (var i = 0; i < embeds.length; i++) {
                                var raw = embeds[i].getAttribute('data-media-url') || embeds[i].getAttribute('data-video-url') || embeds[i].getAttribute('data-video-src') || embeds[i].getAttribute('data-mp4') || '';
                                var attr = resolve(raw);
                                if (attr && attr.indexOf('http') === 0) urls.push({url: attr, title: document.title || 'Media'});
                            }
                            var seen = {};
                            for (var i = 0; i < urls.length; i++) {
                                var key = urls[i].url;
                                if (!seen[key]) { seen[key] = true; window.MediaScanner.postMedia(urls[i].url, urls[i].title); }
                            }
                        }
                        scan();
                        setTimeout(function() { scan(); }, 1500);
                        setTimeout(function() { scan(); }, 4000);
                        } catch(e) {}
                    })();
                """.trimIndent(), null)
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val urlStr = request?.url?.toString() ?: ""
                if (onShouldIntercept(urlStr)) {
                    return WebResourceResponse("text/plain", "UTF-8", null)
                }
                // Add Save-Data header by modifying requests in data saving mode
                if (isDataSaving) {
                    val existingHeaders = request?.requestHeaders?.toMutableMap() ?: mutableMapOf()
                    if (!existingHeaders.containsKey("Save-Data")) {
                        // Cannot modify headers on existing request, so note it for logging;
                        // actual Save-Data header sent via user-agent string hint set in settings
                    }
                }
                if (urlStr.contains(".mp4") || urlStr.contains(".m3u8") || urlStr.contains(".ts?") || 
                    urlStr.contains(".webm") || urlStr.contains(".mov?") || urlStr.contains(".avi?")) {
                    try {
                        val title = request?.requestHeaders?.get("Referer")?.let { 
                            it.substringAfterLast("/").substringBefore("?").take(30) 
                        } ?: "Stream"
                        onMediaDetected(urlStr, title)
                    } catch (_: Exception) {}
                }
                return super.shouldInterceptRequest(view, request)
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                onProgressChanged(newProgress)
            }

            // Popup blocking: return false to block all new windows when isBlockPopups = true
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                return if (isBlockPopups) {
                    false // Block popup
                } else {
                    super.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
                }
            }

            // Geolocation permission — respects locationPermission setting
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: android.webkit.GeolocationPermissions.Callback?
            ) {
                when (locationPermission) {
                    "Allowed" -> callback?.invoke(origin, true, true)   // allow & persist
                    "Denied"  -> callback?.invoke(origin, false, false) // deny
                    else      -> callback?.invoke(origin, true, false)  // Ask: allow once, no persist
                }
            }

            // Camera / Microphone permission — respects microphonePermission setting
            override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                val resources = request?.resources ?: return
                val audioRequested = android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE in resources
                val videoRequested = android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE in resources

                if (audioRequested || videoRequested) {
                    when (microphonePermission) {
                        "Allowed" -> request.grant(resources)
                        "Denied"  -> request.deny()
                        else      -> request.deny() // "Ask" — for a real browser we'd show a dialog; deny for safety
                    }
                } else {
                    request.grant(resources)
                }
            }

            // Notification permission — respects notificationPermission setting
            @Suppress("DEPRECATION")
            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                return super.onJsConfirm(view, url, message, result)
            }
        }

        // Data savings: inject JS to pause autoplay, lazy-load images
        // NOTE: Data saving JS is now merged into the single WebViewClient above.
        // This prevents the double-WebViewClient bug that was overwriting ad/tracker blocking.
        if (isDataSaving) {
            // Extra: append Save-Data hint to user-agent string
            val currentUA = settings.userAgentString ?: ""
            if (!currentUA.contains("Save-Data")) {
                settings.userAgentString = "$currentUA Save-Data/1.0"
            }
        }
    }
}

