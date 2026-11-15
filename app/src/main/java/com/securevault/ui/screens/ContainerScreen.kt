package com.securevault.ui.screens

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securevault.data.local.ContainerItem
import com.securevault.ui.viewmodel.ContainerViewModel
import com.securevault.utils.KeystoreHelper
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

sealed class PasswordAction {
    data class Mount(val item: ContainerItem) : PasswordAction()
    data class Unmount(val item: ContainerItem) : PasswordAction()
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ContainerScreen(
    masterPassword: String,
    viewModel: ContainerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // Dialog states
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showPropertiesDialog by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    
    var selectedContainer by remember { mutableStateOf<ContainerItem?>(null) }
    var passwordAction by remember { mutableStateOf<PasswordAction?>(null) }
    
    // Password in memory (buat unmount tanpa nanya password lagi)
    val mountedPasswords = remember { mutableStateMapOf<String, String>() }
    
    // Form fields
    var newName by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var renameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var useMasterPassword by remember { mutableStateOf(false) }
    var enableBiometric by remember { mutableStateOf(false) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmVisible by remember { mutableStateOf(false) }

    // Password Strength
    val isMinLength = newPassword.length >= 8
    val hasUpperCase = newPassword.any { it.isUpperCase() }
    val hasLowerCase = newPassword.any { it.isLowerCase() }
    val hasDigit = newPassword.any { it.isDigit() }
    val hasSymbol = newPassword.any { !it.isLetterOrDigit() }
    val isStrong = isMinLength && hasUpperCase && hasLowerCase && hasDigit && hasSymbol

    var strengthColor = Color.Gray
    var strengthText = "Too Short"
    if (newPassword.isNotEmpty()) {
        if (isStrong) { strengthColor = Color(0xFF4CAF50); strengthText = "Strong" }
        else if (isMinLength && (hasUpperCase || hasDigit || hasSymbol)) { strengthColor = Color(0xFFFFC107); strengthText = "Medium" }
        else { strengthColor = Color(0xFFF44336); strengthText = "Weak" }
    }

    // Biometric prompt setup
    val activity = remember(context) { context.findFragmentActivity() }
    val canAuthenticate = BiometricManager.from(context)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

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
            ) { Icon(Icons.Default.Add, contentDescription = "Add Container") }
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (uiState.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color.White)

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
                            onClick = { selectedContainer = item; showBottomSheet = true },
                            onLongClick = { selectedContainer = item; showBottomSheet = true }
                        )
                    }
                }
            }
        }
    }

    // ============ BOTTOM SHEET MENU ============
    if (showBottomSheet && selectedContainer != null) {
        val item = selectedContainer!!
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            containerColor = Color(0xFF1E1E1E)
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text(item.name, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${formatSize(item.size)} • ${if (item.isMounted) "Unlocked" else "Locked"}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))

                ListItem(
                    headlineContent = { Text(if (item.isMounted) "Lock" else "Unlock", color = Color.Green) },
                    leadingContent = { Icon(if (item.isMounted) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = null, tint = Color.Green) },
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            showBottomSheet = false
                            handleUnlockLock(item, item.isMounted, masterPassword, mountedPasswords, viewModel, context, canAuthenticate, activity) {
                                passwordAction = it
                                showPasswordDialog = true
                            }
                        }
                    )
                )
                ListItem(
                    headlineContent = { Text("Rename", color = Color.White) },
                    leadingContent = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White) },
                    modifier = Modifier.combinedClickable(
                        onClick = { renameInput = item.name; showRenameDialog = true; showBottomSheet = false }
                    )
                )
                ListItem(
                    headlineContent = { Text("Properties", color = Color.White) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = Color.White) },
                    modifier = Modifier.combinedClickable(
                        onClick = { showPropertiesDialog = true; showBottomSheet = false }
                    )
                )
                ListItem(
                    headlineContent = { Text("Delete", color = Color.Red) },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                    modifier = Modifier.combinedClickable(
                        onClick = { showDeleteDialog = true; showBottomSheet = false }
                    )
                )
            }
        }
    }

    // ============ DIALOG PASSWORD (Mount/Unmount) ============
    if (showPasswordDialog && passwordAction != null) {
        val action = passwordAction!!
        val actionText = when (action) {
            is PasswordAction.Mount -> "Unlock Container"
            is PasswordAction.Unmount -> "Lock Container (Enter Password)"
        }
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false; passwordAction = null },
            title = { Text(actionText, color = Color.White) },
            text = {
                Column {
                    Text("Enter password for '${when (action) {
                        is PasswordAction.Mount -> action.item.name
                        is PasswordAction.Unmount -> action.item.name
                    }}'", color = Color.LightGray)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val icon = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(imageVector = icon, contentDescription = "Toggle", tint = Color.Gray)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when (val a = action) {
                        is PasswordAction.Mount -> {
                            viewModel.mountContainer(a.item, passwordInput) { mountDir ->
                                mountedPasswords[a.item.id] = passwordInput
                            }
                        }
                        is PasswordAction.Unmount -> {
                            viewModel.unmountContainer(a.item, passwordInput)
                            mountedPasswords.remove(a.item.id)
                        }
                    }
                    passwordInput = ""
                    showPasswordDialog = false
                    passwordAction = null
                }) { Text("OK", color = Color.Green) }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false; passwordAction = null; passwordInput = "" }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    // ============ DIALOG CREATE ============
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

                        if (newPassword.isNotEmpty()) {
                            Text("Strength: $strengthText", color = strengthColor, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { if (isStrong) 1f else if (strengthText == "Medium") 0.5f else 0.25f },
                                color = strengthColor,
                                modifier = Modifier.fillMaxWidth().height(4.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("Must contain: 8+ chars, A-Z, a-z, 0-9, symbol.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                            Spacer(Modifier.height(8.dp))
                        }

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
                    val isValid = if (useMasterPassword) true else (isStrong && newPassword == confirmPassword)
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

    // ============ DIALOG RENAME ============
    if (showRenameDialog && selectedContainer != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Container", color = Color.White) },
            text = {
                Column {
                    Text("Current: ${selectedContainer!!.name}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        label = { Text("New Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Note: Container must be Locked.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameContainer(selectedContainer!!, renameInput)
                    showRenameDialog = false
                }) { Text("Rename", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel", color = Color.Gray) }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    // ============ DIALOG DELETE ============
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

    // ============ DIALOG PROPERTIES ============
    if (showPropertiesDialog && selectedContainer != null) {
        val item = selectedContainer!!
        val date = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date(item.createdAt))
        AlertDialog(
            onDismissRequest = { showPropertiesDialog = false },
            title = { Text("Properties", color = Color.White) },
            text = {
                Column {
                    PropertyRow("Name", item.name)
                    PropertyRow("Path", item.filePath)
                    PropertyRow("Size", formatSize(item.size))
                    PropertyRow("Status", if (item.isMounted) "Unlocked" else "Locked")
                    PropertyRow("Uses Master Pwd", if (item.usesMasterPassword) "Yes" else "No")
                    PropertyRow("Biometric", if (item.biometricEnabled) "Enabled" else "Disabled")
                    PropertyRow("Created", date)
                }
            },
            confirmButton = {
                TextButton(onClick = { showPropertiesDialog = false }) { Text("Close", color = Color.White) }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }
}

// ============ HELPER: Handle Unlock/Lock ============
private fun handleUnlockLock(
    item: ContainerItem,
    isMounted: Boolean,
    masterPassword: String,
    mountedPasswords: MutableMap<String, String>,
    viewModel: ContainerViewModel,
    context: Context,
    canAuthenticate: Boolean,
    activity: FragmentActivity?,
    onNeedPassword: (PasswordAction) -> Unit
) {
    if (isMounted) {
        // Lock: gunakan password dari memory, atau minta
        val cached = mountedPasswords[item.id]
        if (cached != null) {
            viewModel.unmountContainer(item, cached)
            mountedPasswords.remove(item.id)
        } else if (item.usesMasterPassword) {
            viewModel.unmountContainer(item, masterPassword)
        } else {
            onNeedPassword(PasswordAction.Unmount(item))
        }
    } else {
        // Unlock
        if (item.usesMasterPassword) {
            viewModel.mountContainer(item, masterPassword) { }
            return
        }
        // Coba biometric dulu kalo enabled
        if (item.biometricEnabled && canAuthenticate && activity != null) {
            try {
                val prompt = BiometricPrompt(
                    activity, ContextCompat.getMainExecutor(context),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            // Decrypt password dari keystore
                            val dbHelper = com.securevault.data.local.ContainerDbHelper(context)
                            // Ambil encryptedPassword (perlu query)
                            val encrypted = com.securevault.utils.VaultImporterHelper.getEncryptedPassword(context, item.id)
                            if (encrypted != null) {
                                val pwd = KeystoreHelper.decrypt(encrypted)
                                if (pwd != null) {
                                    viewModel.mountContainer(item, pwd) { }
                                    mountedPasswords[item.id] = pwd
                                } else onNeedPassword(PasswordAction.Mount(item))
                            } else onNeedPassword(PasswordAction.Mount(item))
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            onNeedPassword(PasswordAction.Mount(item))
                        }
                    }
                )
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock ${item.name}")
                    .setSubtitle("Use your fingerprint")
                    .setNegativeButtonText("Use Password")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .build()
                prompt.authenticate(promptInfo)
            } catch (e: Exception) {
                onNeedPassword(PasswordAction.Mount(item))
            }
        } else {
            onNeedPassword(PasswordAction.Mount(item))
        }
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
