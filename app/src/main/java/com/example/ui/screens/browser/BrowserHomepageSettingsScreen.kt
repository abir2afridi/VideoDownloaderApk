package com.example.ui.screens.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
fun BrowserHomepageSettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val isSpeedDialEnabled by viewModel.isSpeedDialEnabled.collectAsState()
    val isLargeIcons by viewModel.isLargeIcons.collectAsState()
    val isSuggestedSitesHome by viewModel.isSuggestedSitesHome.collectAsState()
    val isNewsEnabled by viewModel.isNewsEnabled.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Homepage Settings", fontWeight = FontWeight.Bold) },
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
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // Preview card
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Homepage Preview",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        // Speed Dial preview
                        if (isSpeedDialEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("G", "YT", "FB", "TW").forEach { letter ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(if (isLargeIcons) 44.dp else 32.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(letter, style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                        if (isLargeIcons) {
                                            Text("Site", style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp),
                                                color = Color.Gray)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        if (isSuggestedSitesHome) {
                            Surface(shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                                Text("Suggested: YouTube • Reddit • Twitter",
                                    modifier = Modifier.padding(6.dp),
                                    style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        if (isNewsEnabled) {
                            Surface(shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                                Text("📰 News feed enabled",
                                    modifier = Modifier.padding(6.dp),
                                    style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            // ── Speed Dial ────────────────────────────────────────────────────
            item {
                Text(
                    "SPEED DIAL",
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                HomeToggleRow(
                    icon = Icons.Default.Speed,
                    iconColor = Color(0xFF1E88E5),
                    title = "Speed Dial",
                    subtitle = "Show frequently visited sites on homepage",
                    checked = isSpeedDialEnabled,
                    onCheckedChange = { viewModel.isSpeedDialEnabled.value = it }
                )
            }

            item {
                HomeToggleRow(
                    icon = Icons.Default.GridView,
                    iconColor = Color(0xFF8E24AA),
                    title = "Large Icons",
                    subtitle = "Use larger tiles with site names",
                    checked = isLargeIcons,
                    enabled = isSpeedDialEnabled,
                    onCheckedChange = { viewModel.isLargeIcons.value = it }
                )
            }

            // ── Content ───────────────────────────────────────────────────────
            item {
                Text(
                    "CONTENT",
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                HomeToggleRow(
                    icon = Icons.Default.AutoAwesome,
                    iconColor = Color(0xFF00897B),
                    title = "Suggested Sites",
                    subtitle = "Show site suggestions based on browsing",
                    checked = isSuggestedSitesHome,
                    onCheckedChange = { viewModel.isSuggestedSitesHome.value = it }
                )
            }

            item {
                HomeToggleRow(
                    icon = Icons.Default.Article,
                    iconColor = Color(0xFFE53935),
                    title = "News Feed",
                    subtitle = "Show top news articles on homepage",
                    checked = isNewsEnabled,
                    onCheckedChange = { viewModel.isNewsEnabled.value = it }
                )
            }
        }
    }
}

@Composable
private fun HomeToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape)
                .background(iconColor.copy(alpha = 0.12f * alpha)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor.copy(alpha = alpha),
                modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled,
            modifier = Modifier.height(24.dp))
    }
}
