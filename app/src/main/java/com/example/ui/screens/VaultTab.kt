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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.database.DownloadEntity
import com.example.data.download.MediaUtils
import com.example.ui.components.VideoPlayerDialog
import com.example.ui.components.PatternLockView
import com.example.ui.viewmodel.MainViewModel

import androidx.compose.foundation.BorderStroke
import com.example.ui.components.TabHeader
import com.example.ui.components.DownloadHealthIndicators
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.set

private enum class VaultScreen { SETUP, LOCKED, HOME, CATEGORY_VIEW }

private enum class LockType { PIN, PATTERN }

private data class CategoryInfo(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val color: Color
)

private val VAULT_CATEGORIES = listOf(
    CategoryInfo("Images", "Images", Icons.Default.Image, Color(0xFF4CAF50)),
    CategoryInfo("Video", "Videos", Icons.Default.VideoLibrary, Color(0xFF2196F3)),
    CategoryInfo("Audio", "Audio", Icons.Default.Audiotrack, Color(0xFF9C27B0)),
    CategoryInfo("Other", "Others", Icons.Default.Description, Color(0xFFFF9800)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultTab(viewModel: MainViewModel) {
    val context = LocalContext.current
    val isLocked by viewModel.isVaultLocked.collectAsState()
    val isConfigured = viewModel.isVaultConfigured()
    val isBiometricAvailable by viewModel.isBiometricAvailable.collectAsState()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val privateList by viewModel.privateDownloads.collectAsState()
    val storedLockType by viewModel.vaultLockType.collectAsState()

    var currentScreen by remember { mutableStateOf(VaultScreen.LOCKED) }
    var selectedCategory by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var hintInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var useBiometric by remember { mutableStateOf(false) }
    var showForgotPinDialog by remember { mutableStateOf(false) }
    var viewingImage by remember { mutableStateOf<String?>(null) }
    var activePlayingFilePath by remember { mutableStateOf<String?>(null) }

    // Pattern state
    var selectedLockType by remember { mutableStateOf(LockType.PIN) }
    var patternInput by remember { mutableStateOf(emptyList<Int>()) }
    var confirmPatternInput by remember { mutableStateOf(emptyList<Int>()) }
    var patternStep by remember { mutableIntStateOf(1) }
    var patternError by remember { mutableStateOf(false) }

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

    // Navigate to correct screen based on state
    LaunchedEffect(isConfigured, isLocked) {
        currentScreen = when {
            !isConfigured -> VaultScreen.SETUP
            isLocked -> VaultScreen.LOCKED
            else -> VaultScreen.HOME
        }
    }

    fun authenticateWithBiometric() {
        try {
            val activity = context as? FragmentActivity
                ?: run {
                    Toast.makeText(context, "Biometric not supported", Toast.LENGTH_SHORT).show()
                    return
                }
            val pin = viewModel.vaultPin.value ?: return
            val executor = ContextCompat.getMainExecutor(context)

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.unlockVault(pin)
                    pinInput = ""
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                        Toast.makeText(context, errString, Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onAuthenticationFailed() {
                    Toast.makeText(context, "Biometric not recognized", Toast.LENGTH_SHORT).show()
                }
            }

            val biometricPrompt = BiometricPrompt(activity, executor, callback)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Private Vault")
                .setSubtitle("Use your fingerprint to access secure files")
                .setNegativeButtonText("Cancel")
                .build()
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Toast.makeText(context, "Biometric error: ${e.message}", Toast.LENGTH_SHORT).show()
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
                    title = when (currentScreen) {
                        VaultScreen.HOME -> "Private Vault"
                        VaultScreen.CATEGORY_VIEW -> selectedCategory
                        else -> "Private Vault"
                    },
                    actionContent = {
                        if (!isLocked && currentScreen == VaultScreen.HOME) {
                            if (isSelectMode) {
                                IconButton(onClick = { clearSelection() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel Selection")
                                }
                            } else {
                                Row {
                                    IconButton(
                                        onClick = { importLauncher.launch(arrayOf("*/*")) },
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
                        if (currentScreen == VaultScreen.CATEGORY_VIEW && !isSelectMode) {
                            IconButton(onClick = { currentScreen = VaultScreen.HOME }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            when (currentScreen) {
                VaultScreen.SETUP -> {
                    SetupVaultContent(
                        lockType = selectedLockType,
                        onLockTypeChange = { selectedLockType = it },
                        pinInput = pinInput,
                        onPinChange = { if (it.length <= 4) pinInput = it },
                        confirmPinInput = confirmPinInput,
                        onConfirmPinChange = { if (it.length <= 4) confirmPinInput = it },
                        patternInput = patternInput,
                        onPatternComplete = { pattern ->
                            if (patternStep == 1) {
                                patternInput = pattern
                                patternStep = 2
                            } else {
                                confirmPatternInput = pattern
                            }
                        },
                        patternStep = patternStep,
                        patternError = patternError,
                        hintInput = hintInput,
                        onHintChange = { hintInput = it },
                        useBiometric = useBiometric,
                        onUseBiometricChange = { useBiometric = it },
                        isBiometricAvailable = isBiometricAvailable,
                        onSetup = {
                            if (selectedLockType == LockType.PIN) {
                                if (pinInput.length != 4) {
                                    Toast.makeText(context, "PIN must be exactly 4 digits", Toast.LENGTH_SHORT).show()
                                } else if (pinInput != confirmPinInput) {
                                    Toast.makeText(context, "PINs do not match", Toast.LENGTH_SHORT).show()
                                } else if (hintInput.isBlank()) {
                                    Toast.makeText(context, "Please enter a PIN hint for safety", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.setVaultPin(pinInput, hintInput, useBiometric)
                                    Toast.makeText(context, "Vault configured!", Toast.LENGTH_SHORT).show()
                                    pinInput = ""; confirmPinInput = ""; hintInput = ""
                                }
                            } else {
                                if (patternInput.size < 4) {
                                    Toast.makeText(context, "Pattern must connect at least 4 dots", Toast.LENGTH_SHORT).show()
                                } else if (patternInput != confirmPatternInput) {
                                    patternError = true
                                    Toast.makeText(context, "Patterns do not match. Try again.", Toast.LENGTH_SHORT).show()
                                    patternInput = emptyList()
                                    confirmPatternInput = emptyList()
                                    patternStep = 1
                                } else if (hintInput.isBlank()) {
                                    Toast.makeText(context, "Please enter a hint for safety", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.setVaultPattern(patternInput, hintInput, useBiometric)
                                    Toast.makeText(context, "Vault configured!", Toast.LENGTH_SHORT).show()
                                    patternInput = emptyList()
                                    confirmPatternInput = emptyList()
                                    hintInput = ""
                                }
                            }
                        }
                    )
                }

                VaultScreen.LOCKED -> {
                    LockedVaultContent(
                        lockType = storedLockType ?: "pin",
                        pinInput = pinInput,
                        onPinChange = { if (it.length <= 4) pinInput = it },
                        onUnlock = {
                            if (viewModel.unlockVault(pinInput)) {
                                pinInput = ""
                            } else {
                                Toast.makeText(context, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onPatternComplete = { pattern ->
                            if (viewModel.unlockVaultWithPattern(pattern)) {
                                Toast.makeText(context, "Vault Decrypted!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Incorrect pattern", Toast.LENGTH_SHORT).show()
                            }
                        },
                        patternError = patternError,
                        pinHint = viewModel.pinHint.collectAsState().value,
                        isBiometricAvailable = isBiometricAvailable && isBiometricEnabled,
                        onBiometricUnlock = { authenticateWithBiometric() },
                        onForgotPin = { showForgotPinDialog = true }
                    )
                }

                VaultScreen.HOME -> {
                    if (privateList.isEmpty()) {
                        EmptyVaultContent(onImport = { importLauncher.launch(arrayOf("*/*")) })
                    } else {
                        VaultHomeContent(
                            items = privateList,
                            onCategoryClick = { category ->
                                selectedCategory = category
                                currentScreen = VaultScreen.CATEGORY_VIEW
                                clearSelection()
                            },
                            onImport = { importLauncher.launch(arrayOf("*/*")) }
                        )
                    }
                }

                VaultScreen.CATEGORY_VIEW -> {
                    val categoryItems = privateList.filter { it.category == selectedCategory }
                    VaultCategoryContent(
                        category = selectedCategory,
                        items = categoryItems,
                        selectedIds = selectedIds,
                        isSelectMode = isSelectMode,
                        onToggleSelect = { id ->
                            selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                            isSelectMode = selectedIds.isNotEmpty()
                        },
                        onLongPress = { id ->
                            if (!isSelectMode) {
                                selectedIds = setOf(id)
                                isSelectMode = true
                            }
                        },
                        onItemClick = { item ->
                            if (isSelectMode) {
                                val newIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
                                selectedIds = newIds
                                isSelectMode = newIds.isNotEmpty()
                            } else {
                                when (item.category) {
                                    "Images" -> viewingImage = item.filepath
                                    "Video", "Audio" -> {
                                        if (item.status == "COMPLETED") {
                                            activePlayingFilePath = item.filepath
                                        } else {
                                            Toast.makeText(context, "File not ready yet", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    else -> {
                                        // Open file with intent
                                        try {
                                            val file = File(item.filepath)
                                            if (file.exists()) {
                                                val uri = FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    file
                                                )
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                    setDataAndType(uri, item.mimeType)
                                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(intent)
                                            }
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        },
                        onExport = { id ->
                            viewModel.exportFromVault(id)
                            Toast.makeText(context, "Exporting...", Toast.LENGTH_SHORT).show()
                        },
                        onDelete = { id ->
                            viewModel.deleteVaultItem(id)
                            selectedIds = selectedIds - id
                            if (selectedIds.isEmpty()) isSelectMode = false
                        },
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

        // Dialogs & Sheets
        activePlayingFilePath?.let { path ->
            VideoPlayerDialog(filePath = path, onDismiss = { activePlayingFilePath = null })
        }

        infoSheetItem?.let { item ->
            FileInfoBottomSheet(item = item, onDismiss = { infoSheetItem = null })
        }

        viewingImage?.let { imagePath ->
            ImageViewerDialog(
                imagePath = imagePath,
                onDismiss = { viewingImage = null }
            )
        }

        if (showForgotPinDialog) {
            ForgotPinDialog(
                hint = viewModel.pinHint.collectAsState().value,
                lockType = storedLockType ?: "pin",
                onDismiss = { showForgotPinDialog = false },
                onConfirmReset = {
                    viewModel.resetVault()
                    showForgotPinDialog = false
                    pinInput = ""; confirmPinInput = ""; hintInput = ""
                    patternInput = emptyList(); confirmPatternInput = emptyList(); patternStep = 1
                    Toast.makeText(context, "Vault reset. Set a new lock.", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

// ─── Setup Screen ─────────────────────────────────────────────────────────────

@Composable
private fun SetupVaultContent(
    lockType: LockType,
    onLockTypeChange: (LockType) -> Unit,
    pinInput: String,
    onPinChange: (String) -> Unit,
    confirmPinInput: String,
    onConfirmPinChange: (String) -> Unit,
    patternInput: List<Int>,
    onPatternComplete: (List<Int>) -> Unit,
    patternStep: Int,
    patternError: Boolean,
    hintInput: String,
    onHintChange: (String) -> Unit,
    useBiometric: Boolean,
    onUseBiometricChange: (Boolean) -> Unit,
    isBiometricAvailable: Boolean,
    onSetup: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.EnhancedEncryption, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Secure Private Vault", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Set up a secret lock to protect your files. Vault contents are hidden from device galleries.",
            style = MaterialTheme.typography.bodyMedium, color = Color.Gray, textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        // Lock type selector
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = lockType == LockType.PIN,
                onClick = { onLockTypeChange(LockType.PIN) },
                label = { Text("PIN") },
                leadingIcon = { Icon(Icons.Default.Pin, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = lockType == LockType.PATTERN,
                onClick = {
                    onLockTypeChange(LockType.PATTERN)
                    patternInput.toList()
                },
                label = { Text("Pattern") },
                leadingIcon = { Icon(Icons.Default.Pattern, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (lockType == LockType.PIN) {
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
        } else {
            // Pattern input
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (patternStep == 1) "Draw your unlock pattern"
                        else "Draw pattern again to confirm",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PatternLockView(
                        onPatternComplete = onPatternComplete,
                        isError = patternError
                    )
                    if (patternInput.isNotEmpty() && patternStep == 2) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Pattern recorded. Draw again to confirm.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = hintInput, onValueChange = onHintChange,
            label = { Text("Enter Recovery Hint") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("pin_hint_field")
        )

        if (isBiometricAvailable) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Unlock with Fingerprint", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("Optionally use biometric to unlock vault", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Switch(checked = useBiometric, onCheckedChange = onUseBiometricChange)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onSetup, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("setup_vault_button")) {
            Text("Initialize Secure Vault")
        }
    }
}

// ─── Locked Screen ────────────────────────────────────────────────────────────

@Composable
private fun LockedVaultContent(
    lockType: String,
    pinInput: String,
    onPinChange: (String) -> Unit,
    onUnlock: () -> Unit,
    onPatternComplete: (List<Int>) -> Unit,
    patternError: Boolean,
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

        if (lockType == "pattern") {
            Text(
                "Draw your unlock pattern to decrypt files.",
                style = MaterialTheme.typography.bodyMedium, color = Color.Gray, textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            PatternLockView(
                onPatternComplete = onPatternComplete,
                isError = patternError,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
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
                Text("Unlock Vault")
            }
        }

        if (isBiometricAvailable) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onBiometricUnlock,
                modifier = Modifier.fillMaxWidth(0.8f).height(52.dp)
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Unlock with Fingerprint")
            }
        }

        if (!pinHint.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Hint: $pinHint", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onForgotPin) {
            Text("Forgot ${if (lockType == "pattern") "Pattern" else "PIN"}?", color = MaterialTheme.colorScheme.error)
        }
    }
}

// ─── Empty Vault ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyVaultContent(onImport: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(72.dp), tint = Color.LightGray)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Vault is Empty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Tap Import to add files from your device.",
                style = MaterialTheme.typography.bodyMedium, color = Color.Gray, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onImport) {
                Icon(Icons.Default.FileUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import Files")
            }
        }
    }
}

// ─── Vault Home (Categories) ──────────────────────────────────────────────────

@Composable
private fun VaultHomeContent(
    items: List<DownloadEntity>,
    onCategoryClick: (String) -> Unit,
    onImport: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${items.size} files secured",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = onImport,
                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Import", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(VAULT_CATEGORIES) { category ->
                val count = items.count { it.category == category.key }
                CategoryCard(
                    category = category,
                    count = count,
                    onClick = { onCategoryClick(category.key) }
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: CategoryInfo,
    count: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = category.color.copy(alpha = 0.1f)
        ),
        border = BorderStroke(1.dp, category.color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = category.color
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                category.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "$count files",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

// ─── Vault Category View ──────────────────────────────────────────────────────

@Composable
private fun VaultCategoryContent(
    category: String,
    items: List<DownloadEntity>,
    selectedIds: Set<Int>,
    isSelectMode: Boolean,
    onToggleSelect: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
    onItemClick: (DownloadEntity) -> Unit,
    onExport: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onInfo: (DownloadEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "${items.size} files",
            style = MaterialTheme.typography.bodyMedium, color = Color.Gray,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No files in this category", color = Color.Gray)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    val isSelected = item.id in selectedIds
                    VaultFileCard(
                        item = item,
                        isSelected = isSelected,
                        isSelectMode = isSelectMode,
                        onClick = { onItemClick(item) },
                        onLongClick = { onLongPress(item.id) },
                        onInfo = { onInfo(item) },
                        onExport = { onExport(item.id) },
                        onDelete = { onDelete(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultFileCard(
    item: DownloadEntity,
    isSelected: Boolean,
    isSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onInfo: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Thumbnail / Icon
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (item.category == "Images" && File(item.filepath).exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(item.filepath))
                            .crossfade(true)
                            .build(),
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = when (item.category) {
                            "Audio" -> Icons.Default.Audiotrack
                            "Video" -> Icons.Default.VideoLibrary
                            else -> Icons.Default.Description
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                if (isSelectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                item.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                MediaUtils.formatBytes(item.totalBytes),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )

            if (!isSelectMode) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onInfo, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onExport, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "Export", modifier = Modifier.size(16.dp), tint = Color(0xFF4CAF50))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// ─── Image Viewer Dialog ──────────────────────────────────────────────────────

@Composable
private fun ImageViewerDialog(imagePath: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(imagePath))
                    .crossfade(true)
                    .build(),
                contentDescription = "Vault Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .statusBarsPadding()
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
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
    lockType: String,
    onDismiss: () -> Unit,
    onConfirmReset: () -> Unit
) {
    val lockLabel = if (lockType == "pattern") "Pattern" else "PIN"
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Forgot $lockLabel?") },
        text = {
            Column {
                if (!hint.isNullOrBlank()) {
                    Text("Your hint: $hint")
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
