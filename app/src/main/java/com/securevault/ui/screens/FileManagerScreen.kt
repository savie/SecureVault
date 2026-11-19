package com.securevault.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securevault.ui.components.AppTopBar
import com.securevault.ui.components.FilePanel
import com.securevault.ui.components.MenuItem
import com.securevault.ui.components.dialogs.*
import com.securevault.ui.viewmodel.*
import com.securevault.utils.ArchiveFormat
import com.securevault.utils.ArchiveHelper
import com.securevault.utils.openFile
import java.io.File

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    masterPassword: String,
    viewModel: FileManagerViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var selectedItem by remember { mutableStateOf<FileItem?>(null) }
    var selectedIsLeft by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPropertiesDialog by remember { mutableStateOf(false) }
    var showToolsDialog by remember { mutableStateOf(false) }
    var showArchiveFormatDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.archiveMessage) {
        state.archiveMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearArchiveMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = "File Manager",
            actions = {
                IconButton(onClick = { viewModel.refreshAllPanels() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                }
            }
        )

        Row(modifier = Modifier.fillMaxSize().weight(1f)) {
            FilePanel(
                panelState = state.leftPanel,
                onNavigate = { viewModel.loadPanel(isLeft = true, path = it) },
                onNavigateUp = { viewModel.navigateUp(isLeft = true) },
                onSort = { viewModel.changeSort(isLeft = true, option = it) },
                onRefresh = { viewModel.refreshPanel(isLeft = true) },
                onItemClick = { item ->
                    if (item.isDirectory) viewModel.loadPanel(isLeft = true, path = item.path)
                    else openFile(context = context, item = item)
                },
                onItemLongClick = { item ->
                    selectedItem = item; selectedIsLeft = true; showMenu = true
                },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            HorizontalDivider(modifier = Modifier.width(2.dp).fillMaxHeight(), color = Color(0xFF2C2C2C))
            FilePanel(
                panelState = state.rightPanel,
                onNavigate = { viewModel.loadPanel(isLeft = false, path = it) },
                onNavigateUp = { viewModel.navigateUp(isLeft = false) },
                onSort = { viewModel.changeSort(isLeft = false, option = it) },
                onRefresh = { viewModel.refreshPanel(isLeft = false) },
                onItemClick = { item ->
                    if (item.isDirectory) viewModel.loadPanel(isLeft = false, path = item.path)
                    else openFile(context = context, item = item)
                },
                onItemLongClick = { item ->
                    selectedItem = item; selectedIsLeft = false; showMenu = true
                },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }

    if (state.pendingPaste != null) {
        PasteConfirmationDialog(
            sourceFileName = state.pendingPaste!!.sourceFile.name,
            targetDir = state.pendingPaste!!.targetDir.absolutePath,
            action = state.pendingPaste!!.action,
            onConfirm = { viewModel.executePaste() },
            onDismiss = { viewModel.clearPendingPaste() }
        )
    }

    if (state.pendingConflict != null) {
        ConflictDialog(
            fileName = state.pendingConflict!!.sourceFile.name,
            targetDir = state.pendingConflict!!.destDir.absolutePath,
            onResolve = { action -> viewModel.resolveConflict(action) },
            onDismiss = { viewModel.clearConflict() }
        )
    }

    if (showToolsDialog && selectedItem != null) {
        FileToolsDialog(
            fileName = selectedItem!!.name,
            isSvltFile = selectedItem!!.name.endsWith(".svlt", true),
            onEncrypt = {
                viewModel.encryptToVault(selectedItem!!, selectedIsLeft, masterPassword)
                showToolsDialog = false
            },
            onDecrypt = {
                viewModel.decryptFromVault(selectedItem!!, selectedIsLeft, masterPassword)
                showToolsDialog = false
            },
            onDismiss = { showToolsDialog = false }
        )
    }

    if (showMenu && selectedItem != null) {
        ModalBottomSheet(
            onDismissRequest = { showMenu = false },
            containerColor = Color(0xFF1E1E1E)
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text(selectedItem!!.name, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(selectedItem!!.path, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))

                MenuItem("Copy", Icons.Default.ContentCopy, Color.White) {
                    viewModel.promptPaste(selectedItem!!, ClipboardAction.COPY, selectedIsLeft)
                    showMenu = false
                }
                MenuItem("Move", Icons.Default.DriveFileMove, Color.White) {
                    viewModel.promptPaste(selectedItem!!, ClipboardAction.MOVE, selectedIsLeft)
                    showMenu = false
                }
                MenuItem("Delete", Icons.Default.Delete, Color.Red) {
                    showDeleteDialog = true
                    showMenu = false
                }
                MenuItem("Rename", Icons.Default.Edit, Color.White) {
                    showRenameDialog = true
                    showMenu = false
                }
                MenuItem("Properties", Icons.Default.Info, Color.White) {
                    showPropertiesDialog = true
                    showMenu = false
                }

                HorizontalDivider(color = Color(0xFF2C2C2C), modifier = Modifier.padding(vertical = 8.dp))

                MenuItem("Tools", Icons.Default.Build, Color.White) {
                    showToolsDialog = true
                    showMenu = false
                }
                MenuItem("Compress", Icons.Default.Archive, Color.White) {
                    showArchiveFormatDialog = true
                    showMenu = false
                }
                if (!selectedItem!!.isDirectory && ArchiveHelper.detectFormat(File(selectedItem!!.path)) != null) {
                    MenuItem("Extract", Icons.Default.Archive, Color.White) {
                        viewModel.extractArchive(selectedItem!!, selectedIsLeft)
                        showMenu = false
                    }
                }
            }
        }
    }

    if (showArchiveFormatDialog && selectedItem != null) {
        ArchiveFormatDialog(
            fileName = selectedItem!!.name,
            onSelect = { format ->
                viewModel.compressFile(selectedItem!!, selectedIsLeft, format)
                showArchiveFormatDialog = false
            },
            onDismiss = { showArchiveFormatDialog = false }
        )
    }

    if (showRenameDialog && selectedItem != null) {
        RenameFileDialog(
            currentName = selectedItem!!.name,
            onRename = { newName ->
                viewModel.renameFile(selectedItem!!, newName, selectedIsLeft)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showDeleteDialog && selectedItem != null) {
        DeleteFileDialog(
            fileName = selectedItem!!.name,
            onDelete = {
                viewModel.deleteFile(selectedItem!!, selectedIsLeft)
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    if (showPropertiesDialog && selectedItem != null) {
        FilePropertiesDialog(
            filePath = selectedItem!!.path,
            fileName = selectedItem!!.name,
            isDirectory = selectedItem!!.isDirectory,
            fileSize = selectedItem!!.size,
            onDismiss = { showPropertiesDialog = false }
        )
    }
}
