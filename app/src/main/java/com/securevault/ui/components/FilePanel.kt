package com.securevault.ui.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.securevault.ui.viewmodel.FileItem
import com.securevault.ui.viewmodel.PanelState
import com.securevault.ui.viewmodel.SortOption
import com.securevault.utils.formatSize
import com.securevault.utils.getFileIcon
import com.securevault.utils.getIconColor
import java.io.File

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FilePanel(
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
