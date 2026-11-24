package com.securevault.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.local.ContainerItem
import com.securevault.data.repository.ContainerRepository
import com.securevault.utils.BiometricUnlockStore
import com.securevault.utils.MasterPasswordStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppEntryStage {
    LOADING,
    SETUP,
    UNLOCK,
    RECOVERY,
    READY,
    ERROR
}

data class ContainerRecoveryUiState(
    val isCheckingStartup: Boolean = false,
    val pending: List<ContainerItem>? = null,
    val errors: Map<String, String> = emptyMap(),
    val busyIds: Set<String> = emptySet(),
    val startupError: String? = null
)

data class AppEntryUiState(
    val stage: AppEntryStage = AppEntryStage.LOADING,
    val masterPassword: String? = null,
    val biometricEnabled: Boolean = false,
    val isBusy: Boolean = false,
    val error: String? = null,
    val recovery: ContainerRecoveryUiState = ContainerRecoveryUiState()
)

class AppEntryViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val app = application.applicationContext
    private val repository = ContainerRepository(app)

    private val _uiState = MutableStateFlow(AppEntryUiState())
    val uiState: StateFlow<AppEntryUiState> = _uiState.asStateFlow()

    private var startupRecoveryJob: Job? = null
    private val manualRecoveryJobs = mutableMapOf<String, Job>()
    private var authGeneration = 0L

    init {
        loadMasterPassword()
    }

    private fun isCurrentRecovery(generation: Long): Boolean =
        authGeneration == generation &&
            _uiState.value.stage == AppEntryStage.RECOVERY

    private fun cancelRecoveryJobs() {
        startupRecoveryJob?.cancel()
        startupRecoveryJob = null

        manualRecoveryJobs.values.forEach { it.cancel() }
        manualRecoveryJobs.clear()
    }

    fun loadMasterPassword() {
        if (_uiState.value.isBusy) return

        viewModelScope.launch {
            _uiState.value = AppEntryUiState(
                stage = AppEntryStage.LOADING,
                isBusy = true
            )

            try {
                val result = withContext(Dispatchers.IO) {
                    MasterPasswordStore.loadOrMigrate(app)
                }

                when (result) {
                    is MasterPasswordStore.LoadResult.NotSet -> {
                        _uiState.value = AppEntryUiState(
                            stage = AppEntryStage.SETUP
                        )
                    }

                    is MasterPasswordStore.LoadResult.Loaded -> {
                        val biometricEnabled = withContext(Dispatchers.IO) {
                            BiometricUnlockStore.isEnrolled(app)
                        }

                        _uiState.value = AppEntryUiState(
                            stage = AppEntryStage.UNLOCK,
                            masterPassword = result.password,
                            biometricEnabled = biometricEnabled
                        )
                    }

                    is MasterPasswordStore.LoadResult.Error -> {
                        _uiState.value = AppEntryUiState(
                            stage = AppEntryStage.ERROR,
                            error = result.message
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e(
                    "SecureVault",
                    "Master Password loading failed",
                    e
                )
                _uiState.value = AppEntryUiState(
                    stage = AppEntryStage.ERROR,
                    error = "Unable to load the Master Password. Please retry."
                )
            }
        }
    }

    fun retryMasterPasswordLoad() {
        if (_uiState.value.stage == AppEntryStage.LOADING) return
        loadMasterPassword()
    }

    fun saveMasterPassword(password: String) {
        if (_uiState.value.isBusy) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isBusy = true,
                error = null
            )

            try {
                val saved = withContext(Dispatchers.IO) {
                    MasterPasswordStore.save(app, password)
                }

                _uiState.value = if (saved) {
                    AppEntryUiState(
                        stage = AppEntryStage.UNLOCK,
                        masterPassword = password
                    )
                } else {
                    _uiState.value.copy(
                        isBusy = false,
                        error = "Unable to secure the Master Password. Please try again."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e(
                    "SecureVault",
                    "Master Password saving failed",
                    e
                )
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    error = "Unable to secure the Master Password. Please try again."
                )
            }
        }
    }

    fun unlock() {
        val current = _uiState.value
        val password = current.masterPassword ?: return

        if (current.stage != AppEntryStage.UNLOCK) return

        authGeneration++
        val generation = authGeneration

        _uiState.value = current.copy(
            stage = AppEntryStage.RECOVERY,
            recovery = ContainerRecoveryUiState(
                isCheckingStartup = true
            )
        )

        runStartupChecks(password, generation)
    }

    fun lock() {
        val current = _uiState.value
        if (current.masterPassword == null) return

        authGeneration++
        cancelRecoveryJobs()

        _uiState.value = current.copy(
            stage = AppEntryStage.UNLOCK,
            recovery = ContainerRecoveryUiState()
        )
    }

    fun requireReauthentication() {
        val current = _uiState.value
        if (current.masterPassword == null) return

        if (
            current.stage == AppEntryStage.RECOVERY ||
            current.stage == AppEntryStage.READY ||
            current.stage == AppEntryStage.UNLOCK
        ) {
            authGeneration++
            cancelRecoveryJobs()

            _uiState.value = current.copy(
                stage = AppEntryStage.UNLOCK,
                recovery = ContainerRecoveryUiState()
            )
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            biometricEnabled = enabled
        )
    }

    fun retryStartupRecovery() {
        val current = _uiState.value
        val password = current.masterPassword ?: return
        if (current.stage != AppEntryStage.RECOVERY) return

        runStartupChecks(password, authGeneration)
    }

    private fun runStartupChecks(
        password: String,
        generation: Long
    ) {
        if (startupRecoveryJob?.isActive == true) return
        if (!isCurrentRecovery(generation)) return

        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            _uiState.value = _uiState.value.copy(
                recovery = ContainerRecoveryUiState(
                    isCheckingStartup = true
                )
            )

            try {
                val migrationSuccess =
                    repository.migrateContainersToInternalStorage()

                if (!isCurrentRecovery(generation)) return@launch

                if (!migrationSuccess) {
                    _uiState.value = _uiState.value.copy(
                        recovery = ContainerRecoveryUiState(
                            isCheckingStartup = false,
                            startupError =
                                "Container storage migration failed. " +
                                "Your original Container files have been preserved. Retry to continue."
                        )
                    )
                    return@launch
                }

                val items = repository.getPendingRecoveries()

                if (!isCurrentRecovery(generation)) return@launch

                _uiState.value = _uiState.value.copy(
                    recovery = ContainerRecoveryUiState(
                        isCheckingStartup = false,
                        pending = items
                    )
                )

                val errors = mutableMapOf<String, String>()

                items.filter { it.usesMasterPassword }.forEach { item ->
                    if (!isCurrentRecovery(generation)) return@launch

                    val currentRecovery = _uiState.value.recovery

                    _uiState.value = _uiState.value.copy(
                        recovery = currentRecovery.copy(
                            busyIds = currentRecovery.busyIds + item.id
                        )
                    )

                    val success = repository.recoverContainer(
                        item,
                        password
                    )

                    if (!isCurrentRecovery(generation)) return@launch

                    val updatedRecovery = _uiState.value.recovery

                    _uiState.value = _uiState.value.copy(
                        recovery = updatedRecovery.copy(
                            busyIds = updatedRecovery.busyIds - item.id
                        )
                    )

                    if (!success) {
                        errors[item.id] =
                            "Automatic recovery failed. Retry or check the Container file."
                    }
                }

                val remaining = repository.getPendingRecoveries()

                if (!isCurrentRecovery(generation)) return@launch

                if (remaining.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        stage = AppEntryStage.READY,
                        recovery = ContainerRecoveryUiState(
                            pending = emptyList()
                        )
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        stage = AppEntryStage.RECOVERY,
                        recovery = ContainerRecoveryUiState(
                            isCheckingStartup = false,
                            pending = remaining,
                            errors = errors
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e(
                    "SecureVault",
                    "Container startup gate failed",
                    e
                )

                if (isCurrentRecovery(generation)) {
                    _uiState.value = _uiState.value.copy(
                        stage = AppEntryStage.RECOVERY,
                        recovery = ContainerRecoveryUiState(
                            isCheckingStartup = false,
                            startupError =
                                "Startup verification failed. Retry to continue."
                        )
                    )
                }
            }
        }

        startupRecoveryJob = job
        job.start()
    }

    fun recoverContainer(
        item: ContainerItem,
        password: String
    ) {
        val current = _uiState.value
        val generation = authGeneration

        if (item.id in current.recovery.busyIds) return
        if (manualRecoveryJobs[item.id]?.isActive == true) return
        if (current.stage != AppEntryStage.RECOVERY) return

        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val recovery = _uiState.value.recovery

            _uiState.value = _uiState.value.copy(
                recovery = recovery.copy(
                    busyIds = recovery.busyIds + item.id,
                    errors = recovery.errors - item.id
                )
            )

            try {
                val success = repository.recoverContainer(
                    item,
                    password
                )

                if (!isCurrentRecovery(generation)) return@launch

                val remaining = if (success) {
                    repository.getPendingRecoveries()
                } else {
                    emptyList()
                }

                if (!isCurrentRecovery(generation)) return@launch

                val updated = _uiState.value.recovery
                val errors = updated.errors.toMutableMap()

                if (!success) {
                    errors[item.id] =
                        "Recovery failed. Check the password and try again."
                }

                if (success && remaining.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        stage = AppEntryStage.READY,
                        recovery = ContainerRecoveryUiState(
                            pending = emptyList()
                        )
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        recovery = updated.copy(
                            pending = if (success) remaining else updated.pending,
                            busyIds = updated.busyIds - item.id,
                            errors = errors
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e(
                    "SecureVault",
                    "Manual Container recovery failed: ${item.id}",
                    e
                )

                if (isCurrentRecovery(generation)) {
                    val updated = _uiState.value.recovery
                    _uiState.value = _uiState.value.copy(
                        recovery = updated.copy(
                            busyIds = updated.busyIds - item.id,
                            errors = updated.errors + (
                                item.id to
                                    "Recovery failed. Check the password and try again."
                                )
                        )
                    )
                }
            } finally {
                val runningJob = currentCoroutineContext()[Job]

                if (manualRecoveryJobs[item.id] === runningJob) {
                    manualRecoveryJobs.remove(item.id)
                }

                if (isCurrentRecovery(generation)) {
                    val updated = _uiState.value.recovery
                    if (item.id in updated.busyIds) {
                        _uiState.value = _uiState.value.copy(
                            recovery = updated.copy(
                                busyIds = updated.busyIds - item.id
                            )
                        )
                    }
                }
            }
        }

        manualRecoveryJobs[item.id] = job
        job.start()
    }
}
