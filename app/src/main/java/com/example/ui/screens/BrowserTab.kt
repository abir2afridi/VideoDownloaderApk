package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.*
import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.viewmodel.MainViewModel

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserTab(viewModel: MainViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    val currentUrl by viewModel.currentWebUrl.collectAsState()
    val isIncognito by viewModel.isIncognito.collectAsState()
    val isTrackerBlocking by viewModel.isTrackerBlocking.collectAsState()
    val isHttpsOnly by viewModel.isHttpsOnly.collectAsState()
    val detectedMedia by viewModel.detectedMediaList.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()

    var urlInput by remember { mutableStateOf(currentUrl) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var progressVal by remember { mutableStateOf(0) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showMediaSheet by remember { mutableStateOf(false) }

    // Sync input bar when url state changes
    LaunchedEffect(currentUrl) {
        urlInput = currentUrl
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isIncognito) Color(0xFF1C1B1F) else MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                // Address Bar row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bookmark toggle
                    val isBookmarked = viewModel.isUrlBookmarked(currentUrl)
                    IconButton(
                        onClick = {
                            viewModel.toggleBookmark(currentUrl, webViewInstance?.title ?: "Web Page")
                            Toast.makeText(context, if (isBookmarked) "Bookmark removed" else "Page bookmarked", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("bookmark_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // URL TextField
                    TextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("browser_address_bar")
                            .height(52.dp),
                        placeholder = { Text("Search or enter website...") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(24.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = if (isHttpsOnly) Icons.Default.Lock else Icons.Default.Language,
                                contentDescription = "Security Status",
                                tint = if (isHttpsOnly) MaterialTheme.colorScheme.primary else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (isIncognito) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .background(Color.DarkGray, shape = CircleShape)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Private", color = Color.White, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                var destination = urlInput.trim()
                                if (destination.isNotBlank()) {
                                    if (!destination.startsWith("http://") && !destination.startsWith("https://")) {
                                        if (destination.contains(".") && !destination.contains(" ")) {
                                            destination = "https://$destination"
                                        } else {
                                            destination = "https://google.com/search?q=$destination"
                                        }
                                    }
                                    webViewInstance?.loadUrl(destination)
                                }
                            }
                        )
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Bookmark List Button
                    IconButton(
                        onClick = { showBookmarksSheet = true },
                        modifier = Modifier.testTag("bookmarks_list_btn")
                    ) {
                        Icon(Icons.Default.Bookmarks, contentDescription = "Bookmarks")
                    }
                }

                // Toolbar Controls Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        IconButton(
                            onClick = { webViewInstance?.goBack() },
                            enabled = webViewInstance?.canGoBack() == true,
                            modifier = Modifier.testTag("browser_back")
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                        IconButton(
                            onClick = { webViewInstance?.goForward() },
                            enabled = webViewInstance?.canGoForward() == true,
                            modifier = Modifier.testTag("browser_forward")
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Forward")
                        }
                        IconButton(
                            onClick = { webViewInstance?.reload() },
                            modifier = Modifier.testTag("browser_reload")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }

                    // Incognito & Tracker blocking toggles
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Incognito switch
                        IconButton(
                            onClick = {
                                val newState = !isIncognito
                                viewModel.isIncognito.value = newState
                                if (newState) {
                                    CookieManager.getInstance().removeAllCookies(null)
                                    webViewInstance?.clearCache(true)
                                    webViewInstance?.clearHistory()
                                    Toast.makeText(context, "Incognito Mode Activated", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Standard Mode Activated", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("incognito_toggle")
                        ) {
                            Icon(
                                imageVector = if (isIncognito) Icons.Filled.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = "Incognito",
                                tint = if (isIncognito) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }

                        // Tracker blocker status
                        IconButton(
                            onClick = {
                                viewModel.isTrackerBlocking.value = !isTrackerBlocking
                                Toast.makeText(context, "Tracker Blocker: " + if (!isTrackerBlocking) "ENABLED" else "DISABLED", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = if (isTrackerBlocking) Icons.Filled.Shield else Icons.Outlined.Shield,
                                contentDescription = "Tracker blocker",
                                tint = if (isTrackerBlocking) Color(0xFF4CAF50) else Color.Gray
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
                            .height(3.dp)
                            .padding(top = 2.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        floatingActionButton = {
            if (detectedMedia.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showMediaSheet = true },
                    modifier = Modifier.testTag("media_detected_fab"),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayCircleFilled, contentDescription = "Detected Media", tint = Color.Red)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${detectedMedia.size} Media Found",
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
                .padding(innerPadding)
        ) {
            // Android WebView
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun postMedia(url: String, title: String) {
                                viewModel.addDetectedMedia(url, title)
                            }
                        }, "MediaScanner")

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                viewModel.clearDetectedMedia()
                                if (url != null) {
                                    viewModel.currentWebUrl.value = url
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                view?.evaluateJavascript("""
                                    (function() {
                                        function scan() {
                                            var videos = document.getElementsByTagName('video');
                                            for (var i = 0; i < videos.length; i++) {
                                                var src = videos[i].src || '';
                                                if (src && !src.startsWith('blob:') && !src.startsWith('data:')) {
                                                    window.MediaScanner.postMedia(src, document.title || 'Video');
                                                }
                                                var sources = videos[i].getElementsByTagName('source');
                                                for (var j = 0; j < sources.length; j++) {
                                                    var sSrc = sources[j].src;
                                                    if (sSrc && !sSrc.startsWith('blob:') && !sSrc.startsWith('data:')) {
                                                        window.MediaScanner.postMedia(sSrc, document.title || 'Video');
                                                    }
                                                }
                                            }
                                            var links = document.getElementsByTagName('a');
                                            for (var i = 0; i < links.length; i++) {
                                                var href = links[i].href;
                                                if (href && (href.indexOf('.mp4') !== -1 || href.indexOf('.mp3') !== -1 || href.indexOf('.m4a') !== -1)) {
                                                    window.MediaScanner.postMedia(href, links[i].innerText || document.title || 'Media File');
                                                }
                                            }
                                        }
                                        scan();
                                        setInterval(scan, 2000);
                                    })();
                                """.trimIndent(), null)
                            }

                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                val urlStr = request?.url?.toString() ?: ""
                                if (isTrackerBlocking) {
                                    val blockedKeywords = listOf(
                                        "doubleclick.net", "ads.", "analytics", "telemetry",
                                        "google-analytics", "facebook.com/tr", "adnxs.com", "taboola"
                                    )
                                    if (blockedKeywords.any { urlStr.contains(it, ignoreCase = true) }) {
                                        return WebResourceResponse("text/plain", "UTF-8", null)
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                super.onProgressChanged(view, newProgress)
                                progressVal = newProgress
                            }
                        }

                        loadUrl(currentUrl)
                        webViewInstance = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // BOOKMARKS SHEET
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
                    Text(
                        text = "Your Bookmarks",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    if (bookmarks.isEmpty()) {
                        Text(
                            text = "No bookmarks saved yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
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
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // DETECTED MEDIA BOTTOM SHEET
        if (showMediaSheet) {
            ModalBottomSheet(
                onDismissRequest = { showMediaSheet = false },
                modifier = Modifier.testTag("media_bottom_sheet")
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
                            text = "⚡ Detected Downloadable Media",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { viewModel.clearDetectedMedia(); showMediaSheet = false }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear all detected", tint = Color.Red)
                        }
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false).heightIn(max = 400.dp)
                    ) {
                        items(detectedMedia) { media ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = media.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    
                                    Text(
                                        text = media.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                viewModel.addDownload(media.url, media.title)
                                                Toast.makeText(context, "Download Queued!", Toast.LENGTH_SHORT).show()
                                                showMediaSheet = false
                                            },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Download", style = MaterialTheme.typography.labelSmall)
                                        }

                                        FilledTonalButton(
                                            onClick = {
                                                viewModel.addDownload(media.url, media.title)
                                                Toast.makeText(context, "Download started!", Toast.LENGTH_SHORT).show()
                                                showMediaSheet = false
                                            },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Fast Download", style = MaterialTheme.typography.labelSmall)
                                        }

                                        IconButton(
                                            onClick = {
                                                viewModel.addDownload(media.url, media.title, isAudioOnly = true)
                                                Toast.makeText(context, "Audio Download started!", Toast.LENGTH_SHORT).show()
                                                showMediaSheet = false
                                            }
                                        ) {
                                            Icon(Icons.Default.Audiotrack, contentDescription = "Audio only", tint = MaterialTheme.colorScheme.primary)
                                        }

                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(media.url))
                                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy link")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
