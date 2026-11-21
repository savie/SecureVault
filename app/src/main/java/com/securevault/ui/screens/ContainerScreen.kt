package com.securevault.ui.screens

import androidx.biometric.BiometricManager
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
import com.securevault.data.local.ContainerItem
import com.securevault.ui.components.AppTopBar
import com.securevault.ui.components.ContainerCard
import com.securevault.ui.components.dialogs.*
import com.securevault.ui.viewmodel.ContainerViewModel

sealed class PasswordAction {
    data class Mount(val item: ContainerItem) : PasswordAction()
    data class Unmount(val item: ContainerItem) : PasswordAction()
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ContainerScreen(
    masterPassword: String,
    biometricGloballyEnabled: Boolean,
    viewModel: ContainerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showPropertiesDialog by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    var selectedContainer by remember { mutableStateOf<ContainerItem?>(null) }
    var passwordAction by remember { mutableStateOf<PasswordAction?>(null) }

    val mountedPasswords = remember { mutableStateMapOf<String, String>() }

    val activity = remember(context) { context.findFragmentActivity() }
    val canAuthenticate = BiometricManager.from(context)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Containers",
                actions = {
                    IconButton(onClick = { viewModel.refreshContainers() }) {
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

                if (uiState.containers.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No containers yet. Tap + to create.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.containers) { item ->
                            ContainerCard(
                                item = item,
                                onClick = { selectedContainer = item; showBottomSheet = true },
                                onLongClick = { selectedContainer = item; showBottomSheet = true }
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
                                uiState.message!!.startsWith("🔒") -> Color(0xFF03A9F4)
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
                    onClick = { showCreateDialog = true },
                    containerColor = Color.White,
                    contentColor = Color.Black
                ) { Icon(Icons.Default.Add, contentDescription = "Add Container") }
            }
        }
    }

    if (showBottomSheet && selectedContainer != null) {
        ContainerBottomSheet(
            item = selectedContainer!!,
            onDismiss = { showBottomSheet = false },
            onUnlockLockClick = {
                val item = selectedContainer!!
                showBottomSheet = false
                handleUnlockLock(
                    item = item,
                    isMounted = item.isMounted,
                    masterPassword = masterPassword,
                    mountedPasswords = mountedPasswords,
                    viewModel = viewModel,
                    context = context,
                    canAuthenticate = canAuthenticate,
                    biometricGloballyEnabled = biometricGloballyEnabled,
                    activity = activity
                ) {
                    passwordAction = it
                    showPasswordDialog = true
                }
            },
            onRenameClick = { showRenameDialog = true; showBottomSheet = false },
            onPropertiesClick = { showPropertiesDialog = true; showBottomSheet = false },
            onDeleteClick = { showDeleteDialog = true; showBottomSheet = false }
        )
    }

    if (showPasswordDialog && passwordAction != null) {
        PasswordInputDialog(
            action = passwordAction!!,
            onDismiss = { showPasswordDialog = false; passwordAction = null },
            onConfirm = { pwd ->
                when (val a = passwordAction!!) {
                    is PasswordAction.Mount -> {
                        viewModel.mountContainer(a.item, pwd) { _ ->
                            mountedPasswords[a.item.id] = pwd
                        }
                    }
                    is PasswordAction.Unmount -> {
                        viewModel.unmountContainer(a.item, pwd) { success -> if (success) mountedPasswords.remove(a.item.id) }
                    }
                }
                showPasswordDialog = false
                passwordAction = null
            }
        )
    }

    if (showCreateDialog) {
        CreateContainerDialog(
            masterPassword = masterPassword,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, password, useMaster, biometric ->
                viewModel.createContainer(name, password, useMaster, biometric)
                showCreateDialog = false
            }
        )
    }

    if (showRenameDialog && selectedContainer != null) {
        RenameContainerDialog(
            currentName = selectedContainer!!.name,
            onDismiss = { showRenameDialog = false },
            onRename = { newName ->
                viewModel.renameContainer(selectedContainer!!, newName)
                showRenameDialog = false
            }
        )
    }

    if (showDeleteDialog && selectedContainer != null) {
        DeleteContainerDialog(
            containerName = selectedContainer!!.name,
            onDismiss = { showDeleteDialog = false },
            onDelete = {
                viewModel.deleteContainer(selectedContainer!!)
                showDeleteDialog = false
            }
        )
    }

    if (showPropertiesDialog && selectedContainer != null) {
        ContainerPropertiesDialog(
            item = selectedContainer!!,
            onDismiss = { showPropertiesDialog = false }
        )
    }
}
