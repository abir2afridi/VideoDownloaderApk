package com.example.ui.screens.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.browser.AdBlocker
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserPrivacyScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val isAdBlocking by viewModel.isAdBlocking.collectAsState()
    val isTrackerBlocking by viewModel.isTrackerBlocking.collectAsState()
    val cookieMode by viewModel.cookieMode.collectAsState()
    val isSecureDns by viewModel.isSecureDns.collectAsState()
    val secureDnsProvider by viewModel.secureDnsProvider.collectAsState()

    val clearDataOnExit by viewModel.clearDataOnExit.collectAsState()
    val clearHistoryOnExit by viewModel.clearHistoryOnExit.collectAsState()
    val clearTabsOnExit by viewModel.clearTabsOnExit.collectAsState()
    val clearSearchOnExit by viewModel.clearSearchOnExit.collectAsState()
    val clearCookiesOnExit by viewModel.clearCookiesOnExit.collectAsState()
    val clearCacheOnExit by viewModel.clearCacheOnExit.collectAsState()
    val clearAutofillOnExit by viewModel.clearAutofillOnExit.collectAsState()
    val clearDownloadsOnExit by viewModel.clearDownloadsOnExit.collectAsState()
    val showClearDataConfirmation by viewModel.showClearDataConfirmation.collectAsState()

    val isAutofillEnabled by viewModel.isAutofillEnabled.collectAsState()
    val isAutofillAddress by viewModel.isAutofillAddress.collectAsState()
    val isAutofillCards by viewModel.isAutofillCards.collectAsState()
    val isAutofillContacts by viewModel.isAutofillContacts.collectAsState()

    val locationPermission by viewModel.locationPermission.collectAsState()
    val notificationPermission by viewModel.notificationPermission.collectAsState()
    val microphonePermission by viewModel.microphonePermission.collectAsState()
    val externalAppsPolicy by viewModel.externalAppsPolicy.collectAsState()
    val protectedContentPolicy by viewModel.protectedContentPolicy.collectAsState()
    val isAutoDownload by viewModel.isAutoDownload.collectAsState()
    val cryptoWalletPolicy by viewModel.cryptoWalletPolicy.collectAsState()
    val isWeb3Enabled by viewModel.isWeb3Enabled.collectAsState()
    val web3Network by viewModel.web3Network.collectAsState()

    val blockedCount = AdBlocker.blockedDomainsCount(isAdBlocking, isTrackerBlocking)

    var showCookieDialog by remember { mutableStateOf(false) }
    var showDnsDialog by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var showNotifDialog by remember { mutableStateOf(false) }
    var showMicDialog by remember { mutableStateOf(false) }
    var showExternalAppsDialog by remember { mutableStateOf(false) }
    var showProtectedContentDialog by remember { mutableStateOf(false) }
    var showCryptoWalletDialog by remember { mutableStateOf(false) }
    var showClearAdvanced by remember { mutableStateOf(false) }
    var showBlockListScreen by remember { mutableStateOf(false) }
    var showSiteListScreen by remember { mutableStateOf(false) }
    var showWeb3Dialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & Security", fontWeight = FontWeight.Bold) },
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
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // ── SECTION: Privacy & Blocking ──────────────────────────────────
            item { PrivacySectionHeader("Privacy & Blocking") }

            item {
                PrivacyToggleRow(
                    icon = Icons.Default.Block,
                    iconColor = Color(0xFFE53935),
                    title = "Ad Blocking",
                    subtitle = "${AdBlocker.blockedDomainsCount(isAdBlocking, false)} ad domains blocked",
                    checked = isAdBlocking,
                    onCheckedChange = { viewModel.isAdBlocking.value = it }
                )
            }

            // View ad block list
            item {
                AnimatedVisibility(visible = isAdBlocking) {
                    PrivacyClickRow(
                        icon = Icons.Default.FormatListBulleted,
                        iconColor = Color(0xFFE53935),
                        title = "View Ad Block List",
                        subtitle = "${AdBlocker.blockedDomainsCount(true, false)} domains",
                        onClick = { showBlockListScreen = true },
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }

            item {
                PrivacyToggleRow(
                    icon = Icons.Default.Shield,
                    iconColor = Color(0xFF43A047),
                    title = "Tracker Blocking",
                    subtitle = "${AdBlocker.blockedDomainsCount(false, isTrackerBlocking)} tracker domains blocked",
                    checked = isTrackerBlocking,
                    onCheckedChange = { viewModel.isTrackerBlocking.value = it }
                )
            }

            // View tracker block list
            item {
                AnimatedVisibility(visible = isTrackerBlocking) {
                    PrivacyClickRow(
                        icon = Icons.Default.FormatListBulleted,
                        iconColor = Color(0xFF43A047),
                        title = "View Tracker Block List",
                        subtitle = "${AdBlocker.blockedDomainsCount(false, true)} domains",
                        onClick = { showBlockListScreen = true },
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }

            if (blockedCount > 0) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "$blockedCount domains protected against",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Cookies
            item {
                PrivacyClickRow(
                    icon = Icons.Default.Cookie,
                    iconColor = Color(0xFFFB8C00),
                    title = "Cookies",
                    subtitle = when (cookieMode) {
                        "All"          -> "Accept all cookies"
                        "NoThirdParty" -> "Block third-party cookies"
                        else           -> "Block all cookies"
                    },
                    onClick = { showCookieDialog = true }
                )
            }

            // Secure DNS
            item {
                Column {
                    PrivacyToggleRow(
                        icon = Icons.Default.Dns,
                        iconColor = Color(0xFF1E88E5),
                        title = "Secure DNS",
                        subtitle = if (isSecureDns) "Using ${secureDnsProvider} DoH" else "Using default system DNS",
                        checked = isSecureDns,
                        onCheckedChange = { viewModel.isSecureDns.value = it }
                    )
                    AnimatedVisibility(visible = isSecureDns) {
                        PrivacyClickRow(
                            icon = Icons.Default.NetworkCheck,
                            iconColor = Color(0xFF1E88E5),
                            title = "DNS Provider",
                            subtitle = secureDnsProvider,
                            onClick = { showDnsDialog = true },
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }

            // ── SECTION: Site Settings ────────────────────────────────────────
            item { PrivacySectionHeader("Site Settings") }

            // All Sites
            item {
                PrivacyClickRow(
                    icon = Icons.Default.Language,
                    iconColor = Color(0xFF1565C0),
                    title = "All Sites",
                    subtitle = "View and manage per-site permissions",
                    onClick = { showSiteListScreen = true }
                )
            }

            item {
                PrivacyClickRow(
                    icon = Icons.Default.LocationOn,
                    iconColor = Color(0xFF8E24AA),
                    title = "Location",
                    subtitle = when (locationPermission) {
                        "Allowed" -> "Always allowed"
                        "Denied" -> "Always denied"
                        else -> "Ask every time"
                    },
                    onClick = { showLocationDialog = true }
                )
            }

            item {
                PrivacyClickRow(
                    icon = Icons.Default.Notifications,
                    iconColor = Color(0xFFE53935),
                    title = "Notifications",
                    subtitle = when (notificationPermission) {
                        "Allowed" -> "Always allowed"
                        "Denied" -> "Always denied"
                        else -> "Ask every time"
                    },
                    onClick = { showNotifDialog = true }
                )
            }

            item {
                PrivacyClickRow(
                    icon = Icons.Default.Mic,
                    iconColor = Color(0xFF00ACC1),
                    title = "Microphone",
                    subtitle = when (microphonePermission) {
                        "Allowed" -> "Always allowed"
                        "Denied" -> "Always denied"
                        else -> "Ask every time"
                    },
                    onClick = { showMicDialog = true }
                )
            }

            item {
                PrivacyClickRow(
                    icon = Icons.Default.OpenInNew,
                    iconColor = Color(0xFF6D4C41),
                    title = "External Apps",
                    subtitle = when (externalAppsPolicy) {
                        "Always" -> "Always allow"
                        "Never" -> "Never allow"
                        else -> "Ask every time"
                    },
                    onClick = { showExternalAppsDialog = true }
                )
            }

            item {
                PrivacyClickRow(
                    icon = Icons.Default.Lock,
                    iconColor = Color(0xFF546E7A),
                    title = "Protected Content",
                    subtitle = protectedContentPolicy,
                    onClick = { showProtectedContentDialog = true }
                )
            }

            item {
                PrivacyToggleRow(
                    icon = Icons.Default.Download,
                    iconColor = Color(0xFF00897B),
                    title = "Automatic Downloads",
                    subtitle = if (isAutoDownload) "Files download without asking" else "Ask before downloading",
                    checked = isAutoDownload,
                    onCheckedChange = { viewModel.isAutoDownload.value = it }
                )
            }

            item {
                PrivacyClickRow(
                    icon = Icons.Default.AccountBalanceWallet,
                    iconColor = Color(0xFFF9A825),
                    title = "Crypto Wallet",
                    subtitle = when (cryptoWalletPolicy) {
                        "Enabled" -> "Always enabled"
                        "Disabled" -> "Always disabled"
                        else -> "Ask every time"
                    },
                    onClick = { showCryptoWalletDialog = true }
                )
            }

            // ── SECTION: Clear Data on Exit ───────────────────────────────────
            item { PrivacySectionHeader("Clear Data on Exit") }

            item {
                PrivacyToggleRow(
                    icon = Icons.Default.DeleteForever,
                    iconColor = Color(0xFFE53935),
                    title = "Clear Data on Exit",
                    subtitle = "Automatically clear selected data when app closes",
                    checked = clearDataOnExit,
                    onCheckedChange = { viewModel.clearDataOnExit.value = it }
                )
            }

            // Show confirmation toggle (only shown when clearDataOnExit is on)
            item {
                AnimatedVisibility(
                    visible = clearDataOnExit,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    PrivacyToggleRow(
                        icon = Icons.Default.QuestionAnswer,
                        iconColor = Color(0xFF5C6BC0),
                        title = "Show Confirmation",
                        subtitle = if (showClearDataConfirmation) "Confirm before clearing data" else "Clear silently on exit",
                        checked = showClearDataConfirmation,
                        onCheckedChange = { viewModel.showClearDataConfirmation.value = it },
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            item {
                AnimatedVisibility(
                    visible = clearDataOnExit,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(8.dp)
                    ) {
                        ClearDataCheckRow("Browsing History", clearHistoryOnExit) { viewModel.clearHistoryOnExit.value = it }
                        ClearDataCheckRow("Open Tabs", clearTabsOnExit) { viewModel.clearTabsOnExit.value = it }
                        ClearDataCheckRow("Recent Searches", clearSearchOnExit) { viewModel.clearSearchOnExit.value = it }
                        ClearDataCheckRow("Cookies & Site Data", clearCookiesOnExit) { viewModel.clearCookiesOnExit.value = it }

                        // Advanced toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showClearAdvanced = !showClearAdvanced }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Advanced",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                if (showClearAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        AnimatedVisibility(visible = showClearAdvanced) {
                            Column {
                                ClearDataCheckRow("Cached Images & Files", clearCacheOnExit) { viewModel.clearCacheOnExit.value = it }
                                ClearDataCheckRow("Autofill Form Data", clearAutofillOnExit) { viewModel.clearAutofillOnExit.value = it }
                                ClearDataCheckRow("Download List", clearDownloadsOnExit) { viewModel.clearDownloadsOnExit.value = it }
                            }
                        }
                    }
                }
            }

            // ── SECTION: Autofill ─────────────────────────────────────────────
            item { PrivacySectionHeader("Autofill") }

            item {
                PrivacyToggleRow(
                    icon = Icons.Default.AutoAwesome,
                    iconColor = Color(0xFF5C6BC0),
                    title = "Autofill",
                    subtitle = if (isAutofillEnabled) "Automatically fill forms" else "Autofill disabled",
                    checked = isAutofillEnabled,
                    onCheckedChange = { viewModel.isAutofillEnabled.value = it }
                )
            }

            item {
                AnimatedVisibility(
                    visible = isAutofillEnabled,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(8.dp)
                    ) {
                        ClearDataCheckRow("Addresses & More", isAutofillAddress) { viewModel.isAutofillAddress.value = it }
                        ClearDataCheckRow("Payment Cards", isAutofillCards) { viewModel.isAutofillCards.value = it }
                        ClearDataCheckRow("Contact Info", isAutofillContacts) { viewModel.isAutofillContacts.value = it }
                    }
                }
            }

            // ── SECTION: Web3 Network ─────────────────────────────────────────
            item { PrivacySectionHeader("Web3 Network") }

            item {
                PrivacyToggleRow(
                    icon = Icons.Default.Hub,
                    iconColor = Color(0xFF9C27B0),
                    title = "Web3 / dApp Support",
                    subtitle = if (isWeb3Enabled) "Connected to $web3Network" else "Web3 injection disabled",
                    checked = isWeb3Enabled,
                    onCheckedChange = { viewModel.isWeb3Enabled.value = it }
                )
            }

            item {
                AnimatedVisibility(
                    visible = isWeb3Enabled,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    PrivacyClickRow(
                        icon = Icons.Default.AccountTree,
                        iconColor = Color(0xFF9C27B0),
                        title = "Network",
                        subtitle = web3Network,
                        onClick = { showWeb3Dialog = true },
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }
    }

    // ── OVERLAY SCREENS ───────────────────────────────────────────────────────

    if (showBlockListScreen) {
        BrowserBlockListScreen(onBack = { showBlockListScreen = false })
    }

    if (showSiteListScreen) {
        BrowserSiteListScreen(
            viewModel = viewModel,
            onBack = { showSiteListScreen = false }
        )
    }

    // ── DIALOGS ──────────────────────────────────────────────────────────────

    if (showCookieDialog) {
        PrivacyRadioDialog(
            title = "Cookie Settings",
            options = listOf(
                "All" to "Accept all cookies",
                "NoThirdParty" to "Block third-party cookies (Recommended)",
                "None" to "Block all cookies"
            ),
            selected = cookieMode,
            onSelect = { viewModel.cookieMode.value = it },
            onDismiss = { showCookieDialog = false }
        )
    }

    if (showDnsDialog) {
        PrivacyRadioDialog(
            title = "Secure DNS Provider",
            options = listOf(
                "Cloudflare" to "1.1.1.1 (Cloudflare) — Fast & privacy focused",
                "Google" to "8.8.8.8 (Google) — Reliable",
                "AdGuard" to "AdGuard DNS — Blocks ads at DNS level",
                "NextDNS" to "NextDNS — Customizable"
            ),
            selected = secureDnsProvider,
            onSelect = { viewModel.secureDnsProvider.value = it },
            onDismiss = { showDnsDialog = false }
        )
    }

    if (showLocationDialog) {
        PrivacyRadioDialog(
            title = "Location Access",
            options = listOf("Ask" to "Ask every time", "Allowed" to "Allowed", "Denied" to "Denied"),
            selected = locationPermission,
            onSelect = { viewModel.locationPermission.value = it },
            onDismiss = { showLocationDialog = false }
        )
    }

    if (showNotifDialog) {
        PrivacyRadioDialog(
            title = "Notifications",
            options = listOf("Ask" to "Ask every time", "Allowed" to "Allowed", "Denied" to "Denied"),
            selected = notificationPermission,
            onSelect = { viewModel.notificationPermission.value = it },
            onDismiss = { showNotifDialog = false }
        )
    }

    if (showMicDialog) {
        PrivacyRadioDialog(
            title = "Microphone Access",
            options = listOf("Ask" to "Ask every time", "Allowed" to "Allowed", "Denied" to "Denied"),
            selected = microphonePermission,
            onSelect = { viewModel.microphonePermission.value = it },
            onDismiss = { showMicDialog = false }
        )
    }

    if (showExternalAppsDialog) {
        PrivacyRadioDialog(
            title = "External Apps",
            options = listOf("Ask" to "Ask every time", "Always" to "Always allow", "Never" to "Never allow"),
            selected = externalAppsPolicy,
            onSelect = { viewModel.externalAppsPolicy.value = it },
            onDismiss = { showExternalAppsDialog = false }
        )
    }

    if (showProtectedContentDialog) {
        PrivacyRadioDialog(
            title = "Protected Content",
            options = listOf("Ask" to "Ask every time", "Allowed" to "Allowed", "Denied" to "Denied"),
            selected = protectedContentPolicy,
            onSelect = { viewModel.protectedContentPolicy.value = it },
            onDismiss = { showProtectedContentDialog = false }
        )
    }

    if (showCryptoWalletDialog) {
        PrivacyRadioDialog(
            title = "Crypto Wallet",
            options = listOf("Ask" to "Ask every time", "Enabled" to "Always enabled", "Disabled" to "Always disabled"),
            selected = cryptoWalletPolicy,
            onSelect = { viewModel.cryptoWalletPolicy.value = it },
            onDismiss = { showCryptoWalletDialog = false }
        )
    }

    if (showWeb3Dialog) {
        PrivacyRadioDialog(
            title = "Web3 Network",
            options = listOf(
                "Ethereum Mainnet" to "Ethereum Mainnet (ChainID: 1)",
                "Polygon" to "Polygon (MATIC) (ChainID: 137)",
                "BSC" to "Binance Smart Chain (ChainID: 56)",
                "Arbitrum" to "Arbitrum One (ChainID: 42161)",
                "Optimism" to "Optimism (ChainID: 10)",
                "Avalanche" to "Avalanche C-Chain (ChainID: 43114)"
            ),
            selected = web3Network,
            onSelect = { viewModel.web3Network.value = it },
            onDismiss = { showWeb3Dialog = false }
        )
    }
}

// ──────────────────────────── Shared UI Components ───────────────────────────


@Composable
private fun PrivacySectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.2.sp
        ),
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun PrivacyToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
private fun PrivacyClickRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ClearDataCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(36.dp)
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PrivacyRadioDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value); onDismiss() }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == value, onClick = { onSelect(value); onDismiss() })
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
