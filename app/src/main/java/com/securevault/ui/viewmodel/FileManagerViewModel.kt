package com.securevault.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.local.ContainerDbHelper
import com.securevault.utils.ConflictAction
import com.securevault.utils.FileOperationHelper
import com.securevault.utils.ArchiveFormat
import com.securevault.utils.ArchiveHelper
import com.securevault.utils.ArchiveResult
import com.securevault.utils.FileResult
import com.securevault.fs.FileAccessManager
import com.securevault.data.repository.VaultRepository
import com.securevault.data.repository.VaultEncryptResult
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
    val pendingConflict: PendingConflict? = null,
    val archiveMessage: String? = null
)

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(FileManagerState())
    val state: StateFlow<FileManagerState> = _state.asStateFlow()

    private val isRootAvailable = FileAccessManager.rootOperations.isAvailable()
    private val homePath = "/storage/emulated/0/"
    private val forceRootPaths = listOf("/cache", "/bin", "/etc", "/sdcard", "/vendor", "/system", "/product", "/data", "/data/data", "/data/user", "/data/user/0")

    init {
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

    fun loadPanel(isLeft: Boolean, path: String, message: String? = null) {
        val cleanPath = path.replace("//", "/")
        viewModelScope.launch {
            val currentPanel = if (isLeft) _state.value.leftPanel else _state.value.rightPanel
            val newPanel = currentPanel.copy(isLoading = true, currentPath = cleanPath, message = message)
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
                        val rootItems = FileAccessManager.rootOperations.listWithRoot(cleanPath)
                        rootItems?.forEach { info ->
                            list.add(FileItem(info.name, info.path, info.isDirectory, info.size))
                        }
                    } catch (e: Exception) { /* ignore */ }
                }

                if (list.isEmpty() || !isRootAvailable) {
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
        val targetPath = pending.targetDir.absolutePath
        val isLeftTarget = pending.isLeftTarget

        viewModelScope.launch {
            _state.value = _state.value.copy(pendingPaste = null)
            val otherPath = if (isLeftTarget) _state.value.rightPanel.currentPath else _state.value.leftPanel.currentPath
            val targetPanel = if (isLeftTarget) _state.value.leftPanel else _state.value.rightPanel
            _state.value = if (isLeftTarget) _state.value.copy(leftPanel = targetPanel.copy(isLoading = true)) else _state.value.copy(rightPanel = targetPanel.copy(isLoading = true))

            val result = withContext(Dispatchers.IO) {
                when (action) {
                    ClipboardAction.COPY -> FileOperationHelper.copy(pending.sourceFile, pending.targetDir)
                    ClipboardAction.MOVE -> FileOperationHelper.move(pending.sourceFile, pending.targetDir)
                }
            }

            val operationName = if (action == ClipboardAction.COPY) "Copy" else "Move"
            val message = when (result) {
                is FileResult.Success -> "✓ $operationName completed."
                is FileResult.Conflict -> "⚠️ Destination conflict. Choose how to continue."
                is FileResult.Failure -> "❌ $operationName failed."
            }

            if (result is FileResult.Conflict) {
                _state.value = _state.value.copy(
                    pendingConflict = PendingConflict(pending.sourceFile, pending.targetDir, action, isLeftTarget)
                )
            }

            loadPanel(isLeftTarget, targetPath, message)
            if (result is FileResult.Success) loadPanel(!isLeftTarget, otherPath)
    }
    }
    fun resolveConflict(action: ConflictAction) {
        val conflict = _state.value.pendingConflict ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(pendingConflict = null)
            val otherPath = if (conflict.isLeftTarget) _state.value.rightPanel.currentPath else _state.value.leftPanel.currentPath
            val targetPanel = if (conflict.isLeftTarget) _state.value.leftPanel else _state.value.rightPanel
            _state.value = if (conflict.isLeftTarget) _state.value.copy(leftPanel = targetPanel.copy(isLoading = true)) else _state.value.copy(rightPanel = targetPanel.copy(isLoading = true))

            val result = withContext(Dispatchers.IO) {
                when (conflict.action) {
                    ClipboardAction.COPY -> FileOperationHelper.copy(conflict.sourceFile, conflict.destDir, action)
                    ClipboardAction.MOVE -> FileOperationHelper.move(conflict.sourceFile, conflict.destDir, action)
                }
            }

            val operationName = if (conflict.action == ClipboardAction.COPY) "Copy" else "Move"
            val message = when (result) {
                is FileResult.Success -> "✓ $operationName completed."
                is FileResult.Conflict -> "⚠️ Destination conflict remains."
                is FileResult.Failure -> "❌ $operationName failed."
            }

            loadPanel(conflict.isLeftTarget, targetPanel.currentPath, message)
            if (result is FileResult.Success) loadPanel(!conflict.isLeftTarget, otherPath)
        }
    }

    fun clearConflict() { _state.value = _state.value.copy(pendingConflict = null) }

    fun deleteFile(item: FileItem, isLeft: Boolean) {
        if (isVirtualContainer(item)) {
            val panel = if (isLeft) _state.value.leftPanel else _state.value.rightPanel
            val message = "❌ Cannot delete mounted container. Lock it first from Container tab."
            _state.value = if (isLeft) _state.value.copy(leftPanel = panel.copy(message = message)) else _state.value.copy(rightPanel = panel.copy(message = message))
            return
        }
        viewModelScope.launch {
            val panel = if (isLeft) _state.value.leftPanel else _state.value.rightPanel
            _state.value = if (isLeft) _state.value.copy(leftPanel = panel.copy(isLoading = true)) else _state.value.copy(rightPanel = panel.copy(isLoading = true))
            val success = withContext(Dispatchers.IO) { FileOperationHelper.delete(File(item.path)) }
            val message = if (success) "✓ Delete completed." else "❌ Delete failed."
            loadPanel(isLeft, panel.currentPath, message)
        }
    }

    fun renameFile(item: FileItem, newName: String, isLeft: Boolean) {
        if (isVirtualContainer(item)) {
            val panel = if (isLeft) _state.value.leftPanel else _state.value.rightPanel
            val message = "❌ Cannot rename mounted container. Rename it from Container tab."
            _state.value = if (isLeft) _state.value.copy(leftPanel = panel.copy(message = message)) else _state.value.copy(rightPanel = panel.copy(message = message))
            return
        }
        viewModelScope.launch {
            val panel = if (isLeft) _state.value.leftPanel else _state.value.rightPanel
            _state.value = if (isLeft) _state.value.copy(leftPanel = panel.copy(isLoading = true)) else _state.value.copy(rightPanel = panel.copy(isLoading = true))
            val success = withContext(Dispatchers.IO) { FileOperationHelper.rename(File(item.path), newName) }
            val message = if (success) "✓ Rename completed." else "❌ Rename failed."
            loadPanel(isLeft, panel.currentPath, message)
        }
    }
    fun clearArchiveMessage() {
        _state.value = _state.value.copy(archiveMessage = null)
    }

    private fun publishArchiveMessage(message: String) {
        _state.value = _state.value.copy(archiveMessage = message)
    }

    fun compressFile(item: FileItem, isLeft: Boolean, format: ArchiveFormat) {
        if (isVirtualContainer(item)) {
            publishArchiveMessage("Mounted containers cannot be archived.")
            return
        }

        val panel = if (isLeft) _state.value.leftPanel else _state.value.rightPanel

        viewModelScope.launch {
            val loadingPanel = panel.copy(isLoading = true)
            _state.value = if (isLeft) {
                _state.value.copy(leftPanel = loadingPanel)
            } else {
                _state.value.copy(rightPanel = loadingPanel)
            }

            val result = ArchiveHelper.compress(File(item.path), format)
            val message = when (result) {
                is ArchiveResult.Success ->
                    "Archive created: ${result.output.name}"
                is ArchiveResult.Failure ->
                    "Compress failed: ${result.reason}"
            }

            loadPanel(isLeft, panel.currentPath)
            publishArchiveMessage(message)
        }
    }

    fun extractArchive(item: FileItem, isLeft: Boolean) {
        if (isVirtualContainer(item)) {
            publishArchiveMessage("Mounted containers cannot be extracted.")
            return
        }

        val panel = if (isLeft) _state.value.leftPanel else _state.value.rightPanel

        viewModelScope.launch {
            val loadingPanel = panel.copy(isLoading = true)
            _state.value = if (isLeft) {
                _state.value.copy(leftPanel = loadingPanel)
            } else {
                _state.value.copy(rightPanel = loadingPanel)
            }

            val result = ArchiveHelper.extract(File(item.path))
            val message = when (result) {
                is ArchiveResult.Success ->
                    "Extracted beside archive."
                is ArchiveResult.Failure ->
                    "Extract failed: ${result.reason}"
            }

            loadPanel(isLeft, panel.currentPath)
            publishArchiveMessage(message)
        }
    }
    fun encryptToVault(item: FileItem, isLeft: Boolean, masterPassword: String) {
        viewModelScope.launch {
            val panel = if (isLeft) _state.value.leftPanel else _state.value.rightPanel
            _state.value = if (isLeft) {
                _state.value.copy(leftPanel = panel.copy(isLoading = true))
            } else {
                _state.value.copy(rightPanel = panel.copy(isLoading = true))
            }

            val result = VaultRepository(getApplication())
                .encryptFile(item.path, masterPassword)

            val message = when (result) {
                VaultEncryptResult.SUCCESS ->
                    "Encrypted successfully. Original file removed."
                VaultEncryptResult.SOURCE_DELETE_FAILED ->
                    "Encrypted copy saved in Vault, but original could not be deleted. Original kept."
                VaultEncryptResult.FAILURE ->
                    "Encryption failed. Original file was kept."
            }

            loadPanel(isLeft, panel.currentPath)
            publishArchiveMessage(message)
        }
    }

    fun decryptFromVault(item: FileItem, isLeft: Boolean, masterPassword: String) {
        viewModelScope.launch {
            val panel = if (isLeft) _state.value.leftPanel else _state.value.rightPanel
            _state.value = if (isLeft) {
                _state.value.copy(leftPanel = panel.copy(isLoading = true))
            } else {
                _state.value.copy(rightPanel = panel.copy(isLoading = true))
            }

            val success = VaultRepository(getApplication()).decryptFile(
                item.path,
                masterPassword,
                panel.currentPath
            )

            loadPanel(isLeft, panel.currentPath)
            publishArchiveMessage(
                if (success) "Decrypted successfully."
                else "Decryption failed. The encrypted source was kept."
            )
        }
    }
}
