package com.securevault.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securevault.data.local.ContainerItem
import com.securevault.ui.viewmodel.ContainerViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ContainerScreen(
    masterPassword: String,
    viewModel: ContainerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedContainer by remember { mutableStateOf<ContainerItem?>(null) }
    
    // Form fields
    var newName by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    
    var useMasterPassword by remember { mutableStateOf(false) }
    var enableBiometric by remember { mutableStateOf(false) }
    
    // Password Visibility Toggles
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Containers", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    newName = ""; newPassword = ""; confirmPassword = ""
                    useMasterPassword = false; enableBiometric = false
                    isPasswordVisible = false; isConfirmVisible = false
                    showCreateDialog = true 
                },
                containerColor = Color.White,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Container")
            }
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color.White)
            }

            uiState.message?.let {
                Text(it, color = if (it.startsWith("✅")) Color.Green else Color.Red, modifier = Modifier.padding(16.dp))
            }

            if (uiState.containers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No containers yet. Tap + to create.", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.containers) { item ->
                        ContainerCard(
                            item = item,
                            onClick = { /* TODO: Mount (Fase 5.3) */ },
                            onLongClick = { 
                                selectedContainer = item
                                showDeleteDialog = true 
                            }
                        )
                    }
                }
            }
        }
    }

    // Dialog Create
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Container", color = Color.White) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Container Name (e.g., Work)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    Spacer(Modifier.height(8.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = useMasterPassword,
                            onCheckedChange = { useMasterPassword = it },
                            colors = CheckboxDefaults.colors(checkedColor = Color.White, uncheckedColor = Color.Gray)
                        )
                        Text("Use Master Password", color = Color.White)
                    }
                    Text("Note: If you change Master Password, this container will require the old one.", color = Color.Gray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                    Spacer(Modifier.height(8.dp))

                    if (!useMasterPassword) {
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("Container Password") },
                            singleLine = true,
                            enabled = !useMasterPassword,
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                val icon = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(imageVector = icon, contentDescription = "Toggle Password", tint = Color.Gray)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                disabledTextColor = Color.Gray, disabledBorderColor = Color.DarkGray
                            )
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirm Password") },
                            singleLine = true,
                            enabled = !useMasterPassword,
                            visualTransformation = if (isConfirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                val icon = if (isConfirmVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                IconButton(onClick = { isConfirmVisible = !isConfirmVisible }) {
                                    Icon(imageVector = icon, contentDescription = "Toggle Confirm Password", tint = Color.Gray)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                disabledTextColor = Color.Gray, disabledBorderColor = Color.DarkGray
                            )
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = enableBiometric,
                            onCheckedChange = { enableBiometric = it },
                            colors = CheckboxDefaults.colors(checkedColor = Color.White, uncheckedColor = Color.Gray)
                        )
                        Text("Enable Biometric Unlock", color = Color.White)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val finalPassword = if (useMasterPassword) masterPassword else newPassword
                    val isValid = if (useMasterPassword) true else (newPassword == confirmPassword && newPassword.length >= 4)
                    
                    if (newName.isNotBlank() && isValid) {
                        viewModel.createContainer(newName, finalPassword, useMasterPassword, enableBiometric)
                        showCreateDialog = false
                    }
                }) { Text("Create", color = Color.Green) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel", color = Color.Gray) }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    // Dialog Delete
    if (showDeleteDialog && selectedContainer != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Container", color = Color.White) },
            text = { Text("Are you sure you want to delete '${selectedContainer!!.name}'? All files inside will be permanently lost.", color = Color.LightGray) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteContainer(selectedContainer!!)
                    showDeleteDialog = false
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel", color = Color.Gray) }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContainerCard(item: ContainerItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (item.isMounted) Icons.Default.LockOpen else Icons.Default.Lock,
                contentDescription = null,
                tint = if (item.isMounted) Color.Green else Color(0xFFFFC107)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "${formatSize(item.size)} • ${if (item.isMounted) "Unlocked" else "Locked"}",
                    color = Color.Gray, style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
// formatSize TIDAK ditulis di sini karena udah ada di FileManagerScreen.kt
