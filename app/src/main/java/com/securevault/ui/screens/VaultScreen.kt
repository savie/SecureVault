package com.securevault.ui.screens

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
            // FIX: Hapus tombol Lock. Cuma Refresh.
            AppTopBar(
                title = "Vault",
                actions = {
                    IconButton(onClick = { viewModel.refreshFiles() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                }
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color.White)
            }

            uiState.message?.let {
                Text(it, color = if (it.startsWith("✅")) Color.Green else Color.Red, modifier = Modifier.padding(vertical = 8.dp))
            }

            Button(
                onClick = { filePickerLauncher.launch("*/*") },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
                Spacer(Modifier.width(8.dp))
                Text("Add File to Vault", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

            if (uiState.files.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Vault is empty", color = Color.Gray, style = MaterialTheme.typography.titleMedium)
                        Text("Tap + to add files securely", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.files) { file ->
                        VaultItemCard(
                            file = file, 
                            isEnabled = !uiState.isLoading,
                            onClick = { selectedFile = file; showMenu = true }
                        )
                    }
                }
            }
        }
    }

    if (showMenu && selectedFile != null) {
        ModalBottomSheet(
            onDismissRequest = { showMenu = false },
            containerColor = Color(0xFF1E1E1E)
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text(selectedFile!!.originalName, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
fun VaultItemCard(file: VaultItem, isEnabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(enabled = isEnabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(file.originalName, color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text("Tap for options • ${formatSize(file.fileSize)}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
    }
}
