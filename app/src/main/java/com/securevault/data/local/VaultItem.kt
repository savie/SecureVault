package com.securevault.data.local

data class VaultItem(
    val uuid: String,
    val originalName: String,
    val mimeType: String,
    val fileSize: Long,
    val timestamp: Long, // Waktu file masuk ke Vault (encrypt time)
    val originalPath: String,
    val originalTimestamp: Long // Waktu file asli terakhir dimodifikasi (lastModified)
)
