package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SetupScreen(onDone: (String) -> Unit) {
    var pwd by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    
    var isPwdVisible by remember { mutableStateOf(false) }
    var isConfirmVisible by remember { mutableStateOf(false) }

    val isMinLength = pwd.length >= 8
    val hasUpperCase = pwd.any { it.isUpperCase() }
    val hasLowerCase = pwd.any { it.isLowerCase() }
    val hasDigit = pwd.any { it.isDigit() }
    val hasSymbol = pwd.any { !it.isLetterOrDigit() }
    val isStrong = isMinLength && hasUpperCase && hasLowerCase && hasDigit && hasSymbol

    var strengthColor = Color.Gray
    var strengthText = "Too Short"
    if (pwd.isNotEmpty()) {
        if (isStrong) { strengthColor = Color(0xFF4CAF50); strengthText = "Strong" }
        else if (isMinLength && (hasUpperCase || hasDigit || hasSymbol)) { strengthColor = Color(0xFFFFC107); strengthText = "Medium" }
        else { strengthColor = Color(0xFFF44336); strengthText = "Weak" }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Create Master Password", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("This secures your vault. Do not forget it.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        
        Spacer(Modifier.height(24.dp))
        
        OutlinedTextField(
            value = pwd, onValueChange = { pwd = it },
            label = { Text("Password") },
            visualTransformation = if (isPwdVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val icon = if (isPwdVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { isPwdVisible = !isPwdVisible }) {
                    Icon(imageVector = icon, contentDescription = "Toggle Password")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(8.dp))
        
        if (pwd.isNotEmpty()) {
            Text("Strength: $strengthText", color = strengthColor, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { if (isStrong) 1f else if (strengthText == "Medium") 0.5f else 0.25f },
                color = strengthColor,
                modifier = Modifier.fillMaxWidth().height(4.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text("Must contain: 8+ chars, A-Z, a-z, 0-9, symbol.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = confirm, onValueChange = { confirm = it },
            label = { Text("Confirm Password") },
            visualTransformation = if (isConfirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val icon = if (isConfirmVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { isConfirmVisible = !isConfirmVisible }) {
                    Icon(imageVector = icon, contentDescription = "Toggle Password")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))
        
        Button(
            onClick = {
                when {
                    !isStrong -> error = "Password does not meet requirements."
                    pwd != confirm -> error = "Passwords do not match."
                    else -> onDone(pwd)
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.background)
        ) { Text("Create Vault", fontSize = 16.sp) }
    }
}
