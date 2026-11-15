package com.securevault.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
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
import com.securevault.utils.RootUtils

// Opsi Auto-Lock
data class AutoLockOption(val label: String, val millis: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLock: () -> Unit,
    biometricEnabled: Boolean,
    onBiometricToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE) }
    
    // State Auto-Lock
    val autoLockOptions = listOf(
        AutoLockOption("Off", 0L),
        AutoLockOption("1 minute", 60_000L),
        AutoLockOption("5 minutes", 300_000L),
        AutoLockOption("10 minutes", 600_000L)
    )
    var selectedAutoLockMillis by remember {
        mutableStateOf(prefs.getLong("container_auto_lock_ms", 300_000L)) // Default 5 menit
    }
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = autoLockOptions.find { it.millis == selectedAutoLockMillis }?.label ?: "5 minutes"

    val isRooted = remember { RootUtils.isRootAvailable() }
    val hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onLock) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212))
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            
            Text("Security", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
            Spacer(Modifier.height(8.dp))

            // Fingerprint Toggle
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Fingerprint, contentDescription = "Fingerprint", tint = Color.White)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Biometric Unlock", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Use fingerprint to unlock vault", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = { onBiometricToggle(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color.White)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // === AUTO-LOCK CONTAINER ===
            Text("Container Auto-Lock", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                modifier = Modifier.fillMaxWidth()
            ) {
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
                                        selectedAutoLockMillis = option.millis
                                        prefs.edit().putLong("container_auto_lock_ms", option.millis).apply()
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Storage Access Status", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
            Spacer(Modifier.height(8.dp))

            // Status Root
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                modifier = Modifier.fillMaxWidth()
            ) {
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

            // Status All Files Access
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
