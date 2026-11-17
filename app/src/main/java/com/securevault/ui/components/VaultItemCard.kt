package com.securevault.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.securevault.data.local.VaultItem
import com.securevault.utils.formatSize

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultItemCard(file: VaultItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = getVaultFileIcon(file.mimeType),
                contentDescription = null,
                tint = getVaultIconColor(file.mimeType)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(file.originalName, color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "${formatSize(file.fileSize)} • Encrypted",
                    color = Color.Gray, style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun getVaultFileIcon(mimeType: String): ImageVector {
    return when {
        mimeType.startsWith("image/") -> Icons.Default.Image
        mimeType.startsWith("video/") -> Icons.Default.Movie
        mimeType.startsWith("audio/") -> Icons.Default.Audiotrack
        mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
        mimeType.contains("zip") || mimeType.contains("rar") || mimeType.contains("7z") -> Icons.Default.Archive
        mimeType.startsWith("text/") -> Icons.Default.Description
        mimeType.contains("apk") -> Icons.Default.Android
        else -> Icons.Default.InsertDriveFile
    }
}

private fun getVaultIconColor(mimeType: String): Color {
    return when {
        mimeType.startsWith("image/") -> Color(0xFF4CAF50)
        mimeType.startsWith("video/") -> Color(0xFF9C27B0)
        mimeType.startsWith("audio/") -> Color(0xFFE91E63)
        mimeType == "application/pdf" -> Color(0xFFF44336)
        mimeType.contains("zip") || mimeType.contains("rar") -> Color(0xFFFF9800)
        mimeType.contains("apk") -> Color(0xFF2196F3)
        else -> Color.LightGray
    }
}
