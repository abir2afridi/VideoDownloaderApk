package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isAmoledMode by viewModel.isAmoledMode.collectAsState()
            val selectedAccentColor by viewModel.selectedAccentColor.collectAsState()
            val selectedThemeMode by viewModel.selectedThemeMode.collectAsState()

            val darkTheme = when (selectedThemeMode) {
                "Dark" -> true
                "Light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            MyApplicationTheme(
                darkTheme = darkTheme,
                isAmoled = isAmoledMode,
                accentColor = selectedAccentColor
            ) {
                var currentTab by remember { mutableStateOf("Home") }

                Box(modifier = Modifier.fillMaxSize()) {
                    // Content Area
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (currentTab) {
                            "Home" -> DashboardTab(viewModel, onNavigateToTab = { currentTab = it })
                            "Browser" -> BrowserTab(viewModel)
                            "Downloads" -> DownloadsTab(viewModel)
                            "Vault" -> VaultTab(viewModel)
                            "Files" -> FilesTab(viewModel)
                            "Settings" -> SettingsTab(viewModel)
                        }
                    }

                    // Floating Bottom Bar
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bottom_nav_bar"),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            tonalElevation = 3.dp,
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            ),
                            shadowElevation = 8.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FloatingNavItem(
                                    selected = currentTab == "Home",
                                    onClick = { currentTab = "Home" },
                                    filledIcon = Icons.Filled.Home,
                                    outlinedIcon = Icons.Outlined.Home,
                                    label = "Home",
                                    modifier = Modifier.testTag("nav_home")
                                )
                                FloatingNavItem(
                                    selected = currentTab == "Browser",
                                    onClick = { currentTab = "Browser" },
                                    filledIcon = Icons.Filled.Language,
                                    outlinedIcon = Icons.Outlined.Language,
                                    label = "Browser",
                                    modifier = Modifier.testTag("nav_browser")
                                )
                                FloatingNavItem(
                                    selected = currentTab == "Downloads",
                                    onClick = { currentTab = "Downloads" },
                                    filledIcon = Icons.Filled.CloudDownload,
                                    outlinedIcon = Icons.Outlined.CloudDownload,
                                    label = "Downloads",
                                    modifier = Modifier.testTag("nav_downloads")
                                )
                                FloatingNavItem(
                                    selected = currentTab == "Files",
                                    onClick = { currentTab = "Files" },
                                    filledIcon = Icons.Filled.Folder,
                                    outlinedIcon = Icons.Outlined.Folder,
                                    label = "Files",
                                    modifier = Modifier.testTag("nav_files")
                                )
                                FloatingNavItem(
                                    selected = currentTab == "Settings",
                                    onClick = { currentTab = "Settings" },
                                    filledIcon = Icons.Filled.Settings,
                                    outlinedIcon = Icons.Outlined.Settings,
                                    label = "Settings",
                                    modifier = Modifier.testTag("nav_settings")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.FloatingNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    filledIcon: ImageVector,
    outlinedIcon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    )

    val activeIndicatorColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 200)
    )

    val activeTextColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 200)
    )

    val iconColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 200)
    )

    Box(
        modifier = modifier
            .weight(1f)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // Disable the native card rectangle ripple since we style a custom rounded pill
                onClick = onClick
            )
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Pill containing the icon
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(activeIndicatorColor)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (selected) filledIcon else outlinedIcon,
                    contentDescription = label,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = activeTextColor
            )
        }
    }
}
