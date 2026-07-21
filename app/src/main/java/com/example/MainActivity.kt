package com.example

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.screens.*
import com.example.ui.screens.LoadingScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.data.download.InstagramCookieStore
import androidx.compose.animation.Crossfade

class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                viewModel.tryMigrateToPublicStorage()
                Toast.makeText(this, "File access granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "File access denied. App will use internal storage.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize cookie store
        InstagramCookieStore.init(this)

        // If already has MANAGE_EXTERNAL_STORAGE on Android 11+, attempt public directory
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            viewModel.tryMigrateToPublicStorage()
            requestStoragePermission()
        } else {
            requestStoragePermission()
        }
        
        setContent {
            var showLoadingScreen by remember { mutableStateOf(true) }
            
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(1800) // 1.8s allows animation to finish and settle
                showLoadingScreen = false
            }

            val isAmoledMode by viewModel.isAmoledMode.collectAsState()
            val selectedAccentColor by viewModel.selectedAccentColor.collectAsState()
            val selectedThemeMode by viewModel.selectedThemeMode.collectAsState()
            val browserTogglePosition by viewModel.browserTogglePosition.collectAsState()

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
                Crossfade(targetState = showLoadingScreen, label = "splash_transition") { loading ->
                    if (loading) {
                        LoadingScreen()
                    } else {
                        MainContent(viewModel, browserTogglePosition)
                    }
                }
            }
        }
    }

    @Composable
    private fun MainContent(
        viewModel: MainViewModel,
        browserTogglePosition: String
    ) {
        var currentTab by remember { mutableStateOf("Home") }
        var isNavCollapsed by remember { mutableStateOf(false) }

        // Auto-collapse when entering Browser tab
        LaunchedEffect(currentTab) {
            if (currentTab == "Browser") {
                isNavCollapsed = true
            } else {
                isNavCollapsed = false
            }
        }

        // Auto-lock vault when app goes to background
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    viewModel.lockVault()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // Content Area
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                when (currentTab) {
                    "Home" -> DashboardTab(viewModel, onNavigateToTab = { currentTab = it })
                    "Browser" -> BrowserTab(viewModel)
                    "Downloads" -> DownloadsTab(viewModel, onNavigateToHome = { currentTab = "Home" })
                    "Vault" -> VaultTab(viewModel)
                    "Settings" -> SettingsTab(
                        viewModel = viewModel,
                        onNavigateToAbout = { currentTab = "About" },
                        onNavigateToHome = { currentTab = "Home" }
                    )
                    "About" -> AboutTab(onBack = { currentTab = "Settings" })
                }
            }
            // Fallback: back to Home from any tab that doesn't handle back itself
            BackHandler(enabled = currentTab != "Home") {
                currentTab = "Home"
            }

            // Floating Bottom Bar with Animation
            if (currentTab != "About") {
                val navAlignment = if (isNavCollapsed && currentTab == "Browser") {
                    when (browserTogglePosition) {
                        "Bottom Left" -> Alignment.CenterStart
                        "Bottom Right" -> Alignment.CenterEnd
                        else -> Alignment.Center
                    }
                } else {
                    Alignment.Center
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    contentAlignment = navAlignment
                ) {
                    AnimatedContent(
                        targetState = isNavCollapsed && currentTab == "Browser",
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(220, delayMillis = 90)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)))
                                .togetherWith(fadeOut(animationSpec = tween(90)) + scaleOut(targetScale = 0.92f, animationSpec = tween(90)))
                        },
                        label = "nav_collapse_animation"
                    ) { collapsed ->
                        if (collapsed) {
                            // Collapsed Single Icon
                            Surface(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { isNavCollapsed = false }
                                    )
                                    .testTag("nav_expand_button"),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                tonalElevation = 6.dp,
                                shadowElevation = 8.dp,
                                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.2f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = "Expand Navigation",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        } else {
                            // Full Bottom Bar
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("bottom_nav_bar"),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                tonalElevation = 6.dp,
                                border = BorderStroke(
                                    width = 0.5.dp,
                                    color = Color.White.copy(alpha = 0.3f)
                                ),
                                shadowElevation = 12.dp
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
                                        onClick = { 
                                            if (currentTab == "Browser") isNavCollapsed = true 
                                            else currentTab = "Browser" 
                                        },
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
                                        label = "Library",
                                        modifier = Modifier.testTag("nav_downloads")
                                    )
                                    FloatingNavItem(
                                        selected = currentTab == "Settings" || currentTab == "About",
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

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            viewModel.tryMigrateToPublicStorage()
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showStoragePermissionDialog()
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1001)
            }
        }
    }

    private fun showStoragePermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("File Access Required")
            .setMessage("NexLoad needs access to your device storage to save downloads to your Downloads folder.\n\nPlease enable \"Allow management of all files\" on the next screen.")
            .setPositiveButton("Enable") { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                storagePermissionLauncher.launch(intent)
            }
            .setNegativeButton("Use Internal Storage") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
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
