package com.securevault

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.securevault.ui.screens.ContainerRecoveryScreen
import com.securevault.ui.screens.ContainerScreen
import com.securevault.ui.screens.FileManagerScreen
import com.securevault.ui.screens.SettingsScreen
import com.securevault.ui.screens.SetupScreen
import com.securevault.ui.screens.UnlockScreen
import com.securevault.ui.screens.VaultScreen
import com.securevault.ui.theme.SecureVaultTheme
import com.securevault.ui.viewmodel.AppEntryStage
import com.securevault.ui.viewmodel.AppEntryViewModel
import com.securevault.ui.viewmodel.FileManagerViewModel
import com.securevault.utils.ContainerAutoLockCoordinator

data class BottomNavItem(
    val title: String,
    val icon: ImageVector,
    val route: String
)

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ContainerAutoLockCoordinator.initialize(applicationContext)

        if (savedInstanceState != null) {
            ViewModelProvider(this)[AppEntryViewModel::class.java]
                .requireReauthentication()
        }

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
    val entryViewModel: AppEntryViewModel = viewModel()
    val state by entryViewModel.uiState.collectAsState()

    when (state.stage) {
        AppEntryStage.LOADING -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        AppEntryStage.SETUP -> {
            SetupScreen(
                onDone = entryViewModel::saveMasterPassword,
                isSaving = state.isBusy,
                saveError = state.error
            )
        }

        AppEntryStage.UNLOCK -> {
            val password = state.masterPassword

            if (password == null) {
                LoadingEntryScreen()
            } else {
                UnlockScreen(
                    savedPassword = password,
                    biometricEnabled = state.biometricEnabled,
                    onUnlock = entryViewModel::unlock
                )
            }
        }

        AppEntryStage.RECOVERY -> {
            val password = state.masterPassword

            if (password == null) {
                LoadingEntryScreen()
            } else {
                ContainerRecoveryScreen(
                    masterPassword = password,
                    recovery = state.recovery,
                    onRetry = entryViewModel::retryStartupRecovery,
                    onRecover = entryViewModel::recoverContainer
                )
            }
        }

        AppEntryStage.READY -> {
            val password = state.masterPassword

            if (password == null) {
                LoadingEntryScreen()
            } else {
                MainAppScreen(
                    masterPassword = password,
                    onLock = entryViewModel::lock,
                    biometricEnabled = state.biometricEnabled,
                    onBiometricToggle = entryViewModel::setBiometricEnabled
                )
            }
        }

        AppEntryStage.ERROR -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Master Password Error",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    state.error ?: "Unable to load Master Password.",
                    color = MaterialTheme.colorScheme.error
                )

                Spacer(Modifier.height(8.dp))

                Button(onClick = entryViewModel::retryMasterPasswordLoad) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun LoadingEntryScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
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
    val vaultViewModel: com.securevault.ui.viewmodel.VaultViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel()
    val fileManagerViewModel: FileManagerViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel()

    var isBottomBarVisible by remember { mutableStateOf(true) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    LaunchedEffect(currentDestination?.route) {
        isBottomBarVisible = true
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
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
                    navItems.forEach { item ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    item.icon,
                                    contentDescription = item.title
                                )
                            },
                            label = { Text(item.title) },
                            selected = currentDestination?.hierarchy?.any {
                                it.route == item.route
                            } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(
                                        navController.graph.findStartDestination().id
                                    ) {
                                        saveState = true
                                    }
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
        NavHost(
            navController = navController,
            startDestination = "vault",
            modifier = Modifier
                .padding(padding)
                .nestedScroll(nestedScrollConnection)
        ) {
            composable("vault") {
                LaunchedEffect(Unit) {
                    vaultViewModel.refreshFiles()
                }
                VaultScreen(
                    masterPassword = masterPassword,
                    onLock = onLock,
                    viewModel = vaultViewModel
                )
            }

            composable("container") {
                ContainerScreen(
                    masterPassword = masterPassword,
                    biometricGloballyEnabled = biometricEnabled
                )
            }

            composable("file_manager") {
                LaunchedEffect(Unit) {
                    fileManagerViewModel.refreshAllPanels()
                }

                BackHandler(enabled = true) {
                    val moved = fileManagerViewModel.navigateUp(isLeft = true)

                    if (!moved) {
                        navController.navigate("vault") {
                            popUpTo(
                                navController.graph.findStartDestination().id
                            ) {
                                saveState = true
                            }
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
