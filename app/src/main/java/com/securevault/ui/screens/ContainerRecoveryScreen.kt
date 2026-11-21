package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.securevault.data.local.ContainerItem
import com.securevault.data.repository.ContainerRepository
import kotlinx.coroutines.launch

@Composable
fun ContainerRecoveryScreen(
    masterPassword: String,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) {
        ContainerRepository(context.applicationContext)
    }
    val scope = rememberCoroutineScope()

    var pending by remember { mutableStateOf<List<ContainerItem>?>(null) }
    var passwords by remember { mutableStateOf(mapOf<String, String>()) }
    var visiblePasswords by remember { mutableStateOf(setOf<String>()) }
    var errors by remember { mutableStateOf(mapOf<String, String>()) }
    var busyIds by remember { mutableStateOf(setOf<String>()) }
    var completed by remember { mutableStateOf(false) }
    var isCheckingStartup by remember { mutableStateOf(true) }
    var startupError by remember { mutableStateOf<String?>(null) }

    fun updatePending(items: List<ContainerItem>) {
        pending = items
        if (items.isEmpty() && !completed) {
            completed = true
            onComplete()
        }
    }

    suspend fun runStartupChecks() {
        isCheckingStartup = true
        startupError = null
        pending = null

        try {
            // Storage migration must finish before recovery starts.
            val migrationSuccess =
                repository.migrateContainersToInternalStorage()

            if (!migrationSuccess) {
                startupError =
                    "Container storage migration failed. " +
                    "Your original Container files have been preserved. Retry to continue."
                isCheckingStartup = false
                return
            }

            val items = repository.getPendingRecoveries()
            pending = items

            items.filter { it.usesMasterPassword }.forEach { item ->
                busyIds = busyIds + item.id

                val success = repository.recoverContainer(
                    item,
                    masterPassword
                )

                busyIds = busyIds - item.id

                if (!success) {
                    errors = errors + (
                        item.id to
                            "Automatic recovery failed. Retry or check the Container file."
                    )
                }
            }

            updatePending(repository.getPendingRecoveries())
        } catch (e: Exception) {
            startupError =
                "Startup verification failed. Retry to continue."
            android.util.Log.e(
                "SecureVault",
                "Container startup gate failed",
                e
            )
        }

        isCheckingStartup = false
    }

    LaunchedEffect(masterPassword) {
        runStartupChecks()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Container Recovery",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "SecureVault is verifying Container storage and recovering " +
                "data left open by a previous session. Complete this process before continuing."
        )

        Spacer(Modifier.height(24.dp))

        if (isCheckingStartup) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Migrating and verifying Container storage...")
        } else if (startupError != null) {
            Text(
                startupError!!,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(Modifier.height(16.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        runStartupChecks()
                    }
                }
            ) {
                Text("Retry")
            }
        } else if (pending == null) {
            Text("Checking Container recovery...")
        } else if (pending!!.isEmpty()) {
            Text("Recovery completed.")
        } else {
            pending!!.forEach { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            item.name,
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            if (item.usesMasterPassword) {
                                "Protected by Master Password"
                            } else {
                                "Enter this Container's password"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )

                        errors[item.id]?.let { error ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                error,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        if (!item.usesMasterPassword) {
                            Spacer(Modifier.height(12.dp))

                            OutlinedTextField(
                                value = passwords[item.id] ?: "",
                                onValueChange = {
                                    passwords = passwords + (item.id to it)
                                },
                                label = { Text("Container Password") },
                                singleLine = true,
                                visualTransformation =
                                    if (item.id in visiblePasswords) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            visiblePasswords =
                                                if (item.id in visiblePasswords) {
                                                    visiblePasswords - item.id
                                                } else {
                                                    visiblePasswords + item.id
                                                }
                                        }
                                    ) {
                                        Icon(
                                            imageVector =
                                                if (item.id in visiblePasswords) {
                                                    Icons.Filled.Visibility
                                                } else {
                                                    Icons.Filled.VisibilityOff
                                                },
                                            contentDescription =
                                                if (item.id in visiblePasswords) {
                                                    "Hide password"
                                                } else {
                                                    "Show password"
                                                }
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Button(
                            enabled = item.id !in busyIds &&
                                (item.usesMasterPassword ||
                                    !passwords[item.id].isNullOrBlank()),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val password =
                                    if (item.usesMasterPassword) {
                                        masterPassword
                                    } else {
                                        passwords[item.id].orEmpty()
                                    }

                                scope.launch {
                                    busyIds = busyIds + item.id
                                    errors = errors - item.id

                                    val success = repository.recoverContainer(
                                        item,
                                        password
                                    )

                                    busyIds = busyIds - item.id

                                    if (success) {
                                        passwords = passwords - item.id
                                        visiblePasswords =
                                            visiblePasswords - item.id

                                        updatePending(
                                            repository.getPendingRecoveries()
                                        )
                                    } else {
                                        errors = errors + (
                                            item.id to
                                                "Recovery failed. Check the password and try again."
                                        )
                                    }
                                }
                            }
                        ) {
                            if (item.id in busyIds) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Recover Container")
                            }
                        }
                    }
                }
            }
        }
    }
}
