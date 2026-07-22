package com.example.ui.screens.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.WrapText
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
fun BrowserContentSettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val textSizePercent by viewModel.textSizePercent.collectAsState()
    val isTextWrap by viewModel.isTextWrap.collectAsState()
    val isBlockPopups by viewModel.isBlockPopups.collectAsState()
    val userAgentMode by viewModel.userAgentMode.collectAsState()
    val isDataSaving by viewModel.isDataSaving.collectAsState()
    val isForceDarkWeb by viewModel.isForceDarkWeb.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Content Settings", fontWeight = FontWeight.Bold) },
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
            // ── Text ──────────────────────────────────────────────────────────
            item {
                ContentSectionHeader("Text")
            }

            item {
                // Text Size Slider
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape)
                                .background(Color(0xFF5C6BC0).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.TextFields, contentDescription = null,
                                tint = Color(0xFF5C6BC0), modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Text Size", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Current: ${textSizePercent}%", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Preview
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ) {
                        Text(
                            text = "The quick brown fox jumps over the lazy dog.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = (14 * textSizePercent / 100f).sp
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (textSizePercent > 50) viewModel.textSizePercent.value = textSizePercent - 10 },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(18.dp))
                        }
                        Slider(
                            value = textSizePercent.toFloat(),
                            onValueChange = { viewModel.textSizePercent.value = it.toInt() },
                            valueRange = 50f..200f,
                            steps = 14,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { if (textSizePercent < 200) viewModel.textSizePercent.value = textSizePercent + 10 },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(18.dp))
                        }
                    }

                    // Quick size buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(75, 100, 125, 150).forEach { pct ->
                            FilterChip(
                                selected = textSizePercent == pct,
                                onClick = { viewModel.textSizePercent.value = pct },
                                label = { Text("${pct}%", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item {
                ContentToggleRow(
                    icon = Icons.AutoMirrored.Filled.WrapText,
                    iconColor = Color(0xFF00ACC1),
                    title = "Text Wrap",
                    subtitle = if (isTextWrap) "Text wraps to screen width" else "Text may overflow horizontally",
                    checked = isTextWrap,
                    onCheckedChange = { viewModel.isTextWrap.value = it }
                )
            }

            // ── Pages ─────────────────────────────────────────────────────────
            item { ContentSectionHeader("Pages") }

            item {
                ContentToggleRow(
                    icon = Icons.Default.Block,
                    iconColor = Color(0xFFE53935),
                    title = "Block Pop-ups",
                    subtitle = if (isBlockPopups) "Pop-up windows are blocked" else "Pop-ups allowed",
                    checked = isBlockPopups,
                    onCheckedChange = { viewModel.isBlockPopups.value = it }
                )
            }

            item {
                ContentToggleRow(
                    icon = Icons.Default.DarkMode,
                    iconColor = Color(0xFF3949AB),
                    title = "Force Dark Mode",
                    subtitle = if (isForceDarkWeb) "All websites rendered dark" else "Use site's own theme",
                    checked = isForceDarkWeb,
                    onCheckedChange = { viewModel.isForceDarkWeb.value = it }
                )
            }

            // ── User Agent ────────────────────────────────────────────────────
            item { ContentSectionHeader("User Agent") }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf("Mobile" to Icons.Default.PhoneAndroid, "Desktop" to Icons.Default.Computer).forEach { (mode, icon) ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.userAgentMode.value = mode },
                            shape = RoundedCornerShape(12.dp),
                            color = if (userAgentMode == mode) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = if (userAgentMode == mode)
                                androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            else null
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = if (userAgentMode == mode) MaterialTheme.colorScheme.primary else Color.Gray,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    mode,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (userAgentMode == mode) FontWeight.Bold else FontWeight.Normal,
                                    color = if (userAgentMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    if (mode == "Mobile") "Optimized for touch" else "Full desktop site",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }

            // ── Data Savings ──────────────────────────────────────────────────
            item { ContentSectionHeader("Performance") }

            item {
                ContentToggleRow(
                    icon = Icons.Default.DataSaverOn,
                    iconColor = Color(0xFF43A047),
                    title = "Data Savings Mode",
                    subtitle = if (isDataSaving)
                        "Images compressed, scripts deferred, background media blocked"
                    else
                        "Full quality content loaded",
                    checked = isDataSaving,
                    onCheckedChange = { viewModel.isDataSaving.value = it }
                )
            }

            if (isDataSaving) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF43A047).copy(alpha = 0.1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Data Savings active:", style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold, color = Color(0xFF43A047))
                            Spacer(Modifier.height(4.dp))
                            listOf(
                                "• Images: WebP format requested, quality reduced",
                                "• Background scripts: deferred loading",
                                "• Autoplay media: blocked",
                                "• Fonts: system fallback preferred"
                            ).forEach { hint ->
                                Text(hint, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp
        ),
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ContentToggleRow(
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
            modifier = Modifier.size(36.dp).clip(CircleShape)
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
