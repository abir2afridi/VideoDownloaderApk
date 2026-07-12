package com.example.ui.screens

import android.widget.Toast
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
import com.example.data.download.MediaUtils
import com.example.ui.components.VideoPlayerDialog
import com.example.ui.viewmodel.MainViewModel

import androidx.compose.foundation.BorderStroke
import com.example.ui.components.TabHeader
import com.example.ui.components.DownloadHealthIndicators

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultTab(viewModel: MainViewModel) {
    val context = LocalContext.current
    val isLocked by viewModel.isVaultLocked.collectAsState()
    val isConfigured = viewModel.isVaultConfigured()

    var pinInput by remember { mutableStateOf("") }
    var hintInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }

    var activePlayingFilePath by remember { mutableStateOf<String?>(null) }

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
                            IconButton(
                                onClick = { viewModel.lockVault() },
                                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = "Lock Vault", tint = Color.White)
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.EnhancedEncryption,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Secure Private Vault",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Set up a secret PIN to protect downloaded videos. Vault downloads are completely hidden from standard device galleries.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { if (it.length <= 4) pinInput = it },
                            label = { Text("Enter 4-Digit Security PIN") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag("setup_pin_field")
                        )

                        OutlinedTextField(
                            value = confirmPinInput,
                            onValueChange = { if (it.length <= 4) confirmPinInput = it },
                            label = { Text("Confirm 4-Digit Security PIN") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag("confirm_pin_field")
                        )

                        OutlinedTextField(
                            value = hintInput,
                            onValueChange = { hintInput = it },
                            label = { Text("Enter PIN Recovery Hint") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag("pin_hint_field")
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (pinInput.length != 4) {
                                    Toast.makeText(context, "PIN must be exactly 4 digits", Toast.LENGTH_SHORT).show()
                                } else if (pinInput != confirmPinInput) {
                                    Toast.makeText(context, "PINs do not match", Toast.LENGTH_SHORT).show()
                                } else if (hintInput.isBlank()) {
                                    Toast.makeText(context, "Please enter a PIN hint for safety", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.setVaultPin(pinInput, hintInput)
                                    Toast.makeText(context, "Vault Successfully Configured!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("setup_vault_button")
                        ) {
                            Text("Initialize Secure Vault")
                        }
                    }
                }

                isLocked -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Vault Locked",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Enter your secret PIN code to decrypt private files.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { if (it.length <= 4) pinInput = it },
                            label = { Text("Enter PIN") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .testTag("unlock_pin_field")
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (viewModel.unlockVault(pinInput)) {
                                    pinInput = ""
                                    Toast.makeText(context, "Vault Decrypted!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Incorrect PIN. Try again.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(52.dp)
                                .testTag("unlock_vault_button")
                        ) {
                            Text("Unlock Partition")
                        }

                        val hint = viewModel.pinHint.collectAsState().value
                        if (!hint.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "PIN Hint: $hint",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }

                else -> {
                    val privateList by viewModel.privateDownloads.collectAsState()

                    if (privateList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(72.dp),
                                    tint = Color.LightGray
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Vault is currently empty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "To download files here, use the browser tab, scan a media page, and download with 'Hidden Vault' active.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
                                )
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "Your Secure Downloads (${privateList.size} files)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 100.dp)
                            ) {
                                items(privateList) { privateItem ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (privateItem.status == "COMPLETED") {
                                                    activePlayingFilePath = privateItem.filepath
                                                } else {
                                                    Toast.makeText(context, "Download in progress. Please wait.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            .testTag("vault_item_${privateItem.id}"),
                                        shape = RoundedCornerShape(24.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (privateItem.category == "Audio") Icons.Default.Audiotrack else Icons.Default.Movie,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = privateItem.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${privateItem.filename}  •  ${MediaUtils.formatBytes(privateItem.totalBytes)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.Gray,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                DownloadHealthIndicators(integrityStatus = privateItem.integrityStatus, connectionHealth = privateItem.connectionHealth)
                                            }

                                            IconButton(onClick = { viewModel.deleteDownload(privateItem.id) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                            }
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
    }
}
