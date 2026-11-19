package com.securevault.ui.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.securevault.ui.viewmodel.ClipboardAction
import com.securevault.utils.ConflictAction
import com.securevault.utils.ArchiveFormat
import com.securevault.utils.PropertyRow
import com.securevault.utils.formatSize
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ============ 1. PASTE CONFIRMATION ============
@Composable
fun PasteConfirmationDialog(
    sourceFileName: String,
    targetDir: String,
    action: ClipboardAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val actionStr = if (action == ClipboardAction.COPY) "Copy" else "Move"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$actionStr File", color = Color.White) },
        text = { Text("Do you want to $actionStr '$sourceFileName' to '$targetDir'?", color = Color.LightGray) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("OK", color = Color.Green) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) } },
        containerColor = Color(0xFF1E1E1E)
    )
}

// ============ 2. CONFLICT DIALOG ============
@Composable
fun ConflictDialog(
    fileName: String,
    targetDir: String,
    onResolve: (ConflictAction) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("File already exists", color = Color.White) },
        text = {
            Column {
                Text(fileName, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Target: $targetDir", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onResolve(ConflictAction.REPLACE) }) { Text("Replace", color = Color.White) }
                TextButton(onClick = { onResolve(ConflictAction.SKIP) }) { Text("Skip", color = Color.Gray) }
                TextButton(onClick = { onResolve(ConflictAction.KEEP_BOTH) }) { Text("Keep Both", color = Color.Green) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Red) } },
        containerColor = Color(0xFF1E1E1E)
    )
}

// ============ 3. FILE TOOLS (Encrypt/Decrypt) ============
@Composable
fun FileToolsDialog(
    fileName: String,
    isSvltFile: Boolean,
    onEncrypt: () -> Unit,
    onDecrypt: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tools", color = Color.White) },
        text = {
            Column {
                Text("File: $fileName", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onEncrypt,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C), contentColor = Color.White)
                ) { Text("Encrypt to Vault") }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onDecrypt,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isSvltFile,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C), contentColor = Color.Green)
                ) { Text("Decrypt File") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Color.White) } },
        containerColor = Color(0xFF1E1E1E)
    )
}

// ============ 4. RENAME FILE ============
@Composable
fun RenameFileDialog(
    currentName: String,
    onRename: (newName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var renameInput by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename", color = Color.White) },
        text = {
            OutlinedTextField(
                value = renameInput,
                onValueChange = { renameInput = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )
        },
        confirmButton = { TextButton(onClick = { onRename(renameInput) }) { Text("Rename", color = Color.White) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) } },
        containerColor = Color(0xFF1E1E1E)
    )
}

// ============ 5. DELETE FILE ============
@Composable
fun DeleteFileDialog(
    fileName: String,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete", color = Color.White) },
        text = { Text("Are you sure you want to delete '$fileName'?", color = Color.LightGray) },
        confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = Color.Red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) } },
        containerColor = Color(0xFF1E1E1E)
    )
}

// ============ 6. FILE PROPERTIES ============
@Composable
fun FilePropertiesDialog(
    filePath: String,
    fileName: String,
    isDirectory: Boolean,
    fileSize: Long,
    onDismiss: () -> Unit
) {
    val file = File(filePath)
    val date = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified()))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Properties", color = Color.White) },
        text = {
            Column {
                PropertyRow("Name", fileName)
                PropertyRow("Path", filePath)
                PropertyRow("Type", if (isDirectory) "Folder" else "File")
                PropertyRow("Size", formatSize(fileSize))
                PropertyRow("Modified", date)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Color.White) } },
        containerColor = Color(0xFF1E1E1E)
    )
}

@Composable
fun ArchiveFormatDialog(
    fileName: String,
    onSelect: (ArchiveFormat) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compress", color = Color.White) },
        text = {
            Column {
                Text("Create archive from:", color = Color.LightGray)
                Spacer(Modifier.height(6.dp))
                Text(fileName, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text("Choose archive format:", color = Color.LightGray)
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onSelect(ArchiveFormat.ZIP) }) {
                    Text("ZIP", color = Color.White)
                }
                TextButton(onClick = { onSelect(ArchiveFormat.TAR_GZ) }) {
                    Text("TAR.GZ", color = Color.Green)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF1E1E1E)
    )
}
