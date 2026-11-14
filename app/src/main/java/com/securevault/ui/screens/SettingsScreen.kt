package com.securevault.ui.screens

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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.securevault.utils.RootUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLock: () -> Unit,
    biometricEnabled: Boolean,
    onBiometricToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
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
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
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

            Spacer(Modifier.height(24.dp))
            Text("Storage Access Status", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
            Spacer(Modifier.height(8.dp))

            // Status Root
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
