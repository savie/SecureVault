package com.securevault.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securevault.data.local.VaultItem
import com.securevault.ui.viewmodel.VaultViewModel
import com.securevault.ui.components.AppTopBar
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    masterPassword: String, 
    onLock: () -> Unit,
    viewModel: VaultViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedFile by remember { mutableStateOf<VaultItem?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPropertiesDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) viewModel.encryptFile(masterPassword, uri)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Vault",
                actions = {
                    IconButton(onClick = { viewModel.refreshFiles() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                }
            )
        },
        // FIX: FAB dihapus dari Scaffold, pindah ke dalam Box
        containerColor = Color(0xFF121212)
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (uiState.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color.White)
                }

                if (uiState.files.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No files yet. Tap + to add.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.files) { file ->
                            VaultItemCard(
                                file = file,
                                onClick = {
                                    Toast.makeText(context, "File viewer coming soon", Toast.LENGTH_SHORT).show()
                                },
                                onLongClick = { selectedFile = file; showMenu = true }
                            )
                        }
                    }
                }
            }

            // FIX: Row buat Message + FAB
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Message (kiri) - ambil sisa space
                if (uiState.message != null) {
                    Card(
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Text(
                            text = uiState.message!!,
                            color = when {
                                uiState.message!!.startsWith("✅") -> Color.Green
                                uiState.message!!.startsWith("❌") -> Color.Red
                                else -> Color.LightGray
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // FAB (kanan)
                FloatingActionButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    containerColor = Color.White,
                    contentColor = Color.Black
                ) { Icon(Icons.Default.Add, contentDescription = "Add File to Vault") }
            }
        }
    }

    if (showMenu && selectedFile != null) {
        ModalBottomSheet(
            onDismissRequest = { showMenu = false },
            containerColor = Color(0xFF1E1E1E)
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text(selectedFile!!.originalName, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(selectedFile!!.originalPath, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))

                ListItem(
                    headlineContent = { Text("Restore (Decrypt)", color = Color.Green) },
                    leadingContent = { Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.Green) },
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            viewModel.restoreFile(masterPassword, selectedFile!!)
                            showMenu = false
                        }
                    )
                )
                ListItem(
                    headlineContent = { Text("Delete from Vault", color = Color.Red) },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                    modifier = Modifier.combinedClickable(
                        onClick = { showDeleteDialog = true; showMenu = false }
                    )
                )
                ListItem(
                    headlineContent = { Text("Properties", color = Color.White) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = Color.White) },
                    modifier = Modifier.combinedClickable(
                        onClick = { showPropertiesDialog = true; showMenu = false }
                    )
                )
            }
        }
    }

    if (showDeleteDialog && selectedFile != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete File", color = Color.White) },
            text = { Text("Are you sure you want to permanently delete '${selectedFile!!.originalName}' from Vault?", color = Color.LightGray) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFile(selectedFile!!)
                    showDeleteDialog = false
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel", color = Color.Gray) }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    if (showPropertiesDialog && selectedFile != null) {
        val date = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date(selectedFile!!.timestamp))
        AlertDialog(
            onDismissRequest = { showPropertiesDialog = false },
            title = { Text("Properties", color = Color.White) },
            text = {
                Column {
                    PropertyRow("Name", selectedFile!!.originalName)
                    PropertyRow("Original Path", selectedFile!!.originalPath)
                    PropertyRow("Type", selectedFile!!.mimeType)
                    PropertyRow("Size", formatSize(selectedFile!!.fileSize))
                    PropertyRow("Encrypted On", date)
                }
            },
            confirmButton = {
                TextButton(onClick = { showPropertiesDialog = false }) { Text("Close", color = Color.White) }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }
}

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
