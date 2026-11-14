package com.securevault

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity // PERUBAHAN DI SINI
import com.securevault.ui.screens.SetupScreen
import com.securevault.ui.screens.UnlockScreen
import com.securevault.ui.screens.VaultScreen
import com.securevault.ui.theme.SecureVaultTheme

class MainActivity : FragmentActivity() { // UBAH KE FRAGMENTACTIVITY
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SecureVaultTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VaultApp()
                }
            }
        }
    }
}

@Composable
fun VaultApp() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
    var savedPassword by remember { mutableStateOf(prefs.getString("master_pwd", null)) }
    var unlocked by remember { mutableStateOf(false) }

    when {
        savedPassword == null -> SetupScreen(
            onDone = { pwd ->
                prefs.edit().putString("master_pwd", pwd).apply()
                savedPassword = pwd
            }
        )
        !unlocked -> UnlockScreen(
            savedPassword = savedPassword!!,
            onUnlock = { unlocked = true }
        )
        else -> VaultScreen(
            onLock = { unlocked = false }
        )
    }
}
