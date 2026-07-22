package com.example.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.text.isDigitsOnly
import kotlin.math.roundToInt

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkSettingsScreen(
    onBack: () -> Unit,
    viewModel: com.example.ui.viewmodel.MainViewModel
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val isRateLimit by viewModel.isRateLimit.collectAsState()
    val maxRate by viewModel.maxRate.collectAsState()
    val isAria2c by viewModel.isAria2c.collectAsState()
    val isProxyEnabled by viewModel.isProxyEnabled.collectAsState()
    val proxyUrl by viewModel.proxyUrl.collectAsState()
    val concurrentFragments by viewModel.concurrentFragments.collectAsState()
    val isForceIpv4 by viewModel.isForceIpv4.collectAsState()
    val isCookiesEnabled by viewModel.isCookiesEnabled.collectAsState()
    val isWifiOnly by viewModel.isWifiOnly.collectAsState()

    var showRateLimitDialog by remember { mutableStateOf(false) }
    var showProxyDialog by remember { mutableStateOf(false) }
    var showConcurrentDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Network",
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
                SectionHeader(text = "General")

                SettingsCard {
                    // Rate Limit
                    SwitchWithDetailRow(
                        icon = Icons.Outlined.Speed,
                        iconTint = Color(0xFFFF9800),
                        title = "Rate limit",
                        subtitle = if (isRateLimit) "$maxRate K/s" else "Disabled",
                        checked = isRateLimit,
                        onCheckedChange = { viewModel.isRateLimit.value = it },
                        onClick = { showRateLimitDialog = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Download with cellular (inverse of isWifiOnly)
                    SwitchRow(
                        icon = if (isWifiOnly) Icons.Outlined.SignalCellularConnectedNoInternet4Bar
                                else Icons.Outlined.SignalCellular4Bar,
                        iconTint = Color(0xFF4CAF50),
                        title = "Download with cellular",
                        subtitle = "Allow downloads over cellular networks",
                        checked = !isWifiOnly,
                        onCheckedChange = { viewModel.isWifiOnly.value = !it }
                    )
                }

                SectionHeader(text = "Advanced")

                SettingsCard {
                    // aria2
                    SwitchRow(
                        icon = Icons.Outlined.Bolt,
                        iconTint = Color(0xFFFF5722),
                        title = "aria2",
                        subtitle = "Use aria2c for downloads",
                        checked = isAria2c,
                        onCheckedChange = { viewModel.isAria2c.value = it }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Proxy
                    SwitchWithDetailRow(
                        icon = Icons.Outlined.VpnKey,
                        iconTint = Color(0xFF673AB7),
                        title = "Proxy",
                        subtitle = if (isProxyEnabled && proxyUrl.isNotBlank()) proxyUrl else "Configure proxy server",
                        checked = isProxyEnabled,
                        onCheckedChange = { viewModel.isProxyEnabled.value = it },
                        onClick = { showProxyDialog = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Concurrent fragments
                    DetailRow(
                        icon = Icons.Outlined.OfflineBolt,
                        iconTint = Color(0xFF009688),
                        title = "Concurrent fragments",
                        subtitle = "$concurrentFragments fragments per download",
                        enabled = !isAria2c,
                        onClick = { showConcurrentDialog = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Force IPv4
                    SwitchRow(
                        icon = Icons.Outlined.SettingsEthernet,
                        iconTint = Color(0xFF607D8B),
                        title = "Force IPv4",
                        subtitle = "Force IPv4 for connections",
                        checked = isForceIpv4,
                        onCheckedChange = { viewModel.isForceIpv4.value = it }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Cookies
                    SwitchRow(
                        icon = Icons.Outlined.Cookie,
                        iconTint = Color(0xFF795548),
                        title = "Cookies",
                        subtitle = if (isCookiesEnabled) "Cookies enabled for downloads" else "Disabled",
                        checked = isCookiesEnabled,
                        onCheckedChange = { viewModel.isCookiesEnabled.value = it }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    )

    if (showRateLimitDialog) {
        RateLimitInputDialog(
            currentRate = maxRate,
            onDismiss = { showRateLimitDialog = false },
            onConfirm = { viewModel.maxRate.value = it }
        )
    }

    if (showProxyDialog) {
        ProxyInputDialog(
            currentUrl = proxyUrl,
            onDismiss = { showProxyDialog = false },
            onConfirm = { viewModel.proxyUrl.value = it }
        )
    }

    if (showConcurrentDialog) {
        ConcurrentFragmentsDialog(
            currentFragments = concurrentFragments,
            onDismiss = { showConcurrentDialog = false },
            onConfirm = { viewModel.concurrentFragments.value = it }
        )
    }
}

// ─── Dialogs ────────────────────────────────────────────────────────────────

@Composable
private fun RateLimitInputDialog(
    currentRate: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentRate) }
    var isError by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Speed, null) },
        title = { Text("Rate limit") },
        text = {
            Column {
                Text(
                    "Set the maximum download rate (K/s). Value must be between 1 and 1,000,000.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        if (it.isDigitsOnly()) {
                            text = it
                            isError = false
                        }
                    },
                    label = { Text("Max rate") },
                    trailingIcon = { Text("K") },
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("Invalid input", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = text.toIntOrNull()
                if (value != null && value in 1..1_000_000) {
                    onConfirm(text)
                    onDismiss()
                } else {
                    isError = true
                }
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ProxyInputDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.VpnKey, null) },
        title = { Text("Proxy") },
        text = {
            Column {
                Text(
                    "Enter the proxy server URL for downloads. Leave empty to disable.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Proxy URL") },
                    placeholder = { Text("http://127.0.0.1:8080") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text); onDismiss() }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConcurrentFragmentsDialog(
    currentFragments: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var sliderValue by remember {
        val initial = when {
            currentFragments <= 1 -> 0f
            currentFragments <= 8 -> 0.33f
            currentFragments <= 16 -> 0.66f
            else -> 1f
        }
        mutableFloatStateOf(initial)
    }
    val count = remember {
        derivedStateOf {
            if (sliderValue <= 0.125f) 1 else ((sliderValue * 3f).roundToInt()) * 8
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.OfflineBolt, null) },
        title = { Text("Concurrent fragments") },
        text = {
            Column {
                Text(
                    "Number of concurrent fragments per download: ${count.value}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                val interactionSource = remember { MutableInteractionSource() }
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    steps = 2,
                    valueRange = 0f..1f,
                    thumb = {
                        SliderDefaults.Thumb(
                            interactionSource = interactionSource,
                            thumbSize = DpSize(4.dp, 32.dp)
                        )
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("24", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(count.value); onDismiss() }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ─── Reusable Components (from FormatSettingsScreen style) ───────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) { Column(modifier = Modifier.padding(vertical = 4.dp), content = content) }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SwitchWithDetailRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text("Configure", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.7f else 0.3f)
            )
        }
        Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}
