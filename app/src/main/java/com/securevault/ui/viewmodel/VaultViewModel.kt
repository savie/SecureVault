package com.securevault.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.local.VaultItem
import com.securevault.data.repository.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VaultUiState(
    val isLoading: Boolean = false,
    val files: List<VaultItem> = emptyList(),
    val message: String? = null
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VaultRepository(application)
    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    init { refreshFiles() }

    fun refreshFiles() {
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) { repository.getVaultFiles() }
            _uiState.value = _uiState.value.copy(files = files)
        }
    }

    fun encryptFile(password: String, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = "Encrypting file...")
            try {
                val path = repository.encryptAndSaveFile(password, uri)
                // Tampilin path asli yang kedeteksi!
                _uiState.value = _uiState.value.copy(isLoading = false, message = "✅ Encrypted! Path: $path")
                refreshFiles()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, message = "❌ Error: ${e.message}")
            }
        }
    }

    fun restoreFile(password: String, item: VaultItem) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = "Decrypting & restoring...")
            try {
                val location = repository.decryptAndRestoreFile(password, item)
                _uiState.value = _uiState.value.copy(isLoading = false, message = "✅ Restored to: $location")
                refreshFiles()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, message = "❌ Error: ${e.message}")
            }
        }
    }
}
