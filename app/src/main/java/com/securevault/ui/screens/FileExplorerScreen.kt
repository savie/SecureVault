package com.securevault.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securevault.ui.viewmodel.ExplorerItem
import com.securevault.ui.viewmodel.FileExplorerViewModel
import java.io.File

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    onFileSelected: (String) -> Unit,
    viewModel: FileExplorerViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var selectedItem by remember { mutableStateOf<ExplorerItem?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    val isHome = state.currentPath == "/storage/emulated/0/"
    val currentDir = File(state.currentPath)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (isHome) "Explorer" else currentDir.name.ifEmpty { "Root" },
                            color = Color.White, fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.currentPath,
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    // Tombol Back: Panggil navigateUp() dari ViewModel
                    IconButton(onClick = {
                        val moved = viewModel.navigateUp()
                        // Jika sudah di paling atas, UI di biarkan (BackHandler di MainActivity yang urus pindah tab)
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Up", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadDirectory("/storage/emulated/0/") }) {
                        Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212))
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            
            if (isHome) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickAccessChip("DCIM") { viewModel.loadDirectory("/storage/emulated/0/DCIM") }
                    QuickAccessChip("Download") { viewModel.loadDirectory("/storage/emulated/0/Download") }
                    QuickAccessChip("Pictures") { viewModel.loadDirectory("/storage/emulated/0/Pictures") }
                    QuickAccessChip("Movies") { viewModel.loadDirectory("/storage/emulated/0/Movies") }
                }
                HorizontalDivider(color = Color(0xFF2C2C2C), thickness = 1.dp)
            }

            if (state.isRootMode) {
                Text("🔒 Root Access Active", color = Color.Yellow, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }

            if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color.White)

            state.message?.let {
                Text(it, color = if (it.startsWith("✅")) Color.Green else Color.Red, modifier = Modifier.padding(16.dp))
            }

            if (state.items.isEmpty() && !state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Folder is empty or access denied.", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.items) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (item.isDirectory) viewModel.loadDirectory(item.path)
                                        else { selectedItem = item; showMenu = true }
                                    },
                                    onLongClick = { selectedItem = item; showMenu = true }
                                )
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                contentDescription = null,
                                tint = if (item.isDirectory) Color(0xFFFFC107) else Color.LightGray
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                                if (!item.isDirectory) Text("${item.size / 1024} KB", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        HorizontalDivider(color = Color(0xFF2C2C2C), thickness = 0.5.dp)
                    }
                }
            }
        }
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
                
                ListItem(
                    headlineContent = { Text("Encrypt to Vault", color = Color.Green) },
                    leadingContent = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Green) },
                    modifier = Modifier.combinedClickable(
                        onClick = { onFileSelected(selectedItem!!.path); showMenu = false }
                    )
                )
                ListItem(
                    headlineContent = { Text("Delete", color = Color.Red) },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                    modifier = Modifier.combinedClickable(
                        onClick = { viewModel.deleteItem(selectedItem!!); showMenu = false }
                    )
                )
            }
        }
    }
}

@Composable
fun QuickAccessChip(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C), contentColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
