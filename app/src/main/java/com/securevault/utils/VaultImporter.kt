package com.securevault.utils

import android.content.Context
import com.securevault.crypto.CryptoManager
import com.securevault.data.local.VaultDbHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object VaultImporter {
    // ENCRYPT: Sama persis kayak Vault (JANGAN DIUBAH)
    suspend fun encryptFile(context: Context, filePath: String, masterPassword: String): Boolean = withContext(Dispatchers.IO) {
        val sourceFile = File(filePath)
        if (!sourceFile.exists()) return@withContext false

        val originalName = sourceFile.name
        val mimeType = when {
            originalName.endsWith(".jpg", true) || originalName.endsWith(".jpeg", true) -> "image/jpeg"
            originalName.endsWith(".png", true) -> "image/png"
            originalName.endsWith(".pdf", true) -> "application/pdf"
            originalName.endsWith(".mp4", true) -> "video/mp4"
            else -> "application/octet-stream"
        }

        var originalBytes: ByteArray? = null
        try {
            originalBytes = sourceFile.readBytes()
        } catch (e: Exception) {
            if (RootUtils.isRootAvailable()) {
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat \"$filePath\""))
                    originalBytes = process.inputStream.readBytes()
                } catch (ex: Exception) { /* Gagal */ }
            }
        }
        
        if (originalBytes == null || originalBytes.isEmpty()) return@withContext false

        val salt = CryptoManager.generateSalt()
        val key = CryptoManager.deriveKey(masterPassword.toCharArray(), salt)
        val encryptedBytes = CryptoManager.encrypt(key, originalBytes)

        val uuid = UUID.randomUUID().toString()
        val vaultDir = File(context.filesDir, "vault").apply { if (!exists()) mkdirs() }
        File(vaultDir, "$uuid.svlt").writeBytes(salt + encryptedBytes)
        
        VaultDbHelper(context).insertFile(uuid, originalName, mimeType, originalBytes.size.toLong(), filePath)

        if (!sourceFile.delete()) RootUtils.deleteFileAsRoot(filePath)
        true
    }

    // DECRYPT: Pakai StorageHelper biar balik ke folder asal (kayak Vault)
    suspend fun decryptFile(context: Context, svltFilePath: String, masterPassword: String): Boolean = withContext(Dispatchers.IO) {
        val sourceFile = File(svltFilePath)
        if (!sourceFile.exists() || !sourceFile.name.endsWith(".svlt")) return@withContext false

        // Ambil UUID dari nama file .svlt
        val uuid = sourceFile.name.removeSuffix(".svlt")
        val dbHelper = VaultDbHelper(context)
        
        // Cari metadata di Database (nama asli, path asli, dll)
        val vaultItem = dbHelper.getFileByUuid(uuid)
        
        val fileBytes = sourceFile.readBytes()
        if (fileBytes.size < 16) return@withContext false
        
        val salt = fileBytes.copyOfRange(0, 16)
        val encryptedData = fileBytes.copyOfRange(16, fileBytes.size)

        val key = CryptoManager.deriveKey(masterPassword.toCharArray(), salt)
        val decryptedBytes = try { CryptoManager.decrypt(key, encryptedData) } catch (e: Exception) { return@withContext false }

        if (vaultItem != null) {
            // KALO ADA DI DATABASE: Pakai StorageHelper (balik ke path asli)
            val savedLocation = StorageHelper.saveFile(
                context, 
                vaultItem.originalName, 
                vaultItem.mimeType, 
                vaultItem.originalPath, 
                decryptedBytes
            )
            
            if (savedLocation != null) {
                sourceFile.delete() // Hapus .svlt
                dbHelper.deleteFile(uuid) // Hapus dari DB
                true
            } else false
        } else {
            // KALO FILE .SVLT DARI LUAR (nggak ada di DB): Simpan di folder yg sama
            val destFile = File(sourceFile.parent, "$uuid.bin")
            try {
                destFile.writeBytes(decryptedBytes)
                sourceFile.delete()
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
