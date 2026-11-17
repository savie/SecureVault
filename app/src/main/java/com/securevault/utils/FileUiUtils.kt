package com.securevault.utils

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.securevault.ui.viewmodel.FileItem
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import java.io.File

fun formatSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
        size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
        else -> String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
    }
}

fun getFileIcon(item: FileItem): ImageVector {
    if (item.name.startsWith("📦")) return Icons.Default.FolderSpecial
    if (item.isDirectory) return Icons.Default.Folder
    val name = item.name.lowercase()
    return when {
        name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") -> Icons.Default.Image
        name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac") || name.endsWith(".m4a") -> Icons.Default.Audiotrack
        name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") -> Icons.Default.Movie
        name.endsWith(".pdf") -> Icons.Default.PictureAsPdf
        name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") -> Icons.Default.Archive
        name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".log") -> Icons.Default.Description
        name.endsWith(".apk") -> Icons.Default.Android
        name.endsWith(".sh") -> Icons.Default.Terminal
        name.endsWith(".svlt") -> Icons.Default.Lock
        else -> Icons.Default.InsertDriveFile
    }
}

fun getIconColor(item: FileItem): Color {
    if (item.name.startsWith("📦")) return Color(0xFF00BCD4)
    if (item.isDirectory) return Color(0xFFFFC107)
    val name = item.name.lowercase()
    return when {
        name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") -> Color(0xFF4CAF50)
        name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac") || name.endsWith(".m4a") -> Color(0xFFE91E63)
        name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") -> Color(0xFF9C27B0)
        name.endsWith(".pdf") -> Color(0xFFF44336)
        name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") -> Color(0xFFFF9800)
        name.endsWith(".apk") -> Color(0xFF2196F3)
        name.endsWith(".sh") -> Color(0xFF00BCD4)
        name.endsWith(".svlt") -> Color(0xFFFFEB3B)
        else -> Color.LightGray
    }
}

@Composable
fun PropertyRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = Color.Gray, modifier = Modifier.width(100.dp))
        Text(value, color = Color.White)
    }
}

fun openFile(context: android.content.Context, item: FileItem) {
    try {
        val ext = item.name.substringAfterLast(".", "")
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.fromFile(File(item.path)), mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    } catch (e: Exception) { Toast.makeText(context, "No app found", Toast.LENGTH_SHORT).show() }
}
