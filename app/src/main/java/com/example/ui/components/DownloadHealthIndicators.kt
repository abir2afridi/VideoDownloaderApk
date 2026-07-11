package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DownloadHealthIndicators(
    integrityStatus: String?,
    connectionHealth: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Integrity Badge Details
        val (integrityColor, integrityText, integrityIcon) = when (integrityStatus) {
            "OK" -> Triple(Color(0xFF2E7D32), "Integrity: OK", Icons.Default.CheckCircle)
            "CORRUPTED" -> Triple(Color(0xFFC62828), "Corrupted", Icons.Default.Warning)
            "MISSING" -> Triple(Color(0xFFEF6C00), "Missing", Icons.Default.Warning)
            else -> Triple(Color(0xFF757575), "Integrity: Pending", Icons.Default.Refresh)
        }

        // Connection Badge Details
        val (connColor, connText, connIcon) = when (connectionHealth) {
            "EXCELLENT" -> Triple(Color(0xFF1B5E20), "Conn: Excellent", Icons.Default.Wifi)
            "GOOD" -> Triple(Color(0xFF0288D1), "Conn: Good", Icons.Default.Wifi)
            "POOR" -> Triple(Color(0xFFE65100), "Conn: Poor", Icons.Default.Wifi)
            "UNREACHABLE" -> Triple(Color(0xFFB71C1C), "Unreachable", Icons.Default.WifiOff)
            else -> Triple(Color(0xFF757575), "Conn: Pending", Icons.Default.Refresh)
        }

        // Integrity Badge
        Surface(
            color = integrityColor.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, integrityColor.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = integrityIcon,
                    contentDescription = null,
                    tint = integrityColor,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = integrityText,
                    style = MaterialTheme.typography.labelSmall,
                    color = integrityColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Connection Badge
        Surface(
            color = connColor.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, connColor.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = connIcon,
                    contentDescription = null,
                    tint = connColor,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = connText,
                    style = MaterialTheme.typography.labelSmall,
                    color = connColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
