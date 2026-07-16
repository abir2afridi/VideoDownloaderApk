package com.example.ui.screens.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
fun BrowserSiteListScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val notificationPermission by viewModel.notificationPermission.collectAsState()
    val allowedNotificationSites by viewModel.allowedNotificationSites.collectAsState()
    val locationPermission by viewModel.locationPermission.collectAsState()
    val microphonePermission by viewModel.microphonePermission.collectAsState()

    var showResetConfirm by remember { mutableStateOf(false) }
    var showNotificationSites by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Site Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Info card
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "These are your global permission defaults. Individual site permissions are managed per-session by the browser.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Section: Default Permissions
            item {
                Text(
                    "DEFAULT PERMISSIONS",
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                SitePermRow(
                    icon = Icons.Default.LocationOn,
                    iconColor = Color(0xFF8E24AA),
                    title = "Location",
                    value = locationPermission,
                    options = listOf("Ask" to "Ask every time", "Allowed" to "Always allowed", "Denied" to "Always denied"),
                    onSelect = { viewModel.locationPermission.value = it }
                )
            }

            item {
                SitePermRow(
                    icon = Icons.Default.Notifications,
                    iconColor = Color(0xFFE53935),
                    title = "Notifications",
                    value = notificationPermission,
                    options = listOf("Ask" to "Ask every time", "Allowed" to "Always allowed", "Denied" to "Always denied"),
                    onSelect = { viewModel.notificationPermission.value = it }
                )
            }

            item {
                SitePermRow(
                    icon = Icons.Default.Mic,
                    iconColor = Color(0xFF00ACC1),
                    title = "Microphone",
                    value = microphonePermission,
                    options = listOf("Ask" to "Ask every time", "Allowed" to "Always allowed", "Denied" to "Always denied"),
                    onSelect = { viewModel.microphonePermission.value = it }
                )
            }

            // Section: Allowed Notification Sites
            item {
                Text(
                    "NOTIFICATION ALLOWED SITES",
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    onClick = { showNotificationSites = !showNotificationSites }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null,
                                tint = Color(0xFFE53935), modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Allowed Sites", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (allowedNotificationSites.isEmpty()) "No sites allowed yet"
                                else "${allowedNotificationSites.size} site${if (allowedNotificationSites.size != 1) "s" else ""} allowed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            if (showNotificationSites) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            item {
                AnimatedVisibility(visible = showNotificationSites) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        if (allowedNotificationSites.isEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No notification permissions granted yet.\nSites that request notifications will appear here.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                        } else {
                            allowedNotificationSites.forEach { origin ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Language, contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text(origin, style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = { viewModel.removeNotificationSite(origin) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Revoke",
                                                tint = Color(0xFFE53935), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }

                            if (allowedNotificationSites.isNotEmpty()) {
                                TextButton(
                                    onClick = { viewModel.clearAllNotificationSites() },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Revoke All", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }

            // Section: Reset All Permissions
            item {
                Text(
                    "RESET",
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE53935).copy(alpha = 0.08f),
                    onClick = { showResetConfirm = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null,
                                tint = Color(0xFFE53935), modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Reset All Permissions", style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold, color = Color(0xFFE53935))
                            Text("Reset location, notifications, microphone to 'Ask every time'",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    // Reset confirmation dialog
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            icon = { Icon(Icons.Default.RestartAlt, contentDescription = null, tint = Color(0xFFE53935)) },
            title = { Text("Reset All Permissions?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will reset location, notifications, and microphone permissions to 'Ask every time'. Notification site allowlist will also be cleared.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.locationPermission.value = "Ask"
                        viewModel.notificationPermission.value = "Ask"
                        viewModel.microphonePermission.value = "Ask"
                        viewModel.externalAppsPolicy.value = "Ask"
                        viewModel.protectedContentPolicy.value = "Ask"
                        viewModel.clearAllNotificationSites()
                        showResetConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SitePermRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        onClick = { showDialog = true }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
                Text(
                    options.firstOrNull { it.first == value }?.second ?: value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    options.forEach { (optVal, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = value == optVal,
                                onClick = { onSelect(optVal); showDialog = false }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } }
        )
    }
}

