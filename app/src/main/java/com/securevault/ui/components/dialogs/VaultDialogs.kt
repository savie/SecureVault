package com.securevault.ui.components.dialogs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.securevault.data.local.VaultItem
import com.securevault.utils.PropertyRow
import com.securevault.utils.formatSize
import java.text.SimpleDateFormat
import java.util.*

// ============ 1. VAULT BOTTOM SHEET ============
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VaultBottomSheet(
    file: VaultItem,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onProperties: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(file.originalName, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(file.originalPath, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))

            ListItem(
                headlineContent = { Text("Restore (Decrypt)", color = Color.Green) },
                leadingContent = { Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.Green) },
                modifier = Modifier.combinedClickable(onClick = onRestore)
            )
            ListItem(
                headlineContent = { Text("Delete from Vault", color = Color.Red) },
                leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                modifier = Modifier.combinedClickable(onClick = onDelete)
            )
            ListItem(
                headlineContent = { Text("Properties", color = Color.White) },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = Color.White) },
                modifier = Modifier.combinedClickable(onClick = onProperties)
            )
        }
    }
}

// ============ 2. DELETE VAULT FILE ============
@Composable
fun DeleteVaultFileDialog(
    fileName: String,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete File", color = Color.White) },
        text = { Text("Are you sure you want to permanently delete '$fileName' from Vault?", color = Color.LightGray) },
        confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = Color.Red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) } },
        containerColor = Color(0xFF1E1E1E)
    )
}

// ============ 3. VAULT PROPERTIES ============
@Composable
fun VaultFilePropertiesDialog(
    file: VaultItem,
    onDismiss: () -> Unit
) {
    val date = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date(file.timestamp))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Properties", color = Color.White) },
        text = {
            Column {
                PropertyRow("Name", file.originalName)
                PropertyRow("Original Path", file.originalPath)
                PropertyRow("Type", file.mimeType)
                PropertyRow("Size", formatSize(file.fileSize))
                PropertyRow("Encrypted On", date)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Color.White) } },
        containerColor = Color(0xFF1E1E1E)
    )
}
