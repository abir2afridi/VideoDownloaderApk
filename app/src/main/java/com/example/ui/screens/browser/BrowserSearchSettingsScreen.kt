package com.example.ui.screens.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserSearchSettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val defaultSearchEngine by viewModel.defaultSearchEngine.collectAsState()
    val isTrendingSearches by viewModel.isTrendingSearches.collectAsState()
    val isRecentSearches by viewModel.isRecentSearches.collectAsState()
    val isSuggestedSites by viewModel.isSuggestedSites.collectAsState()

    val engines = listOf(
        Triple("Google",     "https://google.com",      Color(0xFF4285F4)),
        Triple("Bing",       "https://bing.com",        Color(0xFF00897B)),
        Triple("DuckDuckGo", "https://duckduckgo.com",  Color(0xFFDE5833)),
        Triple("Yahoo",      "https://search.yahoo.com",Color(0xFF7B1FA2)),
        Triple("Brave",      "https://search.brave.com",Color(0xFFE65100)),
        Triple("Ecosia",     "https://ecosia.org",      Color(0xFF2E7D32)),
        Triple("Yandex",     "https://yandex.com",      Color(0xFFD32F2F))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Default Search Engine
            item {
                Text(
                    "DEFAULT SEARCH ENGINE",
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            engines.forEach { (name, url, color) ->
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 3.dp)
                            .clickable { viewModel.defaultSearchEngine.value = name },
                        shape = RoundedCornerShape(12.dp),
                        color = if (defaultSearchEngine == name)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = if (defaultSearchEngine == name)
                            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        else null
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    name.first().toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = color,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text(url, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            if (defaultSearchEngine == name) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            // Suggestions & Features
            item {
                Text(
                    "SUGGESTIONS & FEATURES",
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                SearchToggleRow(
                    icon = Icons.Default.TrendingUp,
                    iconColor = Color(0xFFE53935),
                    title = "Trending Searches",
                    subtitle = "Show trending topics in search bar",
                    checked = isTrendingSearches,
                    onCheckedChange = { viewModel.isTrendingSearches.value = it }
                )
            }

            item {
                SearchToggleRow(
                    icon = Icons.Default.History,
                    iconColor = Color(0xFF1E88E5),
                    title = "Recent Searches",
                    subtitle = "Show your recent searches as suggestions",
                    checked = isRecentSearches,
                    onCheckedChange = { viewModel.isRecentSearches.value = it }
                )
            }

            item {
                SearchToggleRow(
                    icon = Icons.Default.Language,
                    iconColor = Color(0xFF43A047),
                    title = "Suggested Sites",
                    subtitle = "Show website suggestions as you type",
                    checked = isSuggestedSites,
                    onCheckedChange = { viewModel.isSuggestedSites.value = it }
                )
            }
        }
    }
}

@Composable
private fun SearchToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.height(24.dp))
    }
}
