package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookAndFeelScreen(
    onBack: () -> Unit,
    viewModel: com.example.ui.viewmodel.MainViewModel
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val selectedThemeMode by viewModel.selectedThemeMode.collectAsState()
    val isAmoledMode by viewModel.isAmoledMode.collectAsState()
    val selectedAccentColor by viewModel.selectedAccentColor.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Look & Feel",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Theme Mode Section
                Text(
                    text = "Theme Mode",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(modifier = Modifier.padding(4.dp)) {
                        val modes = listOf(
                            Triple("System", Icons.Outlined.SettingsBrightness, "Follow system setting"),
                            Triple("Light", Icons.Outlined.LightMode, "Always use light theme"),
                            Triple("Dark", Icons.Outlined.DarkMode, "Always use dark theme"),
                        )
                        modes.forEachIndexed { index, (mode, icon, desc) ->
                            val isSelected = selectedThemeMode == mode
                            val bgColor by animateColorAsState(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else Color.Transparent,
                                label = "themeBg"
                            )
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bgColor)
                                    .clickable { viewModel.selectedThemeMode.value = mode }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                color = Color.Transparent
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = mode,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mode,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // AMOLED Mode
                Text(
                    text = "Display",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.isAmoledMode.value = !isAmoledMode }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BrightnessMedium,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AMOLED Black Mode",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Pure black background for OLED battery savings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = isAmoledMode,
                            onCheckedChange = { viewModel.isAmoledMode.value = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Accent Color Section
                Text(
                    text = "Accent Color",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Preset colors row
                        val presetColors = listOf(
                            "Bento" to Color(0xFF00897B),
                            "Teal" to Color(0xFF009688),
                            "Blue" to Color(0xFF2196F3),
                            "Orange" to Color(0xFFFF9800),
                            "#FFFF1744" to Color(0xFFFF1744),
                            "#FF8E24AA" to Color(0xFF8E24AA),
                            "#FF3949AB" to Color(0xFF3949AB),
                            "#FF43A047" to Color(0xFF43A047),
                        )

                        Text(
                            text = "Presets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            presetColors.take(4).forEach { (name, color) ->
                                ColorCircle(
                                    color = color,
                                    isSelected = selectedAccentColor == name,
                                    onClick = { viewModel.selectedAccentColor.value = name }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            presetColors.drop(4).forEach { (name, color) ->
                                ColorCircle(
                                    color = color,
                                    isSelected = selectedAccentColor == name,
                                    onClick = { viewModel.selectedAccentColor.value = name }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Current selection indicator
                        val currentColor = when (selectedAccentColor) {
                            "Bento" -> Color(0xFF00897B)
                            "Teal" -> Color(0xFF009688)
                            "Blue" -> Color(0xFF2196F3)
                            "Orange" -> Color(0xFFFF9800)
                            else -> {
                                try {
                                    Color(android.graphics.Color.parseColor(selectedAccentColor))
                                } catch (e: Exception) {
                                    Color(0xFF00897B)
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = currentColor.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, currentColor.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(currentColor)
                                )
                                Column {
                                    Text(
                                        text = "Active Accent",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = selectedAccentColor,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = currentColor
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    )
}

@Composable
private fun ColorCircle(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primary
        else Color.Transparent,
        label = "border"
    )

    Surface(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = color,
        border = BorderStroke(3.dp, borderColor),
        shadowElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isSelected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
