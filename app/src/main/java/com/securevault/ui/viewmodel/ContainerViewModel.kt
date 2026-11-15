package com.securevault.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.local.ContainerItem
import com.securevault.data.repository.ContainerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ContainerUiState(
    val isLoading: Boolean = false,
    val containers: List<ContainerItem> = emptyList(),
    val message: String? = null
)

class ContainerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ContainerRepository(application)
    private val _uiState = MutableStateFlow(ContainerUiState())
    val uiState: StateFlow<ContainerUiState> = _uiState.asStateFlow()

    init { refreshContainers() }

    fun refreshContainers() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { repository.getAllContainers() }
            _uiState.value = _uiState.value.copy(containers = list)
        }
    }

    fun createContainer(name: String, password: String, useMasterPassword: Boolean, enableBiometric: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = "Creating container...")
            val success = repository.createContainer(name, password, useMasterPassword, enableBiometric)
            if (success) {
                _uiState.value = _uiState.value.copy(isLoading = false, message = "✅ Container '$name' created successfully!")
                refreshContainers()
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, message = "❌ Failed to create container.")
            }
        }
    }

    fun deleteContainer(item: ContainerItem) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = "Deleting container...")
            val success = repository.deleteContainer(item)
            if (success) {
                _uiState.value = _uiState.value.copy(isLoading = false, message = "✅ Container deleted.")
                refreshContainers()
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, message = "❌ Failed to delete.")
            }
        }
    }
}
