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

data class ExplorerItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long
)

data class ExplorerState(
    val currentPath: String = "/storage/emulated/0/",
    val items: List<ExplorerItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRootMode: Boolean = false,
    val message: String? = null
)

class FileExplorerViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(ExplorerState())
    val state: StateFlow<ExplorerState> = _state.asStateFlow()

    private val isRootAvailable = RootUtils.isRootAvailable()
    private val homePath = "/storage/emulated/0/"
    private val forceRootPaths = listOf("/cache", "/bin", "/etc", "/sdcard", "/vendor", "/system", "/product", "/data")

    init { loadDirectory(homePath) }

    fun loadDirectory(path: String) {
        val cleanPath = path.replace("//", "/")
        
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, currentPath = cleanPath, message = null)
            
            val items = withContext(Dispatchers.IO) {
                val list = mutableListOf<ExplorerItem>()
                val mustUseRoot = forceRootPaths.contains(cleanPath) || isRootAvailable
                
                if (mustUseRoot && isRootAvailable) {
                    // Pake stat (lebih stabil dari ls -l)
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
                                    
                                    list.add(ExplorerItem(name, fullPath, isDir, size))
                                }
                            }
                        }
                    } catch (e: Exception) { /* Abaikan */ }
                }
                
                // Fallback pake File biasa
                if (list.isEmpty()) {
                    val dir = File(cleanPath)
                    val files = dir.listFiles()
                    if (files != null) {
                        files.forEach { list.add(ExplorerItem(it.name, it.absolutePath, it.isDirectory, it.length())) }
                    }
                }
                
                list.sortWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                list
            }
            
            _state.value = _state.value.copy(isLoading = false, items = items, isRootMode = isRootAvailable)
        }
    }

    fun navigateUp(): Boolean {
        val current = _state.value.currentPath
        if (current == "/") return false
        val parent = File(current).parent
        if (parent != null) { loadDirectory(parent); return true }
        return false
    }

    fun deleteItem(item: ExplorerItem) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = "Deleting ${item.name}...")
            withContext(Dispatchers.IO) {
                var success = File(item.path).delete()
                if (!success && isRootAvailable) {
                    val command = if (item.isDirectory) "rm -rf \"${item.path}\"" else "rm -f \"${item.path}\""
                    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                    success = process.waitFor() == 0
                }
                if (success) {
                    _state.value = _state.value.copy(message = "✅ Deleted: ${item.name}")
                    loadDirectory(_state.value.currentPath)
                } else {
                    _state.value = _state.value.copy(message = "❌ Failed to delete ${item.name}", isLoading = false)
                }
            }
        }
    }
}
