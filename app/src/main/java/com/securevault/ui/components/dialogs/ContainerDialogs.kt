package com.securevault.ui.components.dialogs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.sp
import com.securevault.data.local.ContainerItem
import com.securevault.ui.screens.PasswordAction
import com.securevault.utils.PropertyRow
import com.securevault.utils.formatSize
import java.text.SimpleDateFormat
import java.util.*

// ============ 1. CREATE CONTAINER DIALOG ============
@Composable
fun CreateContainerDialog(
    masterPassword: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, password: String, useMasterPassword: Boolean, enableBiometric: Boolean) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var useMasterPassword by remember { mutableStateOf(false) }
    var enableBiometric by remember { mutableStateOf(false) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmVisible by remember { mutableStateOf(false) }

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

    AlertDialog(
        onDismissRequest = onDismiss,
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
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = "Toggle", tint = Color.Gray
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
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
                        visualTransformation = if (isConfirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isConfirmVisible = !isConfirmVisible }) {
                                Icon(
                                    if (isConfirmVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = "Toggle", tint = Color.Gray
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
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
                    onCreate(newName, finalPassword, useMasterPassword, enableBiometric)
                }
            }) { Text("Create", color = Color.Green) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        },
        containerColor = Color(0xFF1E1E1E)
    )
}

// ============ 2. CONTAINER BOTTOM SHEET ============
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ContainerBottomSheet(
    item: ContainerItem,
    onDismiss: () -> Unit,
    onUnlockLockClick: () -> Unit,
    onRenameClick: () -> Unit,
    onPropertiesClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(item.name, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${formatSize(item.size)} • ${if (item.isMounted) "Unlocked" else "Locked"}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            ListItem(
                headlineContent = { Text(if (item.isMounted) "Lock" else "Unlock", color = Color.Green) },
                leadingContent = { Icon(if (item.isMounted) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = null, tint = Color.Green) },
                modifier = Modifier.combinedClickable(onClick = onUnlockLockClick)
            )
            ListItem(
                headlineContent = { Text("Rename", color = Color.White) },
                leadingContent = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White) },
                modifier = Modifier.combinedClickable(onClick = onRenameClick)
            )
            ListItem(
                headlineContent = { Text("Properties", color = Color.White) },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = Color.White) },
                modifier = Modifier.combinedClickable(onClick = onPropertiesClick)
            )
            ListItem(
                headlineContent = { Text("Delete", color = Color.Red) },
                leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                modifier = Modifier.combinedClickable(onClick = onDeleteClick)
            )
        }
    }
}

// ============ 3. PASSWORD INPUT DIALOG ============
@Composable
fun PasswordInputDialog(
    action: PasswordAction,
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit
) {
    var passwordInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    val actionText = when (action) {
        is PasswordAction.Mount -> "Unlock Container"
        is PasswordAction.Unmount -> "Lock Container (Enter Password)"
    }
    val containerName = when (action) {
        is PasswordAction.Mount -> action.item.name
        is PasswordAction.Unmount -> action.item.name
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(actionText, color = Color.White) },
        text = {
            Column {
                Text("Enter password for '$containerName'", color = Color.LightGray)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, contentDescription = "Toggle", tint = Color.Gray)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(passwordInput)
            }) { Text("OK", color = Color.Green) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        },
        containerColor = Color(0xFF1E1E1E)
    )
}

// ============ 4. RENAME DIALOG ============
@Composable
fun RenameContainerDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (newName: String) -> Unit
) {
    var renameInput by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Container", color = Color.White) },
        text = {
            Column {
                Text("Current: $currentName", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
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
            TextButton(onClick = { onRename(renameInput) }) { Text("Rename", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        },
        containerColor = Color(0xFF1E1E1E)
    )
}

// ============ 5. DELETE DIALOG ============
@Composable
fun DeleteContainerDialog(
    containerName: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Container", color = Color.White) },
        text = { Text("Are you sure you want to delete '$containerName'? All files inside will be permanently lost.", color = Color.LightGray) },
        confirmButton = {
            TextButton(onClick = onDelete) { Text("Delete", color = Color.Red) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        },
        containerColor = Color(0xFF1E1E1E)
    )
}

// ============ 6. PROPERTIES DIALOG ============
@Composable
fun ContainerPropertiesDialog(
    item: ContainerItem,
    onDismiss: () -> Unit
) {
    val date = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date(item.createdAt))
    AlertDialog(
        onDismissRequest = onDismiss,
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
            TextButton(onClick = onDismiss) { Text("Close", color = Color.White) }
        },
        containerColor = Color(0xFF1E1E1E)
    )
}
