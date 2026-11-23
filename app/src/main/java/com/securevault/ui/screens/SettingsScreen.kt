package com.securevault.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.securevault.fs.FileAccessManager
import com.securevault.ui.components.AppTopBar
import com.securevault.utils.AutoLockDiagnosticsStore
import com.securevault.utils.AutoLockSettingsStore
import com.securevault.utils.BiometricUnlockStore

data class AutoLockOption(val label: String, val millis: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLock: () -> Unit,
    biometricEnabled: Boolean,
    onBiometricToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var biometricMessage by remember { mutableStateOf<String?>(null) }
    var biometricEnrolling by remember { mutableStateOf(false) }
    var diagnosticsCount by remember {
        mutableStateOf(AutoLockDiagnosticsStore.getEntryCount(context))
    }
    
    val autoLockOptions = listOf(
        AutoLockOption("Off", 0L),
        AutoLockOption("1 minute", 60_000L),
        AutoLockOption("5 minutes", 300_000L),
        AutoLockOption("10 minutes", 600_000L)
    )
    var selectedAutoLockMillis by remember {
        mutableStateOf(AutoLockSettingsStore.getDurationMillis(context))
    }
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = autoLockOptions.find { it.millis == selectedAutoLockMillis }?.label ?: "5 minutes"

    // Trigger buat refresh status
    var refreshTrigger by remember { mutableStateOf(0) }

    val isRooted = remember(refreshTrigger) { FileAccessManager.rootOperations.isAvailable() }
    val hasAllFilesAccess = remember(refreshTrigger) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true
    }

    Scaffold(
        topBar = {
            // FIX: Tambah tombol Refresh
            AppTopBar(
                title = "Settings",
                actions = {
                    IconButton(onClick = { refreshTrigger++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Status", tint = Color.White)
                    }
                }
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Security", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
            Spacer(Modifier.height(8.dp))

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Fingerprint, contentDescription = "Fingerprint", tint = Color.White)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Biometric Unlock", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Use biometrics across SecureVault, including Containers", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = biometricEnabled, enabled = !biometricEnrolling,
                        onCheckedChange = { enabled ->
                            if (!enabled) {
                                BiometricUnlockStore.disable(context)
                                onBiometricToggle(false)
                                biometricMessage = "Biometric Unlock disabled."
                            } else {
                                val activity = context.findFragmentActivity()
                                if (activity == null) {
                                    biometricMessage = "Biometric authentication is unavailable."
                                } else if (!BiometricUnlockStore.canAuthenticate(context)) {
                                    biometricMessage = "Set up a strong fingerprint or face unlock first."
                                } else {
                                    biometricEnrolling = true
                                    biometricMessage = "Complete biometric authentication to enable unlock."
                                    BiometricUnlockStore.enroll(activity) { success, message ->
                                        biometricEnrolling = false
                                        onBiometricToggle(success)
                                        biometricMessage = message
                                    }
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color.White)
                    )
                }
            }

            biometricMessage?.let {
                Text(it, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))

            Text("Container Auto-Lock", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, contentDescription = "Timer", tint = Color.White)
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Lock Timer", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Lock containers after inactivity", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedLabel,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            autoLockOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label, color = Color.White) },
                                    onClick = {
                                        if (AutoLockSettingsStore.setDurationMillis(context, option.millis)) {
                                            selectedAutoLockMillis = option.millis
                                        }
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "Auto-Lock Diagnostics",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Gray
            )
            Spacer(Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E1E1E)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Internal diagnostic history",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "$diagnosticsCount events stored",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val report = AutoLockDiagnosticsStore.getReport(context)
                            val clipboard = context.getSystemService(
                                android.content.Context.CLIPBOARD_SERVICE
                            ) as ClipboardManager

                            clipboard.setPrimaryClip(
                                ClipData.newPlainText(
                                    "SecureVault Auto-Lock Diagnostics",
                                    report
                                )
                            )

                            Toast.makeText(
                                context,
                                "Diagnostics copied",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Copy Diagnostics")
                    }

                    OutlinedButton(
                        onClick = {
                            val cleared = AutoLockDiagnosticsStore.clear(context)
                            diagnosticsCount = AutoLockDiagnosticsStore.getEntryCount(context)

                            Toast.makeText(
                                context,
                                if (cleared) "Diagnostics cleared" else "Failed to clear diagnostics",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear Diagnostics")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Storage Access Status", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
            Spacer(Modifier.height(8.dp))

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isRooted) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "Root",
                        tint = if (isRooted) Color.Green else Color.Red
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Root Access", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(if (isRooted) "Granted (Unlimited Access)" else "Not Available", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), 
                modifier = Modifier.fillMaxWidth().clickable {
                    if (!hasAllFilesAccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:${context.packageName}")
                        context.startActivity(intent)
                    }
                }
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (hasAllFilesAccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "All Files",
                        tint = if (hasAllFilesAccess) Color.Green else Color.Yellow
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("All Files Access", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            if (hasAllFilesAccess) "Granted" else "Tap to Grant (Recommended for non-root)",
                            color = if (hasAllFilesAccess) Color.Gray else Color.Yellow,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            
            Button(
                onClick = onLock,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                Text("Lock Vault", fontWeight = FontWeight.Bold)
            }
        }
    }
}
