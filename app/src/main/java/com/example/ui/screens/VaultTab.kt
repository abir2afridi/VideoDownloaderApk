package com.example.ui.screens

import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.example.data.database.DownloadEntity
import com.example.data.download.MediaUtils
import com.example.ui.components.VideoPlayerDialog
import com.example.ui.viewmodel.MainViewModel

import androidx.compose.foundation.BorderStroke
import com.example.ui.components.TabHeader
import com.example.ui.components.DownloadHealthIndicators
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.set

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultTab(viewModel: MainViewModel) {
    val context = LocalContext.current
    val isLocked by viewModel.isVaultLocked.collectAsState()
    val isConfigured = viewModel.isVaultConfigured()
    val isBiometricAvailable by viewModel.isBiometricAvailable.collectAsState()

    var pinInput by remember { mutableStateOf("") }
    var hintInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }

    var activePlayingFilePath by remember { mutableStateOf<String?>(null) }
    var showForgotPinDialog by remember { mutableStateOf(false) }

    // Batch selection
    var selectedIds by remember { mutableStateOf(setOf<Int>()) }
    var isSelectMode by remember { mutableStateOf(false) }

    // File info bottom sheet
    var infoSheetItem by remember { mutableStateOf<DownloadEntity?>(null) }

    // Import launcher
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            val displayName = cursor?.use {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && it.moveToFirst()) it.getString(nameIndex) else ""
            } ?: ""
            viewModel.importToVault(context, uri, displayName)
            Toast.makeText(context, "Importing file to vault...", Toast.LENGTH_SHORT).show()
        }
    }

    // Biometric unlock (lazy - only created on demand)
    var biometricUnlockAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(isBiometricAvailable) {
        if (isBiometricAvailable) {
            try {
                val activity = context as? FragmentActivity
                if (activity != null) {
                    val prompt = BiometricPrompt(
                        activity,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                viewModel.unlockVault(viewModel.vaultPin.value ?: "")
                            }
                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                Toast.makeText(context, "Biometric error: $errString", Toast.LENGTH_SHORT).show()
                            }
                            override fun onAuthenticationFailed() {
                                Toast.makeText(context, "Biometric not recognized", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Unlock Private Vault")
                        .setSubtitle("Use your fingerprint to access secure files")
                        .setAllowedAuthenticators(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.BIOMETRIC_WEAK
                        )
                        .build()
                    biometricUnlockAction = { prompt.authenticate(promptInfo) }
                }
            } catch (_: Exception) { }
        }
    }

    fun clearSelection() {
        selectedIds = emptySet()
        isSelectMode = false
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                TabHeader(
                    category = "Security",
                    title = "Private Vault",
                    actionContent = {
                        if (!isLocked) {
                            if (isSelectMode) {
                                IconButton(onClick = { clearSelection() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel Selection")
                                }
                            } else {
                                Row {
                                    IconButton(
                                        onClick = {
                                            importLauncher.launch(arrayOf("*/*"))
                                        },
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape)
                                    ) {
                                        Icon(Icons.Default.FileUpload, contentDescription = "Import File", tint = Color.White)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { viewModel.lockVault(); clearSelection() },
                                        modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape)
                                    ) {
                                        Icon(Icons.Default.Lock, contentDescription = "Lock Vault", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            when {
                !isConfigured -> {
                    SetupVaultContent(
                        pinInput = pinInput,
                        onPinChange = { if (it.length <= 4) pinInput = it },
                        confirmPinInput = confirmPinInput,
                        onConfirmPinChange = { if (it.length <= 4) confirmPinInput = it },
                        hintInput = hintInput,
                        onHintChange = { hintInput = it },
                        onSetup = {
                            if (pinInput.length != 4) {
                                Toast.makeText(context, "PIN must be exactly 4 digits", Toast.LENGTH_SHORT).show()
                            } else if (pinInput != confirmPinInput) {
                                Toast.makeText(context, "PINs do not match", Toast.LENGTH_SHORT).show()
                            } else if (hintInput.isBlank()) {
                                Toast.makeText(context, "Please enter a PIN hint for safety", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.setVaultPin(pinInput, hintInput)
                                Toast.makeText(context, "Vault Successfully Configured!", Toast.LENGTH_SHORT).show()
                                pinInput = ""; confirmPinInput = ""; hintInput = ""
                            }
                        }
                    )
                }

                isLocked -> {
                    LockedVaultContent(
                        pinInput = pinInput,
                        onPinChange = { if (it.length <= 4) pinInput = it },
                        onUnlock = {
                            if (viewModel.unlockVault(pinInput)) {
                                pinInput = ""
                                Toast.makeText(context, "Vault Decrypted!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Incorrect PIN. Try again.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        pinHint = viewModel.pinHint.collectAsState().value,
                        isBiometricAvailable = isBiometricAvailable,
                        onBiometricUnlock = { biometricUnlockAction?.invoke() },
                        onForgotPin = { showForgotPinDialog = true }
                    )
                }

                else -> {
                    val privateList by viewModel.privateDownloads.collectAsState()

                    if (privateList.isEmpty()) {
                        EmptyVaultContent()
                    } else {
                        val toggleSelect: (Int) -> Unit = { id ->
                        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                        isSelectMode = selectedIds.isNotEmpty()
                    }
                    VaultListContent(
                            items = privateList,
                            selectedIds = selectedIds,
                            isSelectMode = isSelectMode,
                            onToggleSelect = toggleSelect,
                            onLongPress = { id ->
                                if (!isSelectMode) {
                                    selectedIds = setOf(id)
                                    isSelectMode = true
                                }
                            },
                            onItemClick = { item ->
                                if (isSelectMode) {
                                    toggleSelect(item.id)
                                } else if (item.status == "COMPLETED") {
                                    activePlayingFilePath = item.filepath
                                } else {
                                    Toast.makeText(context, "Download in progress. Please wait.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onPlay = { path -> activePlayingFilePath = path },
                            onExport = { id -> viewModel.exportFromVault(id); Toast.makeText(context, "Exporting...", Toast.LENGTH_SHORT).show() },
                            onDelete = { id -> viewModel.deleteVaultItem(id) },
                            onInfo = { item -> infoSheetItem = item }
                        )

                        // Batch action bar
                        AnimatedVisibility(
                            visible = isSelectMode && selectedIds.isNotEmpty(),
                            enter = slideInVertically { it },
                            exit = slideOutVertically { it },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                tonalElevation = 4.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${selectedIds.size} selected",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Row {
                                        TextButton(onClick = { clearSelection() }) {
                                            Text("Cancel", color = MaterialTheme.colorScheme.onErrorContainer)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                viewModel.deleteMultipleVaultItems(selectedIds.toList())
                                                clearSelection()
                                                Toast.makeText(context, "Deleting ${selectedIds.size} files...", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Delete")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        activePlayingFilePath?.let { path ->
            VideoPlayerDialog(filePath = path, onDismiss = { activePlayingFilePath = null })
        }

        infoSheetItem?.let { item ->
            FileInfoBottomSheet(
                item = item,
                onDismiss = { infoSheetItem = null }
            )
        }

        if (showForgotPinDialog) {
            ForgotPinDialog(
                hint = viewModel.pinHint.collectAsState().value,
                onDismiss = { showForgotPinDialog = false },
                onConfirmReset = {
                    viewModel.resetVault()
                    showForgotPinDialog = false
                    pinInput = ""; confirmPinInput = ""; hintInput = ""
                    Toast.makeText(context, "Vault has been reset. Set a new PIN.", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

// ─── Setup Screen ─────────────────────────────────────────────────────────────

@Composable
private fun SetupVaultContent(
    pinInput: String,
    onPinChange: (String) -> Unit,
    confirmPinInput: String,
    onConfirmPinChange: (String) -> Unit,
    hintInput: String,
    onHintChange: (String) -> Unit,
    onSetup: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.EnhancedEncryption, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Secure Private Vault", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Set up a secret PIN to protect downloaded videos. Vault downloads are completely hidden from standard device galleries.",
            style = MaterialTheme.typography.bodyMedium, color = Color.Gray, textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        OutlinedTextField(
            value = pinInput, onValueChange = onPinChange,
            label = { Text("Enter 4-Digit Security PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(), singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("setup_pin_field")
        )
        OutlinedTextField(
            value = confirmPinInput, onValueChange = onConfirmPinChange,
            label = { Text("Confirm 4-Digit Security PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(), singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("confirm_pin_field")
        )
        OutlinedTextField(
            value = hintInput, onValueChange = onHintChange,
            label = { Text("Enter PIN Recovery Hint") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("pin_hint_field")
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onSetup, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("setup_vault_button")) {
            Text("Initialize Secure Vault")
        }
    }
}

// ─── Locked Screen ────────────────────────────────────────────────────────────

@Composable
private fun LockedVaultContent(
    pinInput: String,
    onPinChange: (String) -> Unit,
    onUnlock: () -> Unit,
    pinHint: String?,
    isBiometricAvailable: Boolean,
    onBiometricUnlock: () -> Unit,
    onForgotPin: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Vault Locked", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Enter your secret PIN code to decrypt private files.",
            style = MaterialTheme.typography.bodyMedium, color = Color.Gray, textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = pinInput, onValueChange = onPinChange,
            label = { Text("Enter PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(), singleLine = true,
            modifier = Modifier.fillMaxWidth(0.8f).testTag("unlock_pin_field")
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onUnlock,
            modifier = Modifier.fillMaxWidth(0.8f).height(52.dp).testTag("unlock_vault_button")
        ) {
            Text("Unlock Partition")
        }

        if (isBiometricAvailable) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onBiometricUnlock,
                modifier = Modifier.fillMaxWidth(0.8f).height(52.dp)
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Unlock with Biometric")
            }
        }

        if (!pinHint.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("PIN Hint: $pinHint", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onForgotPin) {
            Text("Forgot PIN?", color = MaterialTheme.colorScheme.error)
        }
    }
}

// ─── Empty Screen ─────────────────────────────────────────────────────────────

@Composable
private fun EmptyVaultContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(72.dp), tint = Color.LightGray)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Vault is currently empty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Tap the upload icon above to import files, or use the browser to download directly to vault.",
                style = MaterialTheme.typography.bodyMedium, color = Color.Gray, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
            )
        }
    }
}

// ─── Vault List ───────────────────────────────────────────────────────────────

@Composable
private fun VaultListContent(
    items: List<DownloadEntity>,
    selectedIds: Set<Int>,
    isSelectMode: Boolean,
    onToggleSelect: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
    onItemClick: (DownloadEntity) -> Unit,
    onPlay: (String) -> Unit,
    onExport: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onInfo: (DownloadEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Your Secure Downloads (${items.size} files)",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            items(items, key = { it.id }) { item ->
                val isSelected = item.id in selectedIds
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onItemClick(item) }
                        .testTag("vault_item_${item.id}"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSelectMode) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggleSelect(item.id) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }

                        Icon(
                            imageVector = when (item.category) {
                                "Audio" -> Icons.Default.Audiotrack
                                "Images" -> Icons.Default.Image
                                else -> Icons.Default.Movie
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${item.filename}  •  ${MediaUtils.formatBytes(item.totalBytes)}",
                                style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            DownloadHealthIndicators(
                                integrityStatus = item.integrityStatus,
                                connectionHealth = item.connectionHealth
                            )
                        }

                        if (!isSelectMode) {
                            // Actions
                            IconButton(onClick = { onInfo(item) }) {
                                Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.primary)
                            }
                            if (item.category in listOf("Video", "Audio") && item.status == "COMPLETED") {
                                IconButton(onClick = { onPlay(item.filepath) }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Green)
                                }
                            }
                            IconButton(onClick = { onExport(item.id) }) {
                                Icon(Icons.Default.Share, contentDescription = "Export", tint = Color(0xFF4CAF50))
                            }
                            IconButton(onClick = { onDelete(item.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── File Info Bottom Sheet ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileInfoBottomSheet(
    item: DownloadEntity,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("File Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            InfoRow("Title", item.title)
            InfoRow("Filename", item.filename)
            InfoRow("Type", item.category)
            InfoRow("MIME", item.mimeType)
            InfoRow("Size", MediaUtils.formatBytes(item.totalBytes))
            InfoRow("Status", item.status)
            InfoRow("Quality", item.quality ?: "Auto")
            InfoRow("Downloaded", formatTimestamp(item.timestamp))
            InfoRow("Integrity", item.integrityStatus ?: "Unknown")
            if (item.filepath.isNotEmpty()) {
                InfoRow("Location", item.filepath)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.35f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.weight(0.65f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// ─── Forgot PIN Dialog ────────────────────────────────────────────────────────

@Composable
private fun ForgotPinDialog(
    hint: String?,
    onDismiss: () -> Unit,
    onConfirmReset: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Forgot PIN?") },
        text = {
            Column {
                if (!hint.isNullOrBlank()) {
                    Text("Your PIN hint: $hint")
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text("Resetting will erase all saved files in the vault. This cannot be undone.")
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmReset,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Reset Vault") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatTimestamp(timestamp: Long): String {
    return try {
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
    } catch (_: Exception) { "Unknown" }
}
