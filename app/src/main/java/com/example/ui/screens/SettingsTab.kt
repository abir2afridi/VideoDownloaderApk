package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.ui.viewmodel.MainViewModel
import com.example.data.download.InstagramCookieStore
import java.io.File
import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import com.example.ui.theme.*

import androidx.compose.foundation.BorderStroke
import com.example.ui.components.TabHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    viewModel: MainViewModel,
    onNavigateToAbout: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val isAmoledMode by viewModel.isAmoledMode.collectAsState()
    val isWifiOnly by viewModel.isWifiOnly.collectAsState()
    val isHttpsOnly by viewModel.isHttpsOnly.collectAsState()
    val isTrackerBlocking by viewModel.isTrackerBlocking.collectAsState()
    val maxActiveDownloads by viewModel.maxActiveDownloads.collectAsState()
    val selectedAccentColor by viewModel.selectedAccentColor.collectAsState()
    val selectedThemeMode by viewModel.selectedThemeMode.collectAsState()
    val browserTogglePosition by viewModel.browserTogglePosition.collectAsState()
    val isForceDarkWeb by viewModel.isForceDarkWeb.collectAsState()
    val downloadFolderPath by viewModel.downloadFolderPath.collectAsState()

    // Folder Picker Launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri: Uri? ->
            uri?.let {
                // Persist permissions
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                
                // For simplicity in this app, we'll try to get a display path or just use the URI string
                // Ideally, we'd use DocumentFile, but for a simple "Path" display:
                val path = it.path ?: it.toString()
                
                // We'll update the viewmodel. Note: In a real app, you'd store the URI 
                // and use DocumentFile for file operations on modern Android.
                viewModel.downloadFolderPath.value = path
                Toast.makeText(context, "Download folder updated!", Toast.LENGTH_SHORT).show()
            }
        }
    )

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                TabHeader(
                    category = "NexLoad Pro",
                    title = "Preferences"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: UI & Styling
            SettingsSectionHeader(title = "UI & Customization")

            // Theme Mode Selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Brightness4, contentDescription = null, tint = Color.Gray)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Theme Mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Switch between Light, Dark, or System mode", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
                
                var themeExpanded by remember { mutableStateOf(false) }
                Box {
                    Button(onClick = { themeExpanded = true }, modifier = Modifier.testTag("theme_mode_selector")) {
                        Text(selectedThemeMode)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = themeExpanded, onDismissRequest = { themeExpanded = false }) {
                        listOf("System", "Light", "Dark").forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode) },
                                onClick = {
                                    viewModel.selectedThemeMode.value = mode
                                    themeExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            
            // AMOLED Black Switch
            SettingsToggleRow(
                icon = Icons.Default.BrightnessMedium,
                title = "AMOLED Black Mode",
                subtitle = "Use pure black pitch dark theme to maximize OLED battery savings",
                checked = isAmoledMode,
                onCheckedChange = { viewModel.isAmoledMode.value = it },
                tag = "amoled_mode_switch"
            )

            // Accent Color Selection
            var showColorPicker by remember { mutableStateOf(false) }
            val accentColors = listOf(
                "Bento" to null,
                "Teal" to TealPrimary,
                "Blue" to BluePrimary,
                "Orange" to OrangePrimary,
                "#FF1744" to Color(0xFFFF1744), // Red
                "#D81B60" to Color(0xFFD81B60), // Pink
                "#8E24AA" to Color(0xFF8E24AA), // Purple
                "#3949AB" to Color(0xFF3949AB), // Indigo
                "#00ACC1" to Color(0xFF00ACC1), // Cyan
                "#43A047" to Color(0xFF43A047), // Green
                "#FDD835" to Color(0xFFFDD835), // Yellow
                "#FB8C00" to Color(0xFFFB8C00), // Orange
                "#6D4C41" to Color(0xFF6D4C41)  // Brown
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = Color.Gray)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Theme Accent Color", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Choose application secondary branding accents", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
                
                Surface(
                    onClick = { showColorPicker = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = if (selectedAccentColor.startsWith("#")) "Custom" else selectedAccentColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (showColorPicker) {
                AlertDialog(
                    onDismissRequest = { showColorPicker = false },
                    title = { Text("Pick Accent Color") },
                    text = {
                        Column {
                            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
                                contentPadding = PaddingValues(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.height(240.dp)
                            ) {
                                items(accentColors.size) { index ->
                                    val (name, color) = accentColors[index]
                                    val isSelected = selectedAccentColor == name
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(color ?: BentoPrimary)
                                            .clickable {
                                                viewModel.selectedAccentColor.value = name
                                                showColorPicker = false
                                            }
                                            .border(
                                                width = 3.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                                        }
                                        if (name == "Bento") {
                                            Text("B", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showColorPicker = false }) {
                            Text("Close")
                        }
                    }
                )
            }

            // Browser Toggle Position
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.OpenInFull, contentDescription = null, tint = Color.Gray)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Browser Toggle Position", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Where to place the collapsed navigation icon", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
                
                var posExpanded by remember { mutableStateOf(false) }
                Box {
                    Button(onClick = { posExpanded = true }, modifier = Modifier.testTag("toggle_pos_selector")) {
                        Text(browserTogglePosition)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = posExpanded, onDismissRequest = { posExpanded = false }) {
                        listOf("Bottom Left", "Bottom Center", "Bottom Right").forEach { pos ->
                            DropdownMenuItem(
                                text = { Text(pos) },
                                onClick = {
                                    viewModel.browserTogglePosition.value = pos
                                    posExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Divider()

            // Section 2: Engine Preferences
            SettingsSectionHeader(title = "Download Engine")

            // Wi-Fi Only Switch
            SettingsToggleRow(
                icon = Icons.Default.Wifi,
                title = "Wi-Fi Only Downloads",
                subtitle = "Prevent downloading on cellular networks to conserve mobile plan data",
                checked = isWifiOnly,
                onCheckedChange = { viewModel.isWifiOnly.value = it },
                tag = "wifi_only_switch"
            )

            // Download Path Display
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { folderPickerLauncher.launch(null) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = Color.Gray)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Storage Path", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = downloadFolderPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Change path",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Max downloads count
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row {
                    Icon(Icons.Default.SlowMotionVideo, contentDescription = null, tint = Color.Gray)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Max Parallel Downloads: $maxActiveDownloads", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Limit concurrent background thread downloader tasks", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
                Slider(
                    value = maxActiveDownloads.toFloat(),
                    onValueChange = { viewModel.maxActiveDownloads.value = it.toInt() },
                    valueRange = 1f..5f,
                    steps = 3,
                    modifier = Modifier.fillMaxWidth().testTag("max_downloads_slider")
                )
            }

            Divider()

            // Section 3: Social Media Cookies
            SettingsSectionHeader(title = "Social Media Authentication")

            val hasIgCookies = remember { mutableStateOf(InstagramCookieStore.hasCookies()) }

            // Instagram Login via WebView
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { InstagramLoginActivity.start(context) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Cookie, contentDescription = null, tint = Color(0xFFE1306C))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Instagram Login", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (hasIgCookies.value) "Logged in — Instagram downloads enabled" else "Tap to login with Instagram (needed for downloads)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                if (hasIgCookies.value) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Logged in",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (hasIgCookies.value) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            InstagramCookieStore.clearCookies()
                            hasIgCookies.value = false
                            Toast.makeText(context, "Instagram session cleared", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Gray)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Logout Instagram", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
            }

            Divider()

            // Section 4: Browser Privacy
            SettingsSectionHeader(title = "Browser Security & Privacy")

            SettingsToggleRow(
                icon = Icons.Default.Https,
                title = "HTTPS-Only Protocol",
                subtitle = "Enforce secure connections across browser navigation endpoints",
                checked = isHttpsOnly,
                onCheckedChange = { viewModel.isHttpsOnly.value = it },
                tag = "https_only_switch"
            )

            SettingsToggleRow(
                icon = Icons.Default.Shield,
                title = "Tracker and Ad Blocking",
                subtitle = "Prevent malicious telemetry scripts from scanning connection metadata",
                checked = isTrackerBlocking,
                onCheckedChange = { viewModel.isTrackerBlocking.value = it },
                tag = "tracker_block_switch"
            )

            SettingsToggleRow(
                icon = Icons.Default.DarkMode,
                title = "Force Dark Mode for Web",
                subtitle = "Attempts to render all websites in dark theme automatically",
                checked = isForceDarkWeb,
                onCheckedChange = { viewModel.isForceDarkWeb.value = it },
                tag = "force_dark_web_switch"
            )

            Divider()

            // Section 5: Vault & Security reset
            SettingsSectionHeader(title = "Privacy Vault Security")
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.resetVault()
                        Toast.makeText(context, "Vault successfully wiped and reset", Toast.LENGTH_SHORT).show()
                    }
                    .padding(vertical = 8.dp)
                    .testTag("reset_vault_row"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.LockReset, contentDescription = null, tint = Color.Red)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Reset Secure Private Vault", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.Red)
                    Text("Clear recovery passwords, security hint and reset folder access", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }

            Divider()

            // Section 6: About
            SettingsSectionHeader(title = "About")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToAbout() }
                    .padding(vertical = 8.dp)
                    .testTag("about_row"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("About NexLoad", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text("App info, features, technology & credits", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f)) {
            Icon(icon, contentDescription = null, tint = Color.Gray)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
