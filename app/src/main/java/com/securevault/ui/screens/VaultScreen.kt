package com.securevault.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securevault.data.local.VaultItem
import com.securevault.ui.viewmodel.VaultViewModel

@Composable
fun VaultScreen(
    masterPassword: String, 
    onLock: () -> Unit,
    viewModel: VaultViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) viewModel.encryptFile(masterPassword, uri)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🔓 Vault", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Row {
                // TOMBOL REFRESH
                IconButton(onClick = { viewModel.refreshFiles() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                }
                IconButton(onClick = onLock) {
                    Icon(Icons.Default.Lock, contentDescription = "Lock", tint = Color.White)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

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
                Text("Vault is empty.", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(uiState.files) { file ->
                    VaultItemCard(
                        file = file, 
                        isEnabled = !uiState.isLoading,
                        onClick = { viewModel.restoreFile(masterPassword, file) }
                    )
                }
            }
        }
    }
}

@Composable
fun VaultItemCard(file: VaultItem, isEnabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(enabled = isEnabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(file.originalName, color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text("Tap to Restore • ${formatSize(file.fileSize)}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// Helper Format Size
