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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securevault.ui.viewmodel.FileItem
import com.securevault.ui.viewmodel.FileManagerViewModel
import com.securevault.ui.viewmodel.PanelState
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(modifier = modifier) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        File(panelState.currentPath).name.ifEmpty { "Root" },
                        color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        panelState.currentPath,
                        color = Color.Gray, style = MaterialTheme.typography.bodySmall, maxLines = 1
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = { onNavigateUp() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Up", tint = Color.White)
                }
            },
            actions = {
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
                            imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                            contentDescription = null,
                            tint = if (item.isDirectory) Color(0xFFFFC107) else Color.LightGray,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            if (!item.isDirectory) {
                                Text("${item.size / 1024} KB", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    HorizontalDivider(color = Color(0xFF2C2C2C), thickness = 0.5.dp)
                }
            }
        }
    }
}
