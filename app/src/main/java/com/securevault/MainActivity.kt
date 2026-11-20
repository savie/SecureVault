package com.securevault

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.securevault.data.repository.ContainerRepository
import com.securevault.ui.screens.ContainerScreen
import com.securevault.ui.screens.FileManagerScreen
import com.securevault.ui.screens.SettingsScreen
import com.securevault.ui.screens.SetupScreen
import com.securevault.ui.screens.UnlockScreen
import com.securevault.ui.screens.VaultScreen
import com.securevault.ui.theme.SecureVaultTheme
import com.securevault.ui.viewmodel.FileManagerViewModel
import com.securevault.utils.ContainerSessionManager
import com.securevault.utils.BiometricUnlockStore
import com.securevault.utils.MasterPasswordStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class BottomNavItem(val title: String, val icon: ImageVector, val route: String)

class MainActivity : FragmentActivity() {
    private var autoLockJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        val sessions = ContainerSessionManager.getAllSessions()
                        if (sessions.isNotEmpty()) {
                            val prefs = getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
                            val delayMs = prefs.getLong("container_auto_lock_ms", 300_000L)
                            if (delayMs > 0) {
                                autoLockJob?.cancel()
                                autoLockJob = lifecycleScope.launch {
                                    delay(delayMs)
                                    val repo = ContainerRepository(applicationContext)
                                    val locked = repo.autoLockAll()
                                    if (locked > 0) ContainerSessionManager.triggerAutoLockEvent()
                                }
                            }
                        }
                    }
                    Lifecycle.Event.ON_START -> {
                        autoLockJob?.cancel()
                        autoLockJob = null
                    }
                    else -> {}
                }
            }
        )

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

    var passwordState by remember(context) {
        mutableStateOf(MasterPasswordStore.loadOrMigrate(context))
    }
    var biometricEnabled by remember {
        mutableStateOf(prefs.getBoolean("biometric_enabled", false) && BiometricUnlockStore.isEnrolled(context))
    }
    var unlocked by remember { mutableStateOf(false) }

    when (val state = passwordState) {
        is MasterPasswordStore.LoadResult.Loaded -> {
            if (!unlocked) {
                UnlockScreen(
                    savedPassword = state.password,
                    biometricEnabled = biometricEnabled,
                    onUnlock = { unlocked = true }
                )
            } else {
                MainAppScreen(
                    masterPassword = state.password,
                    onLock = { unlocked = false },
                    onBiometricToggle = { isEnabled ->
                        biometricEnabled = isEnabled
                        prefs.edit().putBoolean("biometric_enabled", isEnabled).apply()
                    },
                    biometricEnabled = biometricEnabled
                )
            }
        }

        MasterPasswordStore.LoadResult.NotSet -> {
            SetupScreen(
                onDone = { password ->
                    val saved = MasterPasswordStore.save(context, password)
                    if (saved) {
                        passwordState = MasterPasswordStore.LoadResult.Loaded(password)
                    }
                    saved
                }
            )
        }

        is MasterPasswordStore.LoadResult.Error -> {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Master Password Error", style = MaterialTheme.typography.headlineSmall)
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                Text(state.message, color = MaterialTheme.colorScheme.error)
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    passwordState = MasterPasswordStore.loadOrMigrate(context)
                }) {
                    Text("Retry")
                }
            }
        }
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

    // State untuk hide on scroll
    var isBottomBarVisible by remember { mutableStateOf(true) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (source == NestedScrollSource.Drag) {
                    if (available.y < -10) isBottomBarVisible = false
                    if (available.y > 10) isBottomBarVisible = true
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    val navItems = listOf(
        BottomNavItem("Vault", Icons.Default.Home, "vault"),
        BottomNavItem("Container", Icons.Default.Lock, "container"),
        BottomNavItem("Explorer", Icons.Default.Folder, "file_manager"),
        BottomNavItem("Settings", Icons.Default.Settings, "settings")
    )

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = isBottomBarVisible,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
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
        }
    ) { padding ->
        NavHost(navController = navController, startDestination = "vault", modifier = Modifier.padding(padding).nestedScroll(nestedScrollConnection)) {
            composable("vault") { 
                LaunchedEffect(Unit) { vaultViewModel.refreshFiles() }
                VaultScreen(masterPassword = masterPassword, onLock = onLock, viewModel = vaultViewModel) 
            }
            composable("container") { 
                ContainerScreen(masterPassword = masterPassword, biometricGloballyEnabled = biometricEnabled)
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
                FileManagerScreen(masterPassword = masterPassword, viewModel = fileManagerViewModel)
            }
            composable("settings") { 
                SettingsScreen(onLock = onLock, biometricEnabled = biometricEnabled, onBiometricToggle = onBiometricToggle) 
            }
        }
    }
}
