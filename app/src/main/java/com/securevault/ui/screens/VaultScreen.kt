package com.securevault.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securevault.data.local.VaultItem
import com.securevault.ui.components.AppTopBar
import com.securevault.ui.components.VaultItemCard
import com.securevault.ui.components.dialogs.DeleteVaultFileDialog
import com.securevault.ui.components.dialogs.VaultBottomSheet
import com.securevault.ui.components.dialogs.VaultFilePropertiesDialog
import com.securevault.ui.viewmodel.VaultViewModel

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

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                FloatingActionButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    containerColor = Color.White,
                    contentColor = Color.Black
                ) { Icon(Icons.Default.Add, contentDescription = "Add File to Vault") }
            }
        }
    }

    if (showMenu && selectedFile != null) {
        VaultBottomSheet(
            file = selectedFile!!,
            onRestore = {
                viewModel.restoreFile(masterPassword, selectedFile!!)
                showMenu = false
            },
            onDelete = { showDeleteDialog = true; showMenu = false },
            onProperties = { showPropertiesDialog = true; showMenu = false },
            onDismiss = { showMenu = false }
        )
    }

    if (showDeleteDialog && selectedFile != null) {
        DeleteVaultFileDialog(
            fileName = selectedFile!!.originalName,
            onDelete = {
                viewModel.deleteFile(selectedFile!!)
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    if (showPropertiesDialog && selectedFile != null) {
        VaultFilePropertiesDialog(
            file = selectedFile!!,
            onDismiss = { showPropertiesDialog = false }
        )
    }
}
