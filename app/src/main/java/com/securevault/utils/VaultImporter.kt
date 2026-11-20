package com.securevault.utils

import android.content.Context
import com.securevault.crypto.CryptoManager
import com.securevault.data.local.VaultDbHelper
import com.securevault.fs.FileAccessManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

enum class VaultImportResult {
    SUCCESS,
    SOURCE_DELETE_FAILED,
    FAILURE
}

object VaultImporter {

    suspend fun encryptFile(
        context: Context,
        filePath: String,
        masterPassword: String
    ): VaultImportResult = withContext(Dispatchers.IO) {
        val sourceFile = File(filePath)

        if (!sourceFile.isAbsolute || !sourceFile.exists() || !sourceFile.isFile) {
            return@withContext VaultImportResult.FAILURE
        }

        val originalName = sourceFile.name
        val mimeType = when {
            originalName.endsWith(".jpg", true) || originalName.endsWith(".jpeg", true) -> "image/jpeg"
            originalName.endsWith(".png", true) -> "image/png"
            originalName.endsWith(".pdf", true) -> "application/pdf"
            originalName.endsWith(".mp4", true) -> "video/mp4"
            else -> "application/octet-stream"
        }

        val originalSize = sourceFile.length()
        if (originalSize <= 0L) return@withContext VaultImportResult.FAILURE

        val originalTimestamp = sourceFile.lastModified()
        val uuid = UUID.randomUUID().toString()
        val vaultDir = File(context.filesDir, "vault")
        val tempFile = File(vaultDir, "$uuid.svlt.tmp")
        val destFile = File(vaultDir, "$uuid.svlt")

        var metadataSaved = false

        try {
            if (!vaultDir.exists() && !vaultDir.mkdirs()) {
                throw IllegalStateException("Cannot create Vault directory")
            }

            val salt = CryptoManager.generateSalt()
            val key = CryptoManager.deriveKey(masterPassword.toCharArray(), salt)

            FileInputStream(sourceFile).use { input ->
                FileOutputStream(tempFile).use { output ->
                    output.write("SVLT".toByteArray(Charsets.UTF_8))
                    output.write(salt)
                    CryptoManager.encryptStream(key, input, output)
                }
            }

            val verified = FileInputStream(tempFile).use { input ->
                val header = ByteArray(20)
                if (!readFully(input, header)) {
                    false
                } else {
                    val magic = String(header.copyOfRange(0, 4), Charsets.UTF_8)
                    val storedSalt = header.copyOfRange(4, 20)
                    magic == "SVLT" &&
                        storedSalt.contentEquals(salt) &&
                        CryptoManager.verifyEncryptedStream(key, input, originalSize)
                }
            }

            if (!verified) {
                throw IllegalStateException("Encrypted file verification failed")
            }

            if (!tempFile.renameTo(destFile)) {
                throw IllegalStateException("Cannot finalize encrypted file")
            }

            VaultDbHelper(context).insertFile(
                uuid,
                originalName,
                mimeType,
                originalSize,
                filePath,
                originalTimestamp
            )
            metadataSaved = true

            if (!deleteSource(sourceFile)) {
                return@withContext VaultImportResult.SOURCE_DELETE_FAILED
            }

            VaultImportResult.SUCCESS
        } catch (e: Exception) {
            e.printStackTrace()

            tempFile.delete()
            if (!metadataSaved) destFile.delete()

            VaultImportResult.FAILURE
        }
    }

    private fun deleteSource(source: File): Boolean {
        if (!source.exists()) return true

        if (!source.delete() && source.exists()) {
            try {
                FileAccessManager.rootOperations.deleteAsRoot(source.absolutePath)
            } catch (_: Exception) {
                // Verify actual filesystem state below.
            }
        }

        return !source.exists()
    }

    private fun readFully(input: java.io.InputStream, buffer: ByteArray): Boolean {
        var offset = 0

        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) return false
            if (count == 0) continue
            offset += count
        }

        return true
    }

    suspend fun decryptFile(
        context: Context,
        svltFilePath: String,
        masterPassword: String,
        destDirPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        val sourceFile = File(svltFilePath)
        if (!sourceFile.exists() || !sourceFile.name.endsWith(".svlt")) {
            return@withContext false
        }

        val uuid = sourceFile.name.removeSuffix(".svlt")
        val dbHelper = VaultDbHelper(context)
        val vaultItem = dbHelper.getFileByUuid(uuid)
        val originalName = vaultItem?.originalName ?: "$uuid.bin"

        try {
            val header = ByteArray(20)
            FileInputStream(sourceFile).use { input ->
                if (!readFully(input, header)) return@withContext false
            }

            val magic = String(header.copyOfRange(0, 4), Charsets.UTF_8)
            if (magic != "SVLT") return@withContext false

            val salt = header.copyOfRange(4, 20)
            val key = CryptoManager.deriveKey(masterPassword.toCharArray(), salt)

            val tempFile = File(context.cacheDir, "decrypt_${System.currentTimeMillis()}.tmp")
            try {
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

            if (vaultItem != null && tempFile.length() != vaultItem.fileSize) {
                tempFile.delete()
                return@withContext false
            }

            val destFile = File(destDirPath, originalName)
            val saved = StorageHelper.saveFileFromTemp(
                context,
                originalName,
                vaultItem?.mimeType ?: "application/octet-stream",
                destFile.absolutePath,
                tempFile
            )
            tempFile.delete()

            if (saved != null) {
                if (vaultItem != null) {
                    StorageHelper.setFileTimestamp(
                        destFile.absolutePath,
                        vaultItem.originalTimestamp
                    )
                }
                sourceFile.delete()
                if (svltFilePath.contains("/vault/")) dbHelper.deleteFile(uuid)
                true
            } else {
                false
            }
            } finally {
                tempFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
