package com.securevault.data.repository

import android.content.Context
import android.net.Uri
import com.securevault.crypto.CryptoManager
import com.securevault.data.local.VaultDbHelper
import com.securevault.data.local.VaultItem
import com.securevault.fs.FileAccessManager
import com.securevault.utils.FileHelper
import com.securevault.utils.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class VaultRepository(private val context: Context) {
    private val dbHelper = VaultDbHelper(context)

    fun getVaultFiles(): List<VaultItem> = dbHelper.getAllFiles()

    suspend fun encryptAndSaveFile(masterPassword: String, sourceUri: Uri): String =
        withContext(Dispatchers.IO) {
            val (originalName, mimeType, absolutePath) =
                FileHelper.getFileInfoFromUri(context, sourceUri)

            val sourceStream = context.contentResolver.openInputStream(sourceUri)
                ?: throw IllegalStateException("Failed to open source file")

            val sourceFile = absolutePath.takeIf { it.isNotBlank() }?.let { File(it) }
            val originalTimestamp = sourceFile
                ?.takeIf { it.isAbsolute && it.exists() }
                ?.lastModified() ?: 0L

            val uuid = UUID.randomUUID().toString()
            val vaultDir = File(context.filesDir, "vault")
            val tempFile = File(vaultDir, "$uuid.svlt.tmp")
            val destFile = File(vaultDir, "$uuid.svlt")

            var totalSize = 0L
            var metadataSaved = false

            try {
                if (!vaultDir.exists() && !vaultDir.mkdirs()) {
                    throw IllegalStateException("Cannot create Vault directory")
                }

                val salt = CryptoManager.generateSalt()
                val key = CryptoManager.deriveKey(masterPassword.toCharArray(), salt)

                sourceStream.use { input ->
                    val countingInput = CountingInputStream(input) { totalSize = it }

                    FileOutputStream(tempFile).use { output ->
                        output.write("SVLT".toByteArray(Charsets.UTF_8))
                        output.write(salt)
                        CryptoManager.encryptStream(key, countingInput, output)
                    }
                }

                if (totalSize <= 0L) {
                    throw IllegalStateException("File is empty")
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
                            CryptoManager.verifyEncryptedStream(key, input, totalSize)
                    }
                }

                if (!verified) {
                    throw IllegalStateException("Encrypted file verification failed")
                }

                if (!tempFile.renameTo(destFile)) {
                    throw IllegalStateException("Cannot finalize encrypted file")
                }

                dbHelper.insertFile(
                    uuid,
                    originalName,
                    mimeType,
                    totalSize,
                    absolutePath,
                    originalTimestamp
                )
                metadataSaved = true
            } catch (e: Exception) {
                tempFile.delete()
                if (!metadataSaved) destFile.delete()
                throw e
            }

            if (!deleteSource(sourceUri, absolutePath)) {
                throw IllegalStateException(
                    "Encrypted copy is saved in Vault, but the original file could not be deleted. The original was kept."
                )
            }

            absolutePath
        }

    private fun deleteSource(sourceUri: Uri, absolutePath: String): Boolean {
        try {
            if (context.contentResolver.delete(sourceUri, null, null) > 0) {
                return true
            }
        } catch (_: Exception) {
            // Try a verified filesystem path when the provider cannot delete.
        }

        if (absolutePath.isBlank()) return false

        val source = File(absolutePath)
        if (!source.isAbsolute) return false
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

    suspend fun decryptAndRestoreFile(masterPassword: String, item: VaultItem): String =
        withContext(Dispatchers.IO) {
            val vaultFile = File(File(context.filesDir, "vault"), "${item.uuid}.svlt")
            if (!vaultFile.exists()) throw Exception("File not found in vault")

            val tempFile = File(context.cacheDir, "restore_${System.currentTimeMillis()}.tmp")

            try {
                val header = ByteArray(20)
                FileInputStream(vaultFile).use { input ->
                    if (!readFully(input, header)) throw Exception("Corrupt vault file")
                }

                val magic = String(header.copyOfRange(0, 4), Charsets.UTF_8)
                if (magic != "SVLT") throw Exception("Invalid vault file (v1 not supported)")

                val salt = header.copyOfRange(4, 20)
                val key = CryptoManager.deriveKey(masterPassword.toCharArray(), salt)

                val success = FileInputStream(vaultFile).use { input ->
                    input.skip(20)
                    FileOutputStream(tempFile).use { output ->
                        CryptoManager.decryptStream(key, input, output)
                    }
                }

                if (!success || tempFile.length() != item.fileSize) {
                    throw Exception("Failed to decrypt (wrong password or corrupt file)")
                }

                val savedLocation = StorageHelper.saveFileFromTemp(
                    context,
                    item.originalName,
                    item.mimeType,
                    item.originalPath,
                    tempFile
                ) ?: throw Exception("Failed to restore. Access denied.")

                StorageHelper.setFileTimestamp(
                    item.originalPath,
                    item.originalTimestamp
                )

                if (!vaultFile.delete()) {
                    throw Exception("File restored, but Vault copy could not be removed")
                }

                dbHelper.deleteFile(item.uuid)
                savedLocation
            } finally {
                tempFile.delete()
            }
        }

    suspend fun deleteVaultFile(item: VaultItem) = withContext(Dispatchers.IO) {
        val vaultFile = File(File(context.filesDir, "vault"), "${item.uuid}.svlt")

        if (vaultFile.exists()) {
            if (!vaultFile.delete() && vaultFile.exists()) {
                FileAccessManager.rootOperations.deleteAsRoot(vaultFile.absolutePath)
            }
            if (vaultFile.exists()) {
                throw IllegalStateException("Failed to delete Vault file")
            }
        }

        dbHelper.deleteFile(item.uuid)
    }

    private class CountingInputStream(
        private val delegate: InputStream,
        private val onRead: (Long) -> Unit
    ) : InputStream() {
        private var total = 0L

        override fun read(): Int {
            val value = delegate.read()
            if (value != -1) {
                total++
                onRead(total)
            }
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = delegate.read(buffer, offset, length)
            if (count > 0) {
                total += count
                onRead(total)
            }
            return count
        }

        override fun close() = delegate.close()
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
        var offset = 0

        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) return false
            if (count == 0) continue
            offset += count
        }

        return true
    }
}
