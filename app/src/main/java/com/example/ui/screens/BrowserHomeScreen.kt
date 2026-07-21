package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

data class SpeedDialSite(
    val title: String,
    val url: String,
    val icon: ImageVector,
    val color: Color
)

private val DEFAULT_SPEED_DIAL = listOf(
    SpeedDialSite("Google", "https://google.com", Icons.Default.Search, Color(0xFF4285F4)),
    SpeedDialSite("YouTube", "https://youtube.com", Icons.Default.PlayArrow, Color(0xFFFF0000)),
    SpeedDialSite("Facebook", "https://facebook.com", Icons.Default.People, Color(0xFF1877F2)),
    SpeedDialSite("Instagram", "https://instagram.com", Icons.Default.CameraAlt, Color(0xFFE4405F)),
    SpeedDialSite("Twitter", "https://x.com", Icons.Default.Close, Color(0xFF1DA1F2)),
    SpeedDialSite("Wikipedia", "https://wikipedia.org", Icons.Default.MenuBook, Color(0xFF636466)),
    SpeedDialSite("Reddit", "https://reddit.com", Icons.Default.Forum, Color(0xFFFF4500)),
    SpeedDialSite("Amazon", "https://amazon.com", Icons.Default.ShoppingCart, Color(0xFFFF9900)),
    SpeedDialSite("GitHub", "https://github.com", Icons.Default.Code, Color(0xFF333333)),
    SpeedDialSite("Netflix", "https://netflix.com", Icons.Default.LiveTv, Color(0xFFE50914)),
    SpeedDialSite("Spotify", "https://spotify.com", Icons.Default.MusicNote, Color(0xFF1DB954)),
    SpeedDialSite("Pinterest", "https://pinterest.com", Icons.Default.PushPin, Color(0xFFBD081C)),
)

@Composable
fun BrowserHomeScreen(
    bookmarks: List<BookmarkEntity>,
    isIncognito: Boolean,
    onSearch: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val bgColor = if (isIncognito) Color(0xFF1C1B1F) else MaterialTheme.colorScheme.background
    val surfaceColor = if (isIncognito) Color(0xFF2D2D3A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val textColor = if (isIncognito) Color.White else MaterialTheme.colorScheme.onSurface
    val mutedColor = if (isIncognito) Color(0xFFAAAAAA) else Color.Gray

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // App branding
        Text(
            text = "NexLoad",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Text(
            text = "Fast, Secure, Private",
            style = MaterialTheme.typography.bodySmall,
            color = mutedColor,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Search bar
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp)),
            placeholder = {
                Text("Search or enter URL...", style = MaterialTheme.typography.bodyMedium)
            },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = surfaceColor,
                unfocusedContainerColor = surfaceColor,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
            ),
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = mutedColor, modifier = Modifier.size(18.dp))
                    }
                }
            },
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

        Spacer(modifier = Modifier.height(24.dp))

        // Section: Bookmarks (if any)
        if (bookmarks.isNotEmpty()) {
            Text(
                "Bookmarks",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = textColor,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                bookmarks.take(4).forEach { bookmark ->
                    BookmarkChip(
                        bookmark = bookmark,
                        surfaceColor = surfaceColor,
                        textColor = textColor,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(bookmark.url) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Section: Speed Dial
        Text(
            "Quick Access",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(DEFAULT_SPEED_DIAL) { site ->
                SpeedDialItem(
                    site = site,
                    surfaceColor = surfaceColor,
                    textColor = textColor,
                    onClick = { onNavigate(site.url) }
                )
            }
        }
    }
}

@Composable
private fun BookmarkChip(
    bookmark: BookmarkEntity,
    surfaceColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = surfaceColor
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Bookmark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                bookmark.title.take(8),
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SpeedDialItem(
    site: SpeedDialSite,
    surfaceColor: Color,
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
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            color = site.color.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = site.icon,
                    contentDescription = site.title,
                    tint = site.color,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            site.title,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
