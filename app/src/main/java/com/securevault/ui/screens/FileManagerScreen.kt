package com.securevault.ui.screens

import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securevault.ui.viewmodel.*
import com.securevault.utils.ConflictAction
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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
    var renameInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("File Manager", color = Color.White, fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212))
        )

        Row(modifier = Modifier.fillMaxSize().weight(1f)) {
            Panel(
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
            Panel(
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

    // DIALOG KONFIRMASI PASTE
    if (state.pendingPaste != null) {
        val actionStr = if (state.pendingPaste!!.action == ClipboardAction.COPY) "Copy" else "Move"
        AlertDialog(
            onDismissRequest = { viewModel.clearPendingPaste() },
            title = { Text("$actionStr File", color = Color.White) },
            text = { Text("Do you want to $actionStr '${state.pendingPaste!!.sourceFile.name}' to '${state.pendingPaste!!.targetDir.absolutePath}'?", color = Color.LightGray) },
            confirmButton = { TextButton(onClick = { viewModel.executePaste() }) { Text("OK", color = Color.Green) } },
            dismissButton = { TextButton(onClick = { viewModel.clearPendingPaste() }) { Text("Cancel", color = Color.Gray) } },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    // DIALOG KONFLIK
    if (state.pendingConflict != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearConflict() },
            title = { Text("File already exists", color = Color.White) },
            text = {
                Column {
                    Text(state.pendingConflict!!.sourceFile.name, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Target: ${state.pendingConflict!!.destDir.absolutePath}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { viewModel.resolveConflict(ConflictAction.REPLACE) }) { Text("Replace", color = Color.White) }
                    TextButton(onClick = { viewModel.resolveConflict(ConflictAction.SKIP) }) { Text("Skip", color = Color.Gray) }
                    TextButton(onClick = { viewModel.resolveConflict(ConflictAction.KEEP_BOTH) }) { Text("Keep Both", color = Color.Green) }
                }
            },
            dismissButton = { TextButton(onClick = { viewModel.clearConflict() }) { Text("Cancel", color = Color.Red) } },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    // DIALOG TOOLS (Encrypt/Decrypt)
    if (showToolsDialog && selectedItem != null) {
        AlertDialog(
            onDismissRequest = { showToolsDialog = false },
            title = { Text("Tools", color = Color.White) },
            text = {
                Column {
                    Text("File: ${selectedItem!!.name}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            viewModel.encryptToVault(selectedItem!!, selectedIsLeft, masterPassword)
                            showToolsDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C), contentColor = Color.White)
                    ) { Text("Encrypt to Vault") }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.decryptFromVault(selectedItem!!, selectedIsLeft, masterPassword)
                            showToolsDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedItem!!.name.endsWith(".svlt", true),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C), contentColor = Color.Green)
                    ) { Text("Decrypt File") }
                }
            },
            confirmButton = { TextButton(onClick = { showToolsDialog = false }) { Text("Close", color = Color.White) } },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    // Bottom Sheet Menu
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
                    renameInput = selectedItem!!.name
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
                MenuItem("Compress", Icons.Default.Archive, Color.Gray) {
                    Toast.makeText(context, "Compress: Coming Soon!", Toast.LENGTH_SHORT).show()
                    showMenu = false
                }
            }
        }
    }

    // Dialog Rename, Delete, Properties (Helper functions omitted for brevity, but they are here)
    if (showRenameDialog && selectedItem != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.renameFile(selectedItem!!, renameInput, selectedIsLeft); showRenameDialog = false }) { Text("Rename", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel", color = Color.Gray) } },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    if (showDeleteDialog && selectedItem != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete", color = Color.White) },
            text = { Text("Are you sure you want to delete '${selectedItem!!.name}'?", color = Color.LightGray) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteFile(selectedItem!!, selectedIsLeft); showDeleteDialog = false }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel", color = Color.Gray) } },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    if (showPropertiesDialog && selectedItem != null) {
        val file = File(selectedItem!!.path)
        val date = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified()))
        AlertDialog(
            onDismissRequest = { showPropertiesDialog = false },
            title = { Text("Properties", color = Color.White) },
            text = {
                Column {
                    PropertyRow("Name", selectedItem!!.name)
                    PropertyRow("Path", selectedItem!!.path)
                    PropertyRow("Type", if (selectedItem!!.isDirectory) "Folder" else "File")
                    PropertyRow("Size", formatSize(selectedItem!!.size))
                    PropertyRow("Modified", date)
                }
            },
            confirmButton = { TextButton(onClick = { showPropertiesDialog = false }) { Text("Close", color = Color.White) } },
            containerColor = Color(0xFF1E1E1E)
        )
    }
}

