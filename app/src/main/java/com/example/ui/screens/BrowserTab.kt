package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.*
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
    val isForceDarkWeb by viewModel.isForceDarkWeb.collectAsState()
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
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { webViewInstance?.goForward() },
                            enabled = webViewInstance?.canGoForward() == true,
                            modifier = Modifier.size(34.dp).testTag("browser_forward")
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Forward", modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { webViewInstance?.reload() },
                            modifier = Modifier.size(34.dp).testTag("browser_reload")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(20.dp))
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
                            placeholder = { Text("Search...", style = MaterialTheme.typography.bodyMedium) },
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
                                    contentDescription = null,
                                    tint = if (isHttpsOnly) MaterialTheme.colorScheme.primary else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                val isBookmarked = viewModel.isUrlBookmarked(currentUrl)
                                IconButton(
                                    onClick = {
                                        viewModel.toggleBookmark(currentUrl, webViewInstance?.title ?: "Web Page")
                                        Toast.makeText(context, if (isBookmarked) "Bookmark removed" else "Page bookmarked", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp).testTag("bookmark_toggle_btn")
                                ) {
                                    Icon(
                                        imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                        contentDescription = "Bookmark",
                                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary else Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            textStyle = MaterialTheme.typography.bodyMedium,
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

                        // Privacy toggles
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
                            modifier = Modifier.size(34.dp).testTag("incognito_toggle")
                        ) {
                            Icon(
                                imageVector = if (isIncognito) Icons.Filled.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = "Incognito",
                                tint = if (isIncognito) MaterialTheme.colorScheme.primary else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                viewModel.isTrackerBlocking.value = !isTrackerBlocking
                                Toast.makeText(context, "Tracker Blocker: " + if (!isTrackerBlocking) "ENABLED" else "DISABLED", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isTrackerBlocking) Icons.Filled.Shield else Icons.Outlined.Shield,
                                contentDescription = "Tracker blocker",
                                tint = if (isTrackerBlocking) Color(0xFF4CAF50) else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { showBookmarksSheet = true },
                            modifier = Modifier.size(34.dp).testTag("bookmarks_list_btn")
                        ) {
                            Icon(Icons.Default.Bookmarks, contentDescription = "Bookmarks", modifier = Modifier.size(20.dp))
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
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (detectedMedia.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showMediaSheet = true },
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
                .padding(top = innerPadding.calculateTopPadding())
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

                        // Apply Force Dark Mode if enabled
                        if (isForceDarkWeb && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            settings.forceDark = WebSettings.FORCE_DARK_ON
                        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            settings.forceDark = WebSettings.FORCE_DARK_OFF
                        }
                        
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
                                // Inject padding at the bottom to ensure content isn't fully hidden by the floating nav bar
                                view?.evaluateJavascript("""
                                    (function() {
                                        var style = document.createElement('style');
                                        style.innerHTML = 'body { padding-bottom: 120px !important; }';
                                        document.head.appendChild(style);
                                        
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
                modifier = Modifier.testTag("media_bottom_sheet"),
                dragHandle = { BottomSheetDefaults.DragHandle() },
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = Color(0xFFFFD600),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Detected Media",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
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
                    
                    Text(
                        text = "${detectedMedia.size} items ready for high-speed download",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 36.dp, top = 2.dp, bottom = 16.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false).heightIn(max = 450.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(detectedMedia) { media ->
                            val isAudio = media.url.lowercase().run { contains(".mp3") || contains(".m4a") || contains(".wav") }
                            
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (isAudio) MaterialTheme.colorScheme.tertiaryContainer 
                                                    else MaterialTheme.colorScheme.primaryContainer
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isAudio) Icons.Default.Audiotrack else Icons.Default.PlayCircle,
                                                contentDescription = null,
                                                tint = if (isAudio) MaterialTheme.colorScheme.onTertiaryContainer 
                                                       else MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.width(12.dp))
                                        
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = media.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = media.url,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Primary Action: Download
                                        Button(
                                            onClick = {
                                                viewModel.addDownload(media.url, media.title)
                                                Toast.makeText(context, "Download Queued!", Toast.LENGTH_SHORT).show()
                                                showMediaSheet = false
                                            },
                                            modifier = Modifier.weight(1.2f).height(42.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Download", style = MaterialTheme.typography.labelLarge)
                                        }

                                        // Secondary Action: Fast Download
                                        FilledTonalButton(
                                            onClick = {
                                                viewModel.addDownload(media.url, media.title)
                                                Toast.makeText(context, "Fast Download started!", Toast.LENGTH_SHORT).show()
                                                showMediaSheet = false
                                            },
                                            modifier = Modifier.weight(1.2f).height(42.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Fast", style = MaterialTheme.typography.labelLarge)
                                        }

                                        // Utility Actions
                                        Row(
                                            modifier = Modifier.weight(0.8f),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Surface(
                                                onClick = {
                                                    viewModel.addDownload(media.url, media.title, isAudioOnly = true)
                                                    Toast.makeText(context, "Extracting Audio...", Toast.LENGTH_SHORT).show()
                                                    showMediaSheet = false
                                                },
                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.size(42.dp)
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
                                            
                                            Spacer(modifier = Modifier.width(8.dp))
                                            
                                            Surface(
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString(media.url))
                                                    Toast.makeText(context, "URL Copied", Toast.LENGTH_SHORT).show()
                                                },
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.size(42.dp)
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
        }
    }
}
