package com.securevault.data.repository

import android.content.Context
import android.net.Uri
import com.securevault.crypto.CryptoManager
import com.securevault.data.local.VaultDbHelper
import com.securevault.data.local.VaultItem
import com.securevault.utils.FileHelper
import com.securevault.utils.RootUtils
import com.securevault.utils.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class VaultRepository(private val context: Context) {
    private val dbHelper = VaultDbHelper(context)

    fun getVaultFiles(): List<VaultItem> = dbHelper.getAllFiles()

    // Fungsi ini pake expression body (= withContext), jadi nggak boleh ada 'return' eksplisit
    suspend fun encryptAndSaveFile(masterPassword: String, sourceUri: Uri): String = withContext(Dispatchers.IO) {
        val (originalName, mimeType, absolutePath) = FileHelper.getFileInfoFromUri(context, sourceUri)
        
        val inputStream = context.contentResolver.openInputStream(sourceUri)
        val originalBytes = inputStream?.readBytes() ?: throw Exception("Failed to read file")
        inputStream.close()

        val salt = CryptoManager.generateSalt()
        val key = CryptoManager.deriveKey(masterPassword.toCharArray(), salt)
        val encryptedBytes = CryptoManager.encrypt(key, originalBytes)

        val uuid = UUID.randomUUID().toString()
        val vaultDir = File(context.filesDir, "vault").apply { if (!exists()) mkdirs() }
        File(vaultDir, "$uuid.svlt").writeBytes(salt + encryptedBytes)
        
        dbHelper.insertFile(uuid, originalName, mimeType, originalBytes.size.toLong(), absolutePath)

        // Hapus file asli
        var isDeleted = false
        try { if (context.contentResolver.delete(sourceUri, null, null) > 0) isDeleted = true } catch (e: Exception) {}
        if (!isDeleted) {
            try { if (File(absolutePath).delete()) isDeleted = true } catch (ex: Exception) {}
        }
        if (!isDeleted) RootUtils.deleteFileAsRoot(absolutePath)

        // Ini nilai yang dibalikin (tanpa keyword 'return')
        absolutePath 
    }

    suspend fun decryptAndRestoreFile(masterPassword: String, item: VaultItem): String = withContext(Dispatchers.IO) {
        val vaultFile = File(File(context.filesDir, "vault"), "${item.uuid}.svlt")
        if (!vaultFile.exists()) throw Exception("File not found in vault")

        val fileBytes = vaultFile.readBytes()
        val salt = fileBytes.copyOfRange(0, 16)
        val encryptedData = fileBytes.copyOfRange(16, fileBytes.size)

        val key = CryptoManager.deriveKey(masterPassword.toCharArray(), salt)
        val decryptedBytes = CryptoManager.decrypt(key, encryptedData)

        val savedLocation = StorageHelper.saveFile(context, item.originalName, item.mimeType, item.originalPath, decryptedBytes)
            ?: throw Exception("Failed to restore. Access denied or storage full.")

        vaultFile.delete()
        dbHelper.deleteFile(item.uuid)
        
        // Ini nilai yang dibalikin
        savedLocation 
    }
}
