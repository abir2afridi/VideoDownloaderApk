package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.TabHeader

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AboutTab(onBack: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                TabHeader(
                    category = "Information",
                    title = "About",
                    actionContent = {
                        IconButton(
                            onClick = onBack
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // 1. MINIMAL BRANDING
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "NEXLOAD",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 4.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Smart Media Architecture • v1.0",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 2. PHILOSOPHY
            Text(
                text = "A powerful, multi-threaded media download manager designed for maximum performance and deep privacy. Built with an intelligent DOM scanning engine to capture any stream.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )

            // 3. ARCHITECT SECTION (MINIMAL)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionLabel("THE ARCHITECT")
                
                Text(
                    text = "Abir Hasan Siam",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MinimalDetailRow(Icons.Outlined.School, "Independent University of Bangladesh")
                    MinimalDetailRow(Icons.Outlined.LocationOn, "Gazipur, Dhaka, Bangladesh")
                    MinimalDetailRow(Icons.Outlined.Cake, "17 November 2002")
                    MinimalDetailRow(Icons.Outlined.Bloodtype, "Blood Group: B+")
                }

                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    MinimalSocialLink("GitHub", "https://github.com/abir2afridi")
                    MinimalSocialLink("Portfolio", "https://abir2afridi.vercel.app/")
                    MinimalSocialLink("Email", "mailto:abir2afridi@gmail.com")
                }
            }

            // 4. CORE CAPABILITIES (FLAT LIST)
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                SectionLabel("CAPABILITIES")
                
                CapabilityRow(Icons.Default.Speed, "Parallel Multi-Threading", "Splits streams into concurrent segments for boosted speeds.")
                CapabilityRow(Icons.Default.Shield, "Private Partition", "Encrypted file storage with PIN-locked hidden vault.")
                CapabilityRow(Icons.Default.AutoAwesome, "Intelligent Detection", "Advanced DOM scanning to capture any audio or video path.")
            }

            // 5. TECHNOLOGY (TEXT TAGS)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionLabel("TECH STACK")
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val stack = listOf("Kotlin", "Jetpack Compose", "Room DB", "WorkManager", "OkHttp", "MVVM", "Material 3")
                    stack.forEach { tech ->
                        Text(
                            text = tech,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        ),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    )
}

@Composable
private fun MinimalDetailRow(icon: ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MinimalSocialLink(label: String, url: String) {
    val context = LocalContext.current
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }
    )
}

@Composable
private fun CapabilityRow(icon: ImageVector, title: String, desc: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
