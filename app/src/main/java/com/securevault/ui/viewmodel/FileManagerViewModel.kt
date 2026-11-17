package com.securevault.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.local.ContainerDbHelper
import com.securevault.utils.ConflictAction
import com.securevault.utils.FileOperationHelper
import com.securevault.utils.FileResult
import com.securevault.utils.RootUtils
import com.securevault.fs.FileAccessManager
import com.securevault.utils.VaultImporter
import com.topjohnwu.superuser.Shell
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

enum class SortOption { NAME, SIZE }
enum class ClipboardAction { COPY, MOVE }

data class PanelState(
    val currentPath: String = "/storage/emulated/0/",
    val items: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRootMode: Boolean = false,
    val message: String? = null,
    val sortOption: SortOption = SortOption.NAME,
    val isAscending: Boolean = true
)

data class PendingPaste(
    val sourceFile: File,
    val targetDir: File,
    val action: ClipboardAction,
    val isLeftTarget: Boolean
)

data class PendingConflict(
    val sourceFile: File,
    val destDir: File,
    val action: ClipboardAction,
    val isLeftTarget: Boolean
)

data class FileManagerState(
    val leftPanel: PanelState = PanelState(),
    val rightPanel: PanelState = PanelState(),
    val pendingPaste: PendingPaste? = null,
    val pendingConflict: PendingConflict? = null
)

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(FileManagerState())
    val state: StateFlow<FileManagerState> = _state.asStateFlow()

    private val isRootAvailable = RootUtils.isRootAvailable()
    private val homePath = "/storage/emulated/0/"
    private val forceRootPaths = listOf("/cache", "/bin", "/etc", "/sdcard", "/vendor", "/system", "/product", "/data", "/data/data", "/data/user", "/data/user/0")

    init {
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10)
        )
        loadPanel(isLeft = true, path = homePath)
        loadPanel(isLeft = false, path = homePath)
    }

    private fun isVirtualContainer(item: FileItem): Boolean {
        val provider = FileAccessManager.getProvider()
        val parent = provider.parent(item.path) ?: return false
        return provider.name(parent) == "containers_temp"
    }

    fun refreshAllPanels() {
        refreshPanel(true)
        refreshPanel(false)
    }

    fun loadPanel(isLeft: Boolean, path: String) {
        val cleanPath = path.replace("//", "/")
        viewModelScope.launch {
            val currentPanel = if (isLeft) _state.value.leftPanel else _state.value.rightPanel
            val newPanel = currentPanel.copy(isLoading = true, currentPath = cleanPath, message = null)
            _state.value = if (isLeft) _state.value.copy(leftPanel = newPanel) else _state.value.copy(rightPanel = newPanel)

            val items = withContext(Dispatchers.IO) {
                val list = mutableListOf<FileItem>()
                val provider = FileAccessManager.getProvider()
                val appFilesDir = getApplication<Application>().filesDir.absolutePath
                val containersTempRoot = "$appFilesDir/containers_temp"

                if (cleanPath.startsWith("$containersTempRoot/") || cleanPath == containersTempRoot) {
                    provider.list(cleanPath).forEach {
                        list.add(FileItem(it.name, it.path, it.isDirectory, it.size))
                    }
                    return@withContext list
                }

                if (cleanPath == homePath) {
                    try {
                        val db = ContainerDbHelper(getApplication())
                        val mounted = db.getAllContainers().filter { it.isMounted }
                        mounted.forEach { c ->
                            val tempPath = "$containersTempRoot/${c.id}"
                            if (provider.exists(tempPath)) {
                                list.add(FileItem("📦 ${c.name}", tempPath, true, c.size))
                            }
                        }
                    } catch (e: Exception) { /* ignore */ }
                }

                val mustUseRoot = forceRootPaths.contains(cleanPath) || isRootAvailable
                if (mustUseRoot && isRootAvailable) {
                    try {
                        val cmd = if (cleanPath == "/data/data" || cleanPath.startsWith("/data/data/")) {
                            "/system/bin/find \"$cleanPath\" -maxdepth 1 -mindepth 1 -exec /system/bin/stat -c '%n|%s|%F' {} + 2>/dev/null"
                        } else {
                            "/system/bin/stat -c '%n|%s|%F' \"$cleanPath\"/* 2>/dev/null"
                        }
                        val result = Shell.cmd(cmd).exec()
                        result.out.forEach { line ->
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
                    } catch (e: Exception) { /* ignore */ }
                }

                if (list.isEmpty()) {
                    provider.list(cleanPath).forEach {
                        list.add(FileItem(it.name, it.path, it.isDirectory, it.size))
                    }
                }

                list
            }

            val sortedItems = sortItems(items, newPanel.sortOption, newPanel.isAscending)
            val updatedPanel = newPanel.copy(isLoading = false, items = sortedItems, isRootMode = isRootAvailable)
            _state.value = if (isLeft) _state.value.copy(leftPanel = updatedPanel) else _state.value.copy(rightPanel = updatedPanel)
        }
    }

    fun refreshPanel(isLeft: Boolean) {
        val path = if (isLeft) _state.value.leftPanel.currentPath else _state.value.rightPanel.currentPath
        loadPanel(isLeft, path)
    }

    // FIX: Stop di "/" (root system), jangan balik ke home
    fun navigateUp(isLeft: Boolean): Boolean {
        val currentPanel = if (isLeft) _state.value.leftPanel else _state.value.rightPanel
        val current = currentPanel.currentPath
        
        val appFilesDir = getApplication<Application>().filesDir.absolutePath
        if (current.startsWith("$appFilesDir/containers_temp/")) {
            loadPanel(isLeft, homePath)
            return true
        }
        
        // Kalau udah di "/", return false (biar MainActivity pindah tab ke Vault)
        if (current == "/") { 
            return false 
        }
        
        if (current == homePath) {
            val parent = File(current).parent
            if (parent != null) { loadPanel(isLeft, parent); return true }
        }
        val parent = File(current).parent
        if (parent != null) { loadPanel(isLeft, parent); return true }
        return false
    }

    fun changeSort(isLeft: Boolean, option: SortOption) {
        val currentPanel = if (isLeft) _state.value.leftPanel else _state.value.rightPanel
        val newAscending = if (currentPanel.sortOption == option) !currentPanel.isAscending else true
        val newPanel = currentPanel.copy(sortOption = option, isAscending = newAscending)
        val sortedItems = sortItems(newPanel.items, option, newAscending)
        val finalPanel = newPanel.copy(items = sortedItems)
        _state.value = if (isLeft) _state.value.copy(leftPanel = finalPanel) else _state.value.copy(rightPanel = finalPanel)
    }

    private fun sortItems(items: List<FileItem>, option: SortOption, ascending: Boolean): List<FileItem> {
        val (mounted, regular) = items.partition { isVirtualContainer(it) }
        val comparator = when (option) {
            SortOption.NAME -> compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() }
            SortOption.SIZE -> compareBy<FileItem> { !it.isDirectory }.thenBy { it.size }
        }
        val sortedRegular = if (ascending) regular.sortedWith(comparator) else regular.sortedWith(comparator.reversed())
        return mounted + sortedRegular
    }

    fun promptPaste(sourceItem: FileItem, action: ClipboardAction, isLeftSource: Boolean) {
        if (isVirtualContainer(sourceItem)) {
            val currentPanel = if (isLeftSource) _state.value.leftPanel else _state.value.rightPanel
            _state.value = if (isLeftSource) {
                _state.value.copy(leftPanel = currentPanel.copy(message = "❌ Cannot copy/move mounted container."))
            } else {
                _state.value.copy(rightPanel = currentPanel.copy(message = "❌ Cannot copy/move mounted container."))
            }
            return
        }
        val targetPanel = if (isLeftSource) _state.value.rightPanel else _state.value.leftPanel
        _state.value = _state.value.copy(
            pendingPaste = PendingPaste(
                sourceFile = File(sourceItem.path),
                targetDir = File(targetPanel.currentPath),
                action = action,
                isLeftTarget = !isLeftSource
            )
        )
    }

    fun clearPendingPaste() { _state.value = _state.value.copy(pendingPaste = null) }

    fun executePaste() {
        val pending = _state.value.pendingPaste ?: return
        val action = pending.action
        val targetPanelPath = pending.targetDir.absolutePath
        val isLeftTarget = pending.isLeftTarget

        viewModelScope.launch {
            _state.value = _state.value.copy(pendingPaste = null)
            val targetPanel = if (isLeftTarget) _state.value.leftPanel else _state.value.rightPanel
            _state.value = if (isLeftTarget) _state.value.copy(leftPanel = targetPanel.copy(isLoading = true)) else _state.value.copy(rightPanel = targetPanel.copy(isLoading = true))

            val result = withContext(Dispatchers.IO) {
                when (action) {
                    ClipboardAction.COPY -> FileOperationHelper.copy(pending.sourceFile, pending.targetDir)
                    ClipboardAction.MOVE -> FileOperationHelper.move(pending.sourceFile, pending.targetDir)
                }
            }

            when (result) {
                is FileResult.Success -> {
                    loadPanel(isLeftTarget, targetPanelPath)
                    loadPanel(!isLeftTarget, if (isLeftTarget) _state.value.rightPanel.currentPath else _state.value.leftPanel.currentPath)
                }
                is FileResult.Conflict -> {
                    _state.value = _state.value.copy(
                        pendingConflict = PendingConflict(pending.sourceFile, pending.targetDir, action, isLeftTarget)
                    )
                    loadPanel(isLeftTarget, targetPanelPath)
                }
                is FileResult.Failure -> loadPanel(isLeftTarget, targetPanelPath)
            }
        }
    }

    fun resolveConflict(action: ConflictAction) {
        val conflict = _state.value.pendingConflict ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(pendingConflict = null)
            val targetPanel = if (conflict.isLeftTarget) _state.value.leftPanel else _state.value.rightPanel
            _state.value = if (conflict.isLeftTarget) _state.value.copy(leftPanel = targetPanel.copy(isLoading = true)) else _state.value.copy(rightPanel = targetPanel.copy(isLoading = true))

            val result = withContext(Dispatchers.IO) {
                when (conflict.action) {
                    ClipboardAction.COPY -> FileOperationHelper.copy(conflict.sourceFile, conflict.destDir, action)
                    ClipboardAction.MOVE -> FileOperationHelper.move(conflict.sourceFile, conflict.destDir, action)
                }
            }

            loadPanel(conflict.isLeftTarget, targetPanel.currentPath)
            loadPanel(!conflict.isLeftTarget, if (conflict.isLeftTarget) _state.value.rightPanel.currentPath else _state.value.leftPanel.currentPath)
        }
    }

    fun clearConflict() { _state.value = _state.value.copy(pendingConflict = null) }

    fun deleteFile(item: FileItem, isLeft: Boolean) {
        if (isVirtualContainer(item)) {
            val panel = if (isLeft) _state.value.leftPanel else _state.value.rightPanel
            _state.value = if (isLeft) {
                _state.value.copy(leftPanel = panel.copy(message = "❌ Cannot delete mounted container. Lock it first from Container tab."))
            } else {
                _state.value.copy(rightPanel = panel.copy(message = "❌ Cannot delete mounted container. Lock it first from Container tab."))
            }
            return
        }
        viewModelScope.launch {
            val panel = if (isLeft) _state.value.leftPanel else _state.value.rightPanel
            _state.value = if (isLeft) _state.value.copy(leftPanel = panel.copy(isLoading = true)) else _state.value.copy(rightPanel = panel.copy(isLoading = true))
            withContext(Dispatchers.IO) { FileOperationHelper.delete(File(item.path)) }
            loadPanel(isLeft, panel.currentPath)
        }
    }

    fun renameFile(item: FileItem, newName: String, isLeft: Boolean) {
        if (isVirtualContainer(item)) {
            val panel = if (isLeft) _state.value.leftPanel else _state.value.rightPanel
            _state.value = if (isLeft) {
                _state.value.copy(leftPanel = panel.copy(message = "❌ Cannot rename mounted container. Rename it from Container tab."))
            } else {
                _state.value.copy(rightPanel = panel.copy(message = "❌ Cannot rename mounted container. Rename it from Container tab."))
            }
            return
        }
        viewModelScope.launch {
            val panel = if (isLeft) _state.value.leftPanel else _state.value.rightPanel
            _state.value = if (isLeft) _state.value.copy(leftPanel = panel.copy(isLoading = true)) else _state.value.copy(rightPanel = panel.copy(isLoading = true))
            withContext(Dispatchers.IO) { FileOperationHelper.rename(File(item.path), newName) }
            loadPanel(isLeft, panel.currentPath)
        }
    }

    fun encryptToVault(item: FileItem, isLeft: Boolean, masterPassword: String) {
        viewModelScope.launch {
            val panel = if (isLeft) _state.value.leftPanel else _state.value.rightPanel
            _state.value = if (isLeft) _state.value.copy(leftPanel = panel.copy(isLoading = true)) else _state.value.copy(rightPanel = panel.copy(isLoading = true))
            
            withContext(Dispatchers.IO) { VaultImporter.encryptFile(getApplication(), item.path, masterPassword) }
            loadPanel(isLeft, panel.currentPath)
        }
    }

    fun decryptFromVault(item: FileItem, isLeft: Boolean, masterPassword: String) {
        viewModelScope.launch {
            val panel = if (isLeft) _state.value.leftPanel else _state.value.rightPanel
            _state.value = if (isLeft) _state.value.copy(leftPanel = panel.copy(isLoading = true)) else _state.value.copy(rightPanel = panel.copy(isLoading = true))
            
            withContext(Dispatchers.IO) { VaultImporter.decryptFile(getApplication(), item.path, masterPassword, panel.currentPath) }
            loadPanel(isLeft, panel.currentPath)
        }
    }
}
