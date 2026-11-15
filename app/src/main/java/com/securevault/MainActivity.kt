package com.securevault

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.securevault.ui.screens.ContainerScreen
import com.securevault.ui.screens.FileManagerScreen
import com.securevault.ui.screens.SettingsScreen
import com.securevault.ui.screens.SetupScreen
import com.securevault.ui.screens.UnlockScreen
import com.securevault.ui.screens.VaultScreen
import com.securevault.ui.theme.SecureVaultTheme
import com.securevault.ui.viewmodel.FileManagerViewModel

data class BottomNavItem(val title: String, val icon: ImageVector, val route: String)

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SecureVaultTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
    var biometricEnabled by remember { mutableStateOf(prefs.getBoolean("biometric_enabled", true)) }
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
            biometricEnabled = biometricEnabled,
            onUnlock = { unlocked = true }
        )
        else -> MainAppScreen(
            masterPassword = savedPassword!!,
            onLock = { unlocked = false },
            onBiometricToggle = { isEnabled ->
                biometricEnabled = isEnabled
                prefs.edit().putBoolean("biometric_enabled", isEnabled).apply()
            },
            biometricEnabled = biometricEnabled
        )
    }
}

@Composable
fun MainAppScreen(
    masterPassword: String, 
    onLock: () -> Unit,
    biometricEnabled: Boolean,
    onBiometricToggle: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    val vaultViewModel: com.securevault.ui.viewmodel.VaultViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val fileManagerViewModel: FileManagerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    val navItems = listOf(
        BottomNavItem("Vault", Icons.Default.Home, "vault"),
        BottomNavItem("Container", Icons.Default.Lock, "container"),
        BottomNavItem("Explorer", Icons.Default.Folder, "file_manager"),
        BottomNavItem("Settings", Icons.Default.Settings, "settings")
    )

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF1E1E1E)) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                navItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = Color.White,
                            indicatorColor = Color.White,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(navController = navController, startDestination = "vault", modifier = Modifier.padding(padding)) {
            composable("vault") { 
                LaunchedEffect(Unit) { vaultViewModel.refreshFiles() }
                VaultScreen(masterPassword = masterPassword, onLock = onLock, viewModel = vaultViewModel) 
            }
            composable("container") { 
                ContainerScreen(masterPassword = masterPassword) 
            }
            composable("file_manager") {
                LaunchedEffect(Unit) { fileManagerViewModel.refreshAllPanels() } 
                BackHandler(enabled = true) {
                    val moved = fileManagerViewModel.navigateUp(isLeft = true)
                    if (!moved) {
                        navController.navigate("vault") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                FileManagerScreen(
                    masterPassword = masterPassword,
                    viewModel = fileManagerViewModel
                )
            }
            composable("settings") { 
                SettingsScreen(
                    onLock = onLock, 
                    biometricEnabled = biometricEnabled,
                    onBiometricToggle = onBiometricToggle
                ) 
            }
        }
    }
}
