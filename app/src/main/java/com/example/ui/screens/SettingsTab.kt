package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import com.example.ui.screens.browser.BrowserContentSettingsScreen
import com.example.ui.screens.browser.BrowserHomepageSettingsScreen
import com.example.ui.screens.browser.BrowserPrivacyScreen
import com.example.ui.screens.browser.BrowserSearchSettingsScreen
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    viewModel: MainViewModel,
    onNavigateToAbout: () -> Unit,
    onNavigateToHome: () -> Unit = {}
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
    val isTime24Hour by viewModel.isTime24Hour.collectAsState()
    val hourColor by viewModel.hourColor.collectAsState()
    val minuteColor by viewModel.minuteColor.collectAsState()
    val secondColor by viewModel.secondColor.collectAsState()

    var showPrivacyScreen by remember { mutableStateOf(false) }
    var showSearchScreen by remember { mutableStateOf(false) }
    var showContentScreen by remember { mutableStateOf(false) }
    var showHomepageScreen by remember { mutableStateOf(false) }

    BackHandler {
        when {
            showPrivacyScreen -> showPrivacyScreen = false
            showSearchScreen -> showSearchScreen = false
            showContentScreen -> showContentScreen = false
            showHomepageScreen -> showHomepageScreen = false
            else -> onNavigateToHome()
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri: Uri? ->
            uri?.let {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                val path = it.path ?: it.toString()
                viewModel.downloadFolderPath.value = path
                Toast.makeText(context, "Download folder updated!", Toast.LENGTH_SHORT).show()
            }
        }
    )

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "NEXLOAD PRO",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                )
                Text(
                    text = "Preferences",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            SettingsCard(
                sectionIcon = Icons.Default.Palette,
                sectionTitle = "UI & Customization"
            ) {
                ThemeModeRow(selectedThemeMode, onModeSelected = { viewModel.selectedThemeMode.value = it })

                SettingsToggleRow(
                    icon = Icons.Default.BrightnessMedium,
                    iconTint = Color(0xFFFF9800),
                    title = "AMOLED Black Mode",
                    subtitle = "Pure black background for OLED battery savings",
                    checked = isAmoledMode,
                    onCheckedChange = { viewModel.isAmoledMode.value = it },
                    tag = "amoled_mode_switch"
                )

                AccentColorRow(
                    selectedAccentColor = selectedAccentColor,
                    onColorSelected = { viewModel.selectedAccentColor.value = it }
                )

                BrowserToggleRow(
                    browserTogglePosition = browserTogglePosition,
                    onPositionSelected = { viewModel.browserTogglePosition.value = it }
                )
            }

            SettingsCard(
                sectionIcon = Icons.Default.CloudDownload,
                sectionTitle = "Download Engine"
            ) {
                SettingsToggleRow(
                    icon = Icons.Default.Wifi,
                    iconTint = Color(0xFF2196F3),
                    title = "Wi-Fi Only Downloads",
                    subtitle = "Prevent downloads on cellular networks",
                    checked = isWifiOnly,
                    onCheckedChange = { viewModel.isWifiOnly.value = it },
                    tag = "wifi_only_switch"
                )

                DownloadPathRow(
                    downloadFolderPath = downloadFolderPath,
                    onPickFolder = { folderPickerLauncher.launch(null) }
                )

                MaxDownloadsRow(
                    maxActiveDownloads = maxActiveDownloads,
                    onValueChange = { viewModel.maxActiveDownloads.value = it }
                )
            }

            SettingsCard(
                sectionIcon = Icons.Default.Security,
                sectionTitle = "Browser Security & Privacy"
            ) {
                SettingsToggleRow(
                    icon = Icons.Default.Https,
                    iconTint = Color(0xFF4CAF50),
                    title = "HTTPS-Only Protocol",
                    subtitle = "Enforce secure connections in the browser",
                    checked = isHttpsOnly,
                    onCheckedChange = { viewModel.isHttpsOnly.value = it },
                    tag = "https_only_switch"
                )

                SettingsToggleRow(
                    icon = Icons.Default.Shield,
                    iconTint = Color(0xFFE91E63),
                    title = "Tracker & Ad Blocking",
                    subtitle = "Block telemetry and tracking scripts",
                    checked = isTrackerBlocking,
                    onCheckedChange = { viewModel.isTrackerBlocking.value = it },
                    tag = "tracker_block_switch"
                )

                SettingsToggleRow(
                    icon = Icons.Default.DarkMode,
                    iconTint = Color(0xFF9C27B0),
                    title = "Force Dark Mode for Web",
                    subtitle = "Auto-render websites in dark theme",
                    checked = isForceDarkWeb,
                    onCheckedChange = { viewModel.isForceDarkWeb.value = it },
                    tag = "force_dark_web_switch"
                )
            }

            SettingsCard(
                sectionIcon = Icons.Default.Tune,
                sectionTitle = "Browser Settings"
            ) {
                BrowserSettingsRow(
                    icon = Icons.Default.Security,
                    iconTint = Color(0xFF43A047),
                    title = "Privacy & Security",
                    subtitle = "Cookies, site permissions, autofill, Web3",
                    onClick = { showPrivacyScreen = true }
                )
                BrowserSettingsRow(
                    icon = Icons.Default.Search,
                    iconTint = Color(0xFF1E88E5),
                    title = "Search Settings",
                    subtitle = "Search engine, trending, suggestions",
                    onClick = { showSearchScreen = true }
                )
                BrowserSettingsRow(
                    icon = Icons.Default.Tune,
                    iconTint = Color(0xFF8E24AA),
                    title = "Content",
                    subtitle = "Text size, pop-ups, user agent, data saving",
                    onClick = { showContentScreen = true }
                )
                BrowserSettingsRow(
                    icon = Icons.Default.Home,
                    iconTint = Color(0xFFE65100),
                    title = "Homepage",
                    subtitle = "Speed dial, suggested sites, news feed",
                    onClick = { showHomepageScreen = true }
                )
            }

            SettingsCard(
                sectionIcon = Icons.Default.AccessTime,
                sectionTitle = "Time Display Settings"
            ) {
                SettingsToggleRow(
                    icon = Icons.Default.Schedule,
                    iconTint = Color(0xFF00BCD4),
                    title = "24-Hour Format",
                    subtitle = if (isTime24Hour) "Show time in 24-hour format" else "Show time in 12-hour format",
                    checked = isTime24Hour,
                    onCheckedChange = { viewModel.isTime24Hour.value = it },
                    tag = "time_format_switch"
                )

                TimeColorRow(
                    label = "Hour Color",
                    currentColor = hourColor,
                    onColorSelected = { viewModel.hourColor.value = it }
                )
                TimeColorRow(
                    label = "Minute Color",
                    currentColor = minuteColor,
                    onColorSelected = { viewModel.minuteColor.value = it }
                )
                TimeColorRow(
                    label = "Second Color",
                    currentColor = secondColor,
                    onColorSelected = { viewModel.secondColor.value = it }
                )
            }

            SettingsCard(
                sectionIcon = Icons.Default.Lock,
                sectionTitle = "Privacy Vault Security"
            ) {
                ResetVaultRow(onReset = {
                    viewModel.resetVault()
                    Toast.makeText(context, "Vault reset successfully", Toast.LENGTH_SHORT).show()
                })
            }

            SettingsCard(
                sectionIcon = Icons.Default.Info,
                sectionTitle = "About"
            ) {
                AboutRow(onNavigateToAbout = onNavigateToAbout)
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }

    if (showPrivacyScreen) {
        BrowserPrivacyScreen(
            viewModel = viewModel,
            onBack = { showPrivacyScreen = false }
        )
    }
    if (showSearchScreen) {
        BrowserSearchSettingsScreen(
            viewModel = viewModel,
            onBack = { showSearchScreen = false }
        )
    }
    if (showContentScreen) {
        BrowserContentSettingsScreen(
            viewModel = viewModel,
            onBack = { showContentScreen = false }
        )
    }
    if (showHomepageScreen) {
        BrowserHomepageSettingsScreen(
            viewModel = viewModel,
            onBack = { showHomepageScreen = false }
        )
    }
}

