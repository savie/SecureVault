package com.securevault.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.utils.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long
)

data class PanelState(
    val currentPath: String = "/storage/emulated/0/",
    val items: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRootMode: Boolean = false,
    val message: String? = null
)

data class FileManagerState(
    val leftPanel: PanelState = PanelState(),
    val rightPanel: PanelState = PanelState()
)

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(FileManagerState())
    val state: StateFlow<FileManagerState> = _state.asStateFlow()

    private val isRootAvailable = RootUtils.isRootAvailable()
    private val homePath = "/storage/emulated/0/"
    private val forceRootPaths = listOf("/cache", "/bin", "/etc", "/sdcard", "/vendor", "/system", "/product", "/data")

    init {
        loadPanel(isLeft = true, path = homePath)
        loadPanel(isLeft = false, path = homePath)
    }

    fun loadPanel(isLeft: Boolean, path: String) {
        val cleanPath = path.replace("//", "/")
        viewModelScope.launch {
            val currentPanel = if (isLeft) _state.value.leftPanel else _state.value.rightPanel
            val newPanel = currentPanel.copy(isLoading = true, currentPath = cleanPath, message = null)
            _state.value = if (isLeft) _state.value.copy(leftPanel = newPanel) else _state.value.copy(rightPanel = newPanel)

            val items = withContext(Dispatchers.IO) {
                val list = mutableListOf<FileItem>()
                val mustUseRoot = forceRootPaths.contains(cleanPath) || isRootAvailable

                if (mustUseRoot && isRootAvailable) {
                    try {
                        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "stat -c '%n|%s|%F' \"$cleanPath\"/* 2>/dev/null"))
                        val output = process.inputStream.bufferedReader().readText()
                        output.lines().forEach { line ->
                            if (line.isNotEmpty()) {
                                val parts = line.split("|")
                                if (parts.size == 3) {
                                    val fullPath = parts[0].trim()
                                    val name = File(fullPath).name
                                    val size = parts[1].trim().toLongOrNull() ?: 0L
                                    val isDir = parts[2].contains("directory", true) || parts[2].contains("link", true)
                                    list.add(FileItem(name, fullPath, isDir, size))
                                }
                            }
                        }
                    } catch (e: Exception) { /* Abaikan */ }
                }

                if (list.isEmpty()) {
                    val dir = File(cleanPath)
                    val files = dir.listFiles()
                    if (files != null) {
                        files.forEach { list.add(FileItem(it.name, it.absolutePath, it.isDirectory, it.length())) }
                    }
                }

                list.sortWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                list
            }

            val updatedPanel = newPanel.copy(isLoading = false, items = items, isRootMode = isRootAvailable)
            _state.value = if (isLeft) _state.value.copy(leftPanel = updatedPanel) else _state.value.copy(rightPanel = updatedPanel)
        }
    }

    fun navigateUp(isLeft: Boolean): Boolean {
        val currentPanel = if (isLeft) _state.value.leftPanel else _state.value.rightPanel
        val current = currentPanel.currentPath
        
        // ---> FIX: Biarin naik terus, bahkan dari Home ---
        // Kalo udah di "/" (root system), baru balik ke Home
        if (current == "/") {
            loadPanel(isLeft, homePath)
            return true
        }
        
        // Kalo di Home (/storage/emulated/0/), naik ke /storage/emulated/
        if (current == homePath) {
            val parent = File(current).parent  // /storage/emulated/
            if (parent != null) {
                loadPanel(isLeft, parent)
                return true
            }
        }
        
        // Kalo di folder lain, naik ke parent biasa
        val parent = File(current).parent
        if (parent != null) {
            loadPanel(isLeft, parent)
            return true
        }
        
        return false
    }
}