// --- HELPER FUNCTIONS ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MenuItem(title: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, color = tint) },
        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
        modifier = Modifier.combinedClickable(onClick = onClick)
    )
}

@Composable
fun PropertyRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = Color.Gray, modifier = Modifier.width(100.dp))
        Text(value, color = Color.White)
    }
}

fun openFile(context: android.content.Context, item: FileItem) {
    try {
        val ext = item.name.substringAfterLast(".", "")
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.fromFile(File(item.path)), mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    } catch (e: Exception) { Toast.makeText(context, "No app found", Toast.LENGTH_SHORT).show() }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Panel(
    panelState: PanelState,
    onNavigate: (String) -> Unit,
    onNavigateUp: () -> Boolean,
    onSort: (SortOption) -> Unit,
    onRefresh: () -> Unit,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        TopAppBar(
            title = {
                Column {
                    Text(text = File(panelState.currentPath).name.ifEmpty { "Root" }, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = panelState.currentPath, color = Color.Gray, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            navigationIcon = { IconButton(onClick = { onNavigateUp() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Up", tint = Color.White) } },
            actions = {
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White) }
                Box {
                    IconButton(onClick = { showSortMenu = true }) { Icon(if (panelState.sortOption == SortOption.NAME) Icons.Default.SortByAlpha else Icons.Default.Sort, contentDescription = "Sort", tint = Color.White) }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        DropdownMenuItem(text = { Text("Name", color = Color.White) }, onClick = { onSort(SortOption.NAME); showSortMenu = false })
                        DropdownMenuItem(text = { Text("Size", color = Color.White) }, onClick = { onSort(SortOption.SIZE); showSortMenu = false })
                    }
                }
                IconButton(onClick = { onNavigate("/storage/emulated/0/") }) { Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E))
        )

        if (panelState.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color.White)

        if (panelState.items.isEmpty() && !panelState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Empty or denied", color = Color.Gray) }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(panelState.items) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onItemClick(item) }, onLongClick = { onItemLongClick(item) }).padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = getFileIcon(item), contentDescription = null, tint = getIconColor(item), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!item.isDirectory) Text(formatSize(item.size), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    HorizontalDivider(color = Color(0xFF2C2C2C), thickness = 0.5.dp)
                }
            }
        }
    }
}

fun formatSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
        size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
        else -> String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
    }
}

fun getFileIcon(item: FileItem): ImageVector {
    if (item.isDirectory) return Icons.Default.Folder
    val name = item.name.lowercase()
    return when {
        name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") -> Icons.Default.Image
        name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac") || name.endsWith(".m4a") -> Icons.Default.Audiotrack
        name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") -> Icons.Default.Movie
        name.endsWith(".pdf") -> Icons.Default.PictureAsPdf
        name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") -> Icons.Default.Archive
        name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".log") -> Icons.Default.Description
        name.endsWith(".apk") -> Icons.Default.Android
        name.endsWith(".sh") -> Icons.Default.Terminal
        name.endsWith(".svlt") -> Icons.Default.Lock
        else -> Icons.Default.InsertDriveFile
    }
}

fun getIconColor(item: FileItem): Color {
    if (item.isDirectory) return Color(0xFFFFC107)
    val name = item.name.lowercase()
    return when {
        name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") -> Color(0xFF4CAF50)
        name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac") || name.endsWith(".m4a") -> Color(0xFFE91E63)
        name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") -> Color(0xFF9C27B0)
        name.endsWith(".pdf") -> Color(0xFFF44336)
        name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") -> Color(0xFFFF9800)
        name.endsWith(".apk") -> Color(0xFF2196F3)
        name.endsWith(".sh") -> Color(0xFF00BCD4)
        name.endsWith(".svlt") -> Color(0xFFFFEB3B)
        else -> Color.LightGray
    }
}
