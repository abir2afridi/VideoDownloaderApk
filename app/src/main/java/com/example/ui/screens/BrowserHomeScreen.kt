package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.BookmarkEntity
import com.example.data.database.HistoryEntity
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

// ─── Speed Dial model ────────────────────────────────────────────────────────

data class SpeedDialSite(
    val title: String,
    val url: String,
    val icon: ImageVector,
    val color: Color
)

private val DEFAULT_SPEED_DIAL = listOf(
    SpeedDialSite("Google",    "https://google.com",    Icons.Default.Search,       Color(0xFF4285F4)),
    SpeedDialSite("YouTube",   "https://youtube.com",   Icons.Default.PlayArrow,    Color(0xFFFF0000)),
    SpeedDialSite("Facebook",  "https://facebook.com",  Icons.Default.People,       Color(0xFF1877F2)),
    SpeedDialSite("Instagram", "https://instagram.com", Icons.Default.CameraAlt,    Color(0xFFE4405F)),
    SpeedDialSite("X",         "https://x.com",         Icons.Default.Tag,          Color(0xFF000000)),
    SpeedDialSite("Wikipedia", "https://wikipedia.org", Icons.AutoMirrored.Filled.MenuBook, Color(0xFF636466)),
    SpeedDialSite("Reddit",    "https://reddit.com",    Icons.Default.Forum,        Color(0xFFFF4500)),
    SpeedDialSite("Amazon",    "https://amazon.com",    Icons.Default.ShoppingCart, Color(0xFFFF9900)),
    SpeedDialSite("GitHub",    "https://github.com",    Icons.Default.Code,         Color(0xFF24292E)),
    SpeedDialSite("Netflix",   "https://netflix.com",   Icons.Default.LiveTv,       Color(0xFFE50914)),
    SpeedDialSite("Spotify",   "https://spotify.com",   Icons.Default.MusicNote,    Color(0xFF1DB954)),
    SpeedDialSite("Pinterest", "https://pinterest.com", Icons.Default.PushPin,      Color(0xFFBD081C)),
)

// ─── Search engine branding ───────────────────────────────────────────────────

private fun engineLabel(engine: String) = when (engine) {
    "Bing"       -> "Search with Bing"
    "DuckDuckGo" -> "Search DuckDuckGo"
    "Yahoo"      -> "Search Yahoo"
    "Brave"      -> "Search with Brave"
    "Ecosia"     -> "Search with Ecosia"
    "Yandex"     -> "Search Yandex"
    else         -> "Search Google"
}

private fun engineColor(engine: String) = when (engine) {
    "Bing"       -> Color(0xFF00897B)
    "DuckDuckGo" -> Color(0xFFDE5833)
    "Yahoo"      -> Color(0xFF7B1FA2)
    "Brave"      -> Color(0xFFE65100)
    "Ecosia"     -> Color(0xFF2E7D32)
    "Yandex"     -> Color(0xFFD32F2F)
    else         -> Color(0xFF4285F4)
}

// ─── News feed item model ─────────────────────────────────────────────────────

private data class NewsItem(
    val title: String,
    val source: String,
    val category: String,
    val icon: ImageVector,
    val color: Color,
    val url: String
)

private val SAMPLE_NEWS = listOf(
    NewsItem("Technology: AI reshapes how browsers work in 2025", "TechCrunch", "Tech", Icons.Default.Memory, Color(0xFF1E88E5), "https://techcrunch.com"),
    NewsItem("Privacy: New standards proposed for web tracking limits", "The Verge", "Privacy", Icons.Default.Shield, Color(0xFF43A047), "https://theverge.com"),
    NewsItem("Science: Breakthrough in quantum computing announced", "Wired", "Science", Icons.Default.Science, Color(0xFF8E24AA), "https://wired.com"),
    NewsItem("Business: Global tech stocks reach all-time high", "Reuters", "Business", Icons.AutoMirrored.Filled.TrendingUp, Color(0xFFE53935), "https://reuters.com"),
    NewsItem("Culture: Streaming wars intensify with new entrants", "BBC", "Culture", Icons.Default.LiveTv, Color(0xFFFF6F00), "https://bbc.com"),
)

