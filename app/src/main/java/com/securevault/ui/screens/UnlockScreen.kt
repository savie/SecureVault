package com.securevault.ui.screens

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.securevault.utils.BiometricUnlockStore
import kotlinx.coroutines.delay

fun Context.findFragmentActivity(): FragmentActivity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is FragmentActivity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun UnlockScreen(
    savedPassword: String,
    biometricEnabled: Boolean,
    onUnlock: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }

    var pwd by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isPwdVisible by remember { mutableStateOf(false) }

    val canAuthenticate = remember(context) {
        BiometricUnlockStore.canAuthenticate(context)
    }
    val biometricReady = biometricEnabled &&
        canAuthenticate &&
        BiometricUnlockStore.isEnrolled(context) &&
        activity != null

    val launchBiometric = {
        val host = activity
        if (host != null && biometricReady) {
            error = null
            BiometricUnlockStore.authenticate(
                activity = host,
                onSuccess = onUnlock,
                onError = { error = it }
            )
        }
    }

    LaunchedEffect(biometricReady) {
        if (biometricReady) {
            delay(500)
            launchBiometric()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Unlock Vault", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = pwd,
            onValueChange = { pwd = it },
            label = { Text("Password") },
            visualTransformation = if (isPwdVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                val icon = if (isPwdVisible) {
                    Icons.Filled.Visibility
                } else {
                    Icons.Filled.VisibilityOff
                }
                IconButton(onClick = { isPwdVisible = !isPwdVisible }) {
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
                if (pwd == savedPassword) {
                    onUnlock()
                } else {
                    error = "Incorrect password."
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onBackground,
                contentColor = MaterialTheme.colorScheme.background
            )
        ) {
            Text("Unlock with Password")
        }

        if (biometricReady) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { launchBiometric() },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Try Fingerprint Again")
            }
        }
    }
}
