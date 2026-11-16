package com.securevault.utils

import android.content.Context
import com.securevault.crypto.CryptoManager
import com.securevault.data.local.VaultDbHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

object VaultImporter {
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

        val originalSize = sourceFile.length()
        if (originalSize == 0L) return@withContext false

        try {
            val salt = CryptoManager.generateSalt()
            val key = CryptoManager.deriveKey(masterPassword.toCharArray(), salt)
            val uuid = UUID.randomUUID().toString()
            val vaultDir = File(context.filesDir, "vault").apply { if (!exists()) mkdirs() }
            val destFile = File(vaultDir, "$uuid.svlt")

            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    output.write("SVLT".toByteArray(Charsets.UTF_8))
                    output.write(salt)
                    CryptoManager.encryptStream(key, input, output)
                }
            }

            VaultDbHelper(context).insertFile(uuid, originalName, mimeType, originalSize, filePath)
            if (!sourceFile.delete()) RootUtils.deleteFileAsRoot(filePath)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun decryptFile(context: Context, svltFilePath: String, masterPassword: String, destDirPath: String): Boolean = withContext(Dispatchers.IO) {
        val sourceFile = File(svltFilePath)
        if (!sourceFile.exists() || !sourceFile.name.endsWith(".svlt")) return@withContext false

        val uuid = sourceFile.name.removeSuffix(".svlt")
        val dbHelper = VaultDbHelper(context)
        val vaultItem = dbHelper.getFileByUuid(uuid)
        val originalName = vaultItem?.originalName ?: "$uuid.bin"

        try {
            val header = ByteArray(20)
            FileInputStream(sourceFile).use { input ->
                if (input.read(header) != 20) return@withContext false
            }
            val magic = String(header.copyOfRange(0, 4), Charsets.UTF_8)
            if (magic != "SVLT") return@withContext false

            val salt = header.copyOfRange(4, 20)
            val key = CryptoManager.deriveKey(masterPassword.toCharArray(), salt)

            val tempFile = File(context.cacheDir, "decrypt_${System.currentTimeMillis()}.tmp")
            val success = FileInputStream(sourceFile).use { input ->
                input.skip(20)
                FileOutputStream(tempFile).use { output ->
                    CryptoManager.decryptStream(key, input, output)
                }
            }

            if (!success) {
                tempFile.delete()
                return@withContext false
            }

            val destFile = File(destDirPath, originalName)
            val saved = StorageHelper.saveFileFromTemp(
                context, originalName, vaultItem?.mimeType ?: "application/octet-stream",
                destFile.absolutePath, tempFile
            )
            tempFile.delete()

            if (saved != null) {
                sourceFile.delete()
                if (svltFilePath.contains("/vault/")) dbHelper.deleteFile(uuid)
                true
            } else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
