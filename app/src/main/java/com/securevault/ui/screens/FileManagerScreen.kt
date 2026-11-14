package com.securevault.ui.screens

import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
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
import com.securevault.ui.viewmodel.FileItem
import com.securevault.ui.viewmodel.FileManagerViewModel
import com.securevault.ui.viewmodel.PanelState
import com.securevault.ui.viewmodel.SortOption
import java.io.File

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(viewModel: FileManagerViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

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
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            HorizontalDivider(
                modifier = Modifier.width(2.dp).fillMaxHeight(),
                color = Color(0xFF2C2C2C)
            )
            Panel(
                panelState = state.rightPanel,
                onNavigate = { viewModel.loadPanel(isLeft = false, path = it) },
                onNavigateUp = { viewModel.navigateUp(isLeft = false) },
                onSort = { viewModel.changeSort(isLeft = false, option = it) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Panel(
    panelState: PanelState,
    onNavigate: (String) -> Unit,
    onNavigateUp: () -> Boolean,
    onSort: (SortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showSortMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = File(panelState.currentPath).name.ifEmpty { "Root" },
                        color = Color.White, 
                        fontWeight = FontWeight.Bold, 
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = panelState.currentPath,
                        color = Color.Gray, 
                        style = MaterialTheme.typography.bodySmall, 
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = { onNavigateUp() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Up", tint = Color.White)
                }
            },
            actions = {
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = if (panelState.sortOption == SortOption.NAME) Icons.Default.SortByAlpha else Icons.Default.Sort,
                            contentDescription = "Sort", tint = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Name (${if (panelState.sortOption == SortOption.NAME && panelState.isAscending) "A-Z" else "Z-A"})", color = Color.White) },
                            onClick = { onSort(SortOption.NAME); showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Size (${if (panelState.sortOption == SortOption.SIZE && panelState.isAscending) "Small-Large" else "Large-Small"})", color = Color.White) },
                            onClick = { onSort(SortOption.SIZE); showSortMenu = false }
                        )
                    }
                }
                IconButton(onClick = { onNavigate("/storage/emulated/0/") }) {
                    Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E))
        )

        if (panelState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color.White)
        }

        panelState.message?.let {
            Text(it, color = Color.Red, modifier = Modifier.padding(8.dp))
        }

        if (panelState.items.isEmpty() && !panelState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Empty or denied", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(panelState.items) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (item.isDirectory) {
                                        onNavigate(item.path)
                                    } else {
                                        try {
                                            val ext = item.name.substringAfterLast(".", "")
                                            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(Uri.fromFile(File(item.path)), mime)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Open with"))
                                        } catch (e: Exception) { /* Abaikan */ }
                                    }
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = getFileIcon(item),
                            contentDescription = null,
                            tint = getIconColor(item),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!item.isDirectory) {
                                Text(formatSize(item.size), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    HorizontalDivider(color = Color(0xFF2C2C2C), thickness = 0.5.dp)
                }
            }
        }
    }
}

// Helper: Format Size (B, KB, MB, GB)
fun formatSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
        size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
        else -> String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
    }
}

// Helper: Ikon Spesifik
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
        else -> Icons.Default.InsertDriveFile
    }
}

// Helper: Warna Ikon
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
        else -> Color.LightGray
    }
}
