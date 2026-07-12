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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.MainViewModel
import java.io.File
import android.content.Intent

import androidx.compose.foundation.BorderStroke
import com.example.ui.components.TabHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(viewModel: MainViewModel) {
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
                    category = "Vortex Pro",
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
                
                var expanded by remember { mutableStateOf(false) }
                Box {
                    Button(onClick = { expanded = true }, modifier = Modifier.testTag("accent_color_selector")) {
                        Text(selectedAccentColor)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("Bento", "Teal", "Blue", "Orange").forEach { color ->
                            DropdownMenuItem(
                                text = { Text(color) },
                                onClick = {
                                    viewModel.selectedAccentColor.value = color
                                    expanded = false
                                }
                            )
                        }
                    }
                }
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

            // Section 3: Browser Privacy
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

            Divider()

            // Section 4: Vault & Security reset
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
