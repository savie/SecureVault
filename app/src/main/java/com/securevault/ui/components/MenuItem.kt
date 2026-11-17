package com.securevault.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MenuItem(title: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, color = tint) },
        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
        modifier = Modifier.combinedClickable(onClick = onClick)
    )
}
