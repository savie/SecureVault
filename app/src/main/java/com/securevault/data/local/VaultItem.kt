package com.securevault.data.local

data class VaultItem(
    val uuid: String,
    val originalName: String,
    val mimeType: String,
    val fileSize: Long,
    val timestamp: Long,
    val originalPath: String // Path lengkap (misal: /storage/emulated/0/DCIM/Camera/foto.jpg)
)