// ─── Greeting ─────────────────────────────────────────────────────────────────

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 5  -> "Good Night"
        hour < 12 -> "Good Morning"
        hour < 17 -> "Good Afternoon"
        hour < 21 -> "Good Evening"
        else      -> "Good Night"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  BrowserHomeScreen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BrowserHomeScreen(
    bookmarks: List<BookmarkEntity>,
    isIncognito: Boolean,
    onSearch: (String) -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: MainViewModel? = null,
    browsingHistory: List<HistoryEntity> = emptyList()
) {
    // ── Collect settings from ViewModel ──────────────────────────────────────
    val isSpeedDialEnabled  = viewModel?.isSpeedDialEnabled?.collectAsState()?.value  ?: true
    val isLargeIcons        = viewModel?.isLargeIcons?.collectAsState()?.value        ?: false
    val isSuggestedSites    = viewModel?.isSuggestedSitesHome?.collectAsState()?.value ?: true
    val isNewsEnabled       = viewModel?.isNewsEnabled?.collectAsState()?.value       ?: true
    val defaultEngine       = viewModel?.defaultSearchEngine?.collectAsState()?.value ?: "Google"

    // ── State ─────────────────────────────────────────────────────────────────
    var searchQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    // Live clock
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val now = Calendar.getInstance()
            currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time)
            currentDate = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now.time)
            delay(10_000L)
        }
    }

    // ── Colors ────────────────────────────────────────────────────────────────
    val bgColor      = if (isIncognito) Color(0xFF131316) else MaterialTheme.colorScheme.background
    val cardColor    = if (isIncognito) Color(0xFF1E1E24) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val textColor    = if (isIncognito) Color(0xFFEEEEEE) else MaterialTheme.colorScheme.onSurface
    val mutedColor   = if (isIncognito) Color(0xFF888888) else MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor  = if (isIncognito) Color(0xFFBB86FC) else engineColor(defaultEngine)

    // Recent history — top 8 unique domains, exclude blank/search results
    val recentSites = remember(browsingHistory) {
        browsingHistory
            .filter { it.url.startsWith("http") && !it.url.contains("/search?") }
            .distinctBy {
                try { android.net.Uri.parse(it.url).host ?: it.url } catch (_: Exception) { it.url }
            }
            .take(8)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(28.dp))

        // ── Greeting + Clock ──────────────────────────────────────────────────
        if (!isIncognito) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (currentTime.isNotEmpty()) {
                    Text(
                        text = currentTime,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Light,
                            fontSize = 56.sp,
                            letterSpacing = (-1).sp
                        ),
                        color = textColor
                    )
                    Text(
                        text = currentDate,
                        style = MaterialTheme.typography.bodyMedium,
                        color = mutedColor,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = greeting(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor
                    )
                }
            }
        } else {
            // Incognito header
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2D1B69).copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Incognito Mode",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Text(
                    text = "Your activity won't be saved",
                    style = MaterialTheme.typography.bodySmall,
                    color = mutedColor,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Search Bar ────────────────────────────────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(32.dp),
                    ambientColor = accentColor.copy(alpha = 0.15f),
                    spotColor = accentColor.copy(alpha = 0.15f)
                ),
            shape = RoundedCornerShape(32.dp),
            color = cardColor,
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.20f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            engineLabel(defaultEngine),
                            style = MaterialTheme.typography.bodyMedium,
                            color = mutedColor
                        )
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchQuery.isNotBlank()) {
                                onSearch(searchQuery.trim())
                                searchQuery = ""
                                focusManager.clearFocus()
                            }
                        }
                    )
                )
                AnimatedVisibility(visible = searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = mutedColor, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Mic shortcut row ──────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "or",
                style = MaterialTheme.typography.bodySmall,
                color = mutedColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = accentColor.copy(alpha = 0.10f),
                modifier = Modifier.clickable { /* voice search placeholder */ }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Search by voice", style = MaterialTheme.typography.labelSmall, color = accentColor)
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── Bookmarks row ─────────────────────────────────────────────────────
        if (bookmarks.isNotEmpty()) {
            SectionHeader("Bookmarks", Icons.Default.Bookmark, accentColor, textColor)
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(end = 4.dp)
            ) {
                items(bookmarks.take(6)) { bookmark ->
                    BookmarkPill(
                        bookmark = bookmark,
                        cardColor = cardColor,
                        textColor = textColor,
                        accentColor = accentColor,
                        onClick = { onNavigate(bookmark.url) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ── Recent sites (from history) ───────────────────────────────────────
        if (isSuggestedSites && recentSites.isNotEmpty()) {
            SectionHeader("Recent Sites", Icons.Default.History, accentColor, textColor)
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(end = 4.dp)
            ) {
                items(recentSites) { historyEntry ->
                    RecentSiteChip(
                        title = historyEntry.title.ifBlank { historyEntry.url },
                        url = historyEntry.url,
                        cardColor = cardColor,
                        textColor = textColor,
                        accentColor = accentColor,
                        onClick = { onNavigate(historyEntry.url) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ── Speed Dial ────────────────────────────────────────────────────────
        if (isSpeedDialEnabled) {
            SectionHeader("Quick Access", Icons.Default.Speed, accentColor, textColor)
            Spacer(modifier = Modifier.height(10.dp))
            val columns = if (isLargeIcons) 3 else 4
            val iconSize = if (isLargeIcons) 58.dp else 48.dp
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                verticalArrangement = Arrangement.spacedBy(if (isLargeIcons) 20.dp else 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp),
                contentPadding = PaddingValues(bottom = 4.dp),
                userScrollEnabled = false
            ) {
                items(DEFAULT_SPEED_DIAL) { site ->
                    SpeedDialItem(
                        site = site,
                        iconSize = iconSize,
                        isLargeIcons = isLargeIcons,
                        cardColor = cardColor,
                        textColor = textColor,
                        onClick = { onNavigate(site.url) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ── News Feed ─────────────────────────────────────────────────────────
        if (isNewsEnabled) {
            SectionHeader("Top Stories", Icons.AutoMirrored.Filled.Article, accentColor, textColor)
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SAMPLE_NEWS.forEach { news ->
                    NewsFeedCard(
                        news = news,
                        cardColor = cardColor,
                        textColor = textColor,
                        mutedColor = mutedColor,
                        onClick = { onNavigate(news.url) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Bottom padding for nav bar
        Spacer(modifier = Modifier.height(90.dp))
    }
}

// ─── Section Header ───────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    textColor: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

// ─── Bookmark Pill ────────────────────────────────────────────────────────────

@Composable
private fun BookmarkPill(
    bookmark: BookmarkEntity,
    cardColor: Color,
    textColor: Color,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .widthIn(max = 120.dp),
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Bookmark,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = bookmark.title.ifBlank { bookmark.url },
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─── Recent Site Chip ─────────────────────────────────────────────────────────

@Composable
private fun RecentSiteChip(
    title: String,
    url: String,
    cardColor: Color,
    textColor: Color,
    accentColor: Color,
    onClick: () -> Unit
) {
    val domain = remember(url) {
        try { android.net.Uri.parse(url).host?.removePrefix("www.") ?: url } catch (_: Exception) { url }
    }
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = cardColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).width(80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Favicon placeholder with first letter
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(accentColor.copy(alpha = 0.3f), accentColor.copy(alpha = 0.10f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = domain.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = domain,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── Speed Dial Item ──────────────────────────────────────────────────────────

@Composable
private fun SpeedDialItem(
    site: SpeedDialSite,
    iconSize: androidx.compose.ui.unit.Dp,
    isLargeIcons: Boolean,
    cardColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(iconSize),
            shape = if (isLargeIcons) RoundedCornerShape(14.dp) else CircleShape,
            color = cardColor,
            border = BorderStroke(1.dp, site.color.copy(alpha = 0.25f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(iconSize)
                        .background(
                            Brush.radialGradient(
                                listOf(site.color.copy(alpha = 0.20f), site.color.copy(alpha = 0.05f))
                            )
                        )
                )
                Icon(
                    imageVector = site.icon,
                    contentDescription = site.title,
                    tint = site.color,
                    modifier = Modifier.size(if (isLargeIcons) 28.dp else 22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = site.title,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (isLargeIcons) 12.sp else 10.sp),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// ─── News Feed Card ───────────────────────────────────────────────────────────

@Composable
private fun NewsFeedCard(
    news: NewsItem,
    cardColor: Color,
    textColor: Color,
    mutedColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = cardColor,
        border = BorderStroke(1.dp, news.color.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(news.color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(news.icon, contentDescription = null, tint = news.color, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = news.color.copy(alpha = 0.12f)
                ) {
                    Text(
                        news.category,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = news.color,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = news.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = news.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedColor
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = mutedColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