@Composable
private fun BrowserSettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconTint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint.copy(alpha = 0.9f),
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun SettingsCard(
    sectionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    sectionTitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = sectionIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = sectionTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            content()
        }
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint.copy(alpha = 0.9f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun ThemeModeRow(
    selectedThemeMode: String,
    onModeSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val modes = listOf("System", "Light", "Dark")

    SettingsRow(
        icon = Icons.Default.Brightness4,
        iconTint = Color(0xFFFF9800),
        title = "Theme Mode",
        subtitle = "System, Light, or Dark appearance",
        trailing = {
            Box {
                Surface(
                    onClick = { expanded = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    modifier = Modifier.testTag("theme_mode_selector")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val modeIcon = when (selectedThemeMode) {
                            "Light" -> Icons.Default.LightMode
                            "Dark" -> Icons.Default.DarkMode
                            else -> Icons.Default.BrightnessAuto
                        }
                        Icon(
                            imageVector = modeIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = selectedThemeMode,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    modes.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val mIcon = when (mode) {
                                        "Light" -> Icons.Default.LightMode
                                        "Dark" -> Icons.Default.DarkMode
                                        else -> Icons.Default.BrightnessAuto
                                    }
                                    Icon(
                                        imageVector = mIcon,
                                        contentDescription = null,
                                        tint = if (mode == selectedThemeMode) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = mode,
                                        fontWeight = if (mode == selectedThemeMode) FontWeight.Bold else FontWeight.Normal,
                                        color = if (mode == selectedThemeMode) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (mode == selectedThemeMode) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onModeSelected(mode)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun AccentColorRow(
    selectedAccentColor: String,
    onColorSelected: (String) -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }
    val accentColors = listOf(
        "Bento" to null,
        "Teal" to TealPrimary,
        "Blue" to BluePrimary,
        "Orange" to OrangePrimary,
        "#FF1744" to Color(0xFFFF1744),
        "#D81B60" to Color(0xFFD81B60),
        "#8E24AA" to Color(0xFF8E24AA),
        "#3949AB" to Color(0xFF3949AB),
        "#00ACC1" to Color(0xFF00ACC1),
        "#43A047" to Color(0xFF43A047),
        "#FDD835" to Color(0xFFFDD835),
        "#FB8C00" to Color(0xFFFB8C00),
        "#6D4C41" to Color(0xFF6D4C41)
    )

    SettingsRow(
        icon = Icons.Default.Palette,
        iconTint = Color(0xFFE91E63),
        title = "Accent Color",
        subtitle = if (selectedAccentColor.startsWith("#")) "Custom color" else "$selectedAccentColor theme",
        trailing = {
            Surface(
                onClick = { showColorPicker = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = if (selectedAccentColor.startsWith("#")) "Custom" else selectedAccentColor,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    )

    if (showColorPicker) {
        ColorPickerDialog(
            accentColors = accentColors,
            selectedAccentColor = selectedAccentColor,
            currentPrimary = MaterialTheme.colorScheme.primary,
            onColorSelected = { color ->
                onColorSelected(color)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }
}

@Composable
private fun SaturationValuePicker(
    hue: Float,
    saturation: Float,
    value: Float,
    onColorChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(hue) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val s = (change.position.x / size.width).coerceIn(0f, 1f)
                    val v = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                    onColorChange(s, v)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Hue background
            drawRect(color = Color.hsv(hue, 1f, 1f))
            
            // White to transparent horizontal gradient
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.White, Color.Transparent)
                )
            )
            
            // Transparent to black vertical gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black)
                )
            )
            
            val x = saturation * size.width
            val y = (1f - value) * size.height
            
            // Pointer
            drawCircle(
                color = Color.Black.copy(alpha = 0.15f),
                radius = 12.dp.toPx(),
                center = Offset(x, y)
            )
            drawCircle(
                color = Color.White,
                radius = 10.dp.toPx(),
                center = Offset(x, y),
                style = Stroke(width = 2.5.dp.toPx())
            )
            drawCircle(
                color = Color.hsv(hue, saturation, value),
                radius = 8.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}

@Composable
private fun HueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Hue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${hue.toInt()}°", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val h = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                        onHueChange(h)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val hueColors = listOf(
                    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                )
                drawRoundRect(
                    brush = Brush.horizontalGradient(hueColors),
                    cornerRadius = CornerRadius(10.dp.toPx())
                )
                
                val x = (hue / 360f) * size.width
                drawCircle(
                    color = Color.White,
                    radius = 10.dp.toPx(),
                    center = Offset(x, size.height / 2),
                    style = Stroke(width = 2.5.dp.toPx())
                )
                drawCircle(
                    color = Color.hsv(hue, 1f, 1f),
                    radius = 7.dp.toPx(),
                    center = Offset(x, size.height / 2)
                )
            }
        }
    }
}


@Composable
private fun ColorPickerDialog(
    accentColors: List<Pair<String, Color?>>,
    selectedAccentColor: String,
    currentPrimary: Color,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Pick Accent Color",
    showDefault: Boolean = false
) {
    var hue by remember { mutableFloatStateOf(0f) }
    var sat by remember { mutableFloatStateOf(0f) }
    var value by remember { mutableFloatStateOf(1f) }
    var hexInput by remember { mutableStateOf("#") }

    val customColor = Color.hsv(hue, sat, value)
    val customHex = formatHexColor(customColor)

    // Sync hex input when color changes
    LaunchedEffect(customHex) {
        if (!hexInput.startsWith("#") || hexInput.length != 7) {
            hexInput = customHex
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(bottom = 0.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(currentPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Palette, null, tint = currentPrimary, modifier = Modifier.size(18.dp))
                        }
                        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Scrollable content
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Presets
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Favorite, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                            Text("Presets", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        
                        val allSwatches = if (showDefault) {
                            listOf("Default" to null as Color?) + accentColors
                        } else accentColors
                        
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(5),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.height(if (allSwatches.size > 5) 90.dp else 44.dp)
                        ) {
                            items(allSwatches.size) { index ->
                                val (name, color) = allSwatches[index]
                                val isSelected = selectedAccentColor == name
                                val swatchColor = when {
                                    color != null -> color
                                    name == "Default" -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                    else -> BentoPrimary
                                }
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                        .clickable { onColorSelected(name) }
                                        .border(
                                            width = if (isSelected) 2.5.dp else 0.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, null, tint = if (swatchColor.let { it.red * 0.299f + it.green * 0.587f + it.blue * 0.114f > 0.5f }) Color.Black else Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Color preview bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(customColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = customHex,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = if (customColor.let { c ->
                                    c.red * 0.299f + c.green * 0.587f + c.blue * 0.114f > 0.5f
                                }) Color.Black else Color.White
                            )
                        )
                    }

                    // SV Square
                    SaturationValuePicker(
                        hue = hue, saturation = sat, value = value,
                        onColorChange = { s, v -> sat = s; value = v }
                    )

                    // Hue Slider
                    HueSlider(
                        hue = hue,
                        onHueChange = { h -> hue = h }
                    )

                    // Hex Input Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = hexInput,
                            onValueChange = { input ->
                                if (input.length <= 7) hexInput = input.uppercase()
                            },
                            label = { Text("Hex", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        )
                        Button(
                            onClick = {
                                try {
                                    val c = Color(android.graphics.Color.parseColor(if (hexInput.startsWith("#")) hexInput else "#$hexInput"))
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(android.graphics.Color.rgb((c.red*255).toInt(), (c.green*255).toInt(), (c.blue*255).toInt()), hsv)
                                    hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                                } catch (_: Exception) {}
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text("Apply", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                }

                // Bottom bar
                Surface(
                    tonalElevation = 2.dp,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Close")
                        }
                        Button(
                            onClick = { onColorSelected(customHex) },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Apply", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}




private fun formatHexColor(color: Color): String {
    val r = (color.red * 255).toInt().coerceIn(0, 255)
    val g = (color.green * 255).toInt().coerceIn(0, 255)
    val b = (color.blue * 255).toInt().coerceIn(0, 255)
    return "#%02X%02X%02X".format(r, g, b)
}

@Composable
private fun BrowserToggleRow(
    browserTogglePosition: String,
    onPositionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val positions = listOf("Bottom Left", "Bottom Center", "Bottom Right")
    val positionIcons = mapOf(
        "Bottom Left" to Icons.Default.AlignHorizontalLeft,
        "Bottom Center" to Icons.Default.AlignHorizontalCenter,
        "Bottom Right" to Icons.Default.AlignHorizontalRight
    )

    SettingsRow(
        icon = Icons.Default.OpenInFull,
        iconTint = Color(0xFF00BCD4),
        title = "Browser Toggle Position",
        subtitle = "Collapsed nav icon alignment",
        trailing = {
            Box {
                Surface(
                    onClick = { expanded = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    modifier = Modifier.testTag("toggle_pos_selector")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = browserTogglePosition,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    positions.forEach { pos ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    positionIcons[pos]?.let {
                                        Icon(
                                            imageVector = it,
                                            contentDescription = null,
                                            tint = if (pos == browserTogglePosition) MaterialTheme.colorScheme.primary
                                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Text(
                                        text = pos,
                                        fontWeight = if (pos == browserTogglePosition) FontWeight.Bold else FontWeight.Normal,
                                        color = if (pos == browserTogglePosition) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (pos == browserTogglePosition) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onPositionSelected(pos)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    SettingsRow(
        icon = icon,
        iconTint = iconTint,
        title = title,
        subtitle = subtitle,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(tag)
            )
        }
    )
}

@Composable
private fun DownloadPathRow(
    downloadFolderPath: String,
    onPickFolder: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable { onPickFolder() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF4CAF50).copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = Color(0xFF4CAF50).copy(alpha = 0.9f),
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Storage Path",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = downloadFolderPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ) {
            Box(modifier = Modifier.padding(8.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Change path",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun MaxDownloadsRow(
    maxActiveDownloads: Int,
    onValueChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF9C27B0).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SlowMotionVideo,
                    contentDescription = null,
                    tint = Color(0xFF9C27B0).copy(alpha = 0.9f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = "Parallel Downloads: $maxActiveDownloads",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Limit concurrent background downloaders",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "1",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Slider(
                value = maxActiveDownloads.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = 1f..5f,
                steps = 3,
                modifier = Modifier
                    .weight(1f)
                    .testTag("max_downloads_slider")
            )
            Text(
                text = "5",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Text(
                    text = "$maxActiveDownloads",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ResetVaultRow(onReset: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable { onReset() }
            .testTag("reset_vault_row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF44336).copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.LockReset,
                contentDescription = null,
                tint = Color(0xFFF44336).copy(alpha = 0.9f),
                modifier = Modifier.size(20.dp)
            )
        }
        Column {
            Text(
                text = "Reset Secure Vault",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFF44336)
            )
            Text(
                text = "Clear password, hint, and vault access",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun AboutRow(onNavigateToAbout: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable { onNavigateToAbout() }
            .testTag("about_row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "About NexLoad",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "App info, features, technology & credits",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun TimeColorRow(
    label: String,
    currentColor: String,
    onColorSelected: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val displayColor = if (currentColor == "Default") MaterialTheme.colorScheme.onSurface
                       else try { Color(android.graphics.Color.parseColor(currentColor)) } catch (_: Exception) { MaterialTheme.colorScheme.onSurface }
    val displayName = if (currentColor == "Default") "Auto" else currentColor

    SettingsRow(
        icon = Icons.Default.FormatColorFill,
        iconTint = if (currentColor == "Default") Color(0xFF9E9E9E) else displayColor,
        title = label,
        subtitle = displayName,
        trailing = {
            Surface(
                onClick = { showPicker = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(displayColor)
                    )
                    Text(
                        text = "Edit",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    )

    if (showPicker) {
        val accentColors = listOf(
            "#FF1744" to Color(0xFFFF1744),
            "#D81B60" to Color(0xFFD81B60),
            "#8E24AA" to Color(0xFF8E24AA),
            "#3949AB" to Color(0xFF3949AB),
            "#00ACC1" to Color(0xFF00ACC1),
            "#43A047" to Color(0xFF43A047),
            "#FDD835" to Color(0xFFFDD835),
            "#FB8C00" to Color(0xFFFB8C00),
            "#6D4C41" to Color(0xFF6D4C41),
            "#FFFFFF" to Color.White,
            "#000000" to Color.Black,
            "#E0E0E0" to Color(0xFFE0E0E0)
        )
        ColorPickerDialog(
            accentColors = accentColors,
            selectedAccentColor = currentColor,
            currentPrimary = if (currentColor == "Default") MaterialTheme.colorScheme.primary else displayColor,
            onColorSelected = { color -> onColorSelected(color); showPicker = false },
            onDismiss = { showPicker = false },
            title = "Pick $label",
            showDefault = true
        )
    }
}
