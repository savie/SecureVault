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

enum class VaultEncryptResult {
    SUCCESS,
    SOURCE_DELETE_FAILED,
    FAILURE
}

class VaultRepository(private val context: Context) {
    private val dbHelper = VaultDbHelper(context)

    fun getVaultFiles(): List<VaultItem> = dbHelper.getAllFiles()

    suspend fun encryptAndSaveFile(masterPassword: String, sourceUri: Uri): String =
        withContext(Dispatchers.IO) {
            val (originalName, mimeType, absolutePath) =
                FileHelper.getFileInfoFromUri(context, sourceUri)

            val input = context.contentResolver.openInputStream(sourceUri)
                ?: throw IllegalStateException("Failed to open source file")

            val sourceFile = absolutePath.takeIf { it.isNotBlank() }?.let { File(it) }
            val timestamp = sourceFile
                ?.takeIf { it.isAbsolute && it.exists() }
                ?.lastModified() ?: 0L

            val deleted = encryptSource(
                masterPassword = masterPassword,
                originalName = originalName,
                mimeType = mimeType,
                originalPath = absolutePath,
                originalTimestamp = timestamp,
                input = input
            ) {
                deleteUriSource(sourceUri, absolutePath)
            }

            if (!deleted) {
                throw IllegalStateException(
                    "Encrypted copy is saved in Vault, but the original file could not be deleted. The original was kept."
                )
            }

            absolutePath
        }

    suspend fun encryptFile(
        filePath: String,
        masterPassword: String
    ): VaultEncryptResult = withContext(Dispatchers.IO) {
        val source = File(filePath)

        if (!source.isAbsolute || !source.exists() || !source.isFile) {
            return@withContext VaultEncryptResult.FAILURE
        }

        val mimeType = when {
            source.name.endsWith(".jpg", true) ||
                source.name.endsWith(".jpeg", true) -> "image/jpeg"
            source.name.endsWith(".png", true) -> "image/png"
            source.name.endsWith(".pdf", true) -> "application/pdf"
            source.name.endsWith(".mp4", true) -> "video/mp4"
            else -> "application/octet-stream"
        }

        try {
            val deleted = encryptSource(
                masterPassword = masterPassword,
                originalName = source.name,
                mimeType = mimeType,
                originalPath = source.absolutePath,
                originalTimestamp = source.lastModified(),
                input = FileInputStream(source)
            ) {
                deleteFileSource(source)
            }

            if (deleted) VaultEncryptResult.SUCCESS
            else VaultEncryptResult.SOURCE_DELETE_FAILED
        } catch (_: Exception) {
            VaultEncryptResult.FAILURE
        }
    }

    private fun encryptSource(
        masterPassword: String,
        originalName: String,
        mimeType: String,
        originalPath: String,
        originalTimestamp: Long,
        input: InputStream,
        deleteOriginal: () -> Boolean
    ): Boolean {
        val uuid = UUID.randomUUID().toString()
        val vaultDir = File(context.filesDir, "vault")
        val tempFile = File(vaultDir, "$uuid.svlt.tmp")
        val destFile = File(vaultDir, "$uuid.svlt")
        var metadataSaved = false
        var totalSize = 0L

        try {
            if (!vaultDir.exists() && !vaultDir.mkdirs()) {
                throw IllegalStateException("Cannot create Vault directory")
            }

            val salt = CryptoManager.generateSalt()
            val key = CryptoManager.deriveKey(masterPassword.toCharArray(), salt)

            input.use { source ->
                val countingInput = CountingInputStream(source) { totalSize = it }

                FileOutputStream(tempFile).use { output ->
                    output.write("SVLT".toByteArray(Charsets.UTF_8))
                    output.write(salt)
                    CryptoManager.encryptStream(key, countingInput, output)
                }
            }

            if (totalSize <= 0L) {
                throw IllegalStateException("File is empty")
            }

            val verified = FileInputStream(tempFile).use { encrypted ->
                val header = ByteArray(20)
                readFully(encrypted, header) &&
                    String(header.copyOfRange(0, 4), Charsets.UTF_8) == "SVLT" &&
                    header.copyOfRange(4, 20).contentEquals(salt) &&
                    CryptoManager.verifyEncryptedStream(key, encrypted, totalSize)
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
                originalPath,
                originalTimestamp
            )
            metadataSaved = true

            return deleteOriginal()
        } catch (error: Exception) {
            tempFile.delete()
            if (!metadataSaved) destFile.delete()
            throw error
        }
    }

    private fun deleteUriSource(uri: Uri, absolutePath: String): Boolean {
        try {
            if (context.contentResolver.delete(uri, null, null) > 0) return true
        } catch (_: Exception) {
            // Fall back to an available filesystem path.
        }

        if (absolutePath.isBlank()) return false
        val source = File(absolutePath)
        if (!source.isAbsolute) return false
        return deleteFileSource(source)
    }

    private fun deleteFileSource(source: File): Boolean {
        if (!source.exists()) return true

        if (!source.delete() && source.exists()) {
            try {
                FileAccessManager.rootOperations.deleteAsRoot(source.absolutePath)
            } catch (_: Exception) {
                // Verify actual state below.
            }
        }

        return !source.exists()
    }

    suspend fun decryptAndRestoreFile(
        masterPassword: String,
        item: VaultItem
    ): String = withContext(Dispatchers.IO) {
        val vaultFile = File(File(context.filesDir, "vault"), "${item.uuid}.svlt")
        restoreEncryptedFile(vaultFile, masterPassword, item, null)
            ?: throw IllegalStateException("Failed to restore to the original location. Vault source was kept.")
    }

    suspend fun decryptFile(
        svltFilePath: String,
        masterPassword: String,
        destDirPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            restoreEncryptedFile(File(svltFilePath), masterPassword, null, destDirPath) != null
        } catch (_: Exception) {
            false
        }
    }

    private fun restoreEncryptedFile(
        sourceFile: File,
        masterPassword: String,
        suppliedItem: VaultItem?,
        fallbackDirectory: String?
    ): String? {

        if (!sourceFile.isAbsolute || !sourceFile.isFile ||
            !sourceFile.name.endsWith(".svlt", true)
        ) {
            return null
        }

        val uuid = sourceFile.name.removeSuffix(".svlt")
        val item = suppliedItem ?: dbHelper.getFileByUuid(uuid)
        val originalName = item?.originalName ?: "$uuid.bin"
        val mimeType = item?.mimeType ?: "application/octet-stream"
        val expectedSize = item?.fileSize ?: -1L


        val destinationPath = if (item != null) {
            item.originalPath
        } else {
            if (fallbackDirectory.isNullOrBlank() || !File(fallbackDirectory).isAbsolute) {
                return null
            }
            File(fallbackDirectory, originalName).absolutePath
        }

        if (destinationPath.isBlank() || !File(destinationPath).isAbsolute) {
            return null
        }

        val destination = File(destinationPath)

        var tempFile: File? = null
        try {
            val restoreTemp = createRestoreTempFile()
            tempFile = restoreTemp

            decryptToTemp(sourceFile, masterPassword, expectedSize, restoreTemp)


            val savedLocation = StorageHelper.saveFileFromTemp(
                context,
                originalName,
                mimeType,
                destinationPath,
                restoreTemp,
                item?.originalTimestamp ?: 0L
            )

            if (savedLocation == null) {
                return null
            }


            if (!deleteFileSource(sourceFile)) {
                return null
            }


            val vaultDirectory = File(context.filesDir, "vault")
            if (item != null &&
                sourceFile.parentFile?.canonicalFile == vaultDirectory.canonicalFile
            ) {
                dbHelper.deleteFile(uuid)
            }

            return savedLocation
        } catch (e: Exception) {
            return null
        } finally {
            tempFile?.delete()
        }
    }

    private fun decryptToTemp(
        sourceFile: File,
        masterPassword: String,
        expectedSize: Long,
        tempFile: File
    ) {
        val header = ByteArray(20)
        FileInputStream(sourceFile).use { input ->
            if (!readFully(input, header)) {
                throw IllegalStateException("Corrupt vault file")
            }
        }

        if (String(header.copyOfRange(0, 4), Charsets.UTF_8) != "SVLT") {
            throw IllegalStateException("Invalid vault file (unsupported format)")
        }

        val salt = header.copyOfRange(4, 20)
        val key = CryptoManager.deriveKey(masterPassword.toCharArray(), salt)

        val success = FileInputStream(sourceFile).use { input ->
            skipFully(input, 20L)
            FileOutputStream(tempFile).use { output ->
                CryptoManager.decryptStream(key, input, output)
            }
        }

        if (!success || tempFile.length() <= 0L ||
            (expectedSize >= 0L && tempFile.length() != expectedSize)
        ) {
            throw IllegalStateException("Failed to decrypt (wrong password or corrupt file)")
        }
    }

    private fun createRestoreTempFile(): File =
        File(context.cacheDir, "restore_${UUID.randomUUID()}.tmp")

    suspend fun deleteVaultFile(item: VaultItem) = withContext(Dispatchers.IO) {
        val vaultFile = File(File(context.filesDir, "vault"), "${item.uuid}.svlt")

        if (vaultFile.exists() && !deleteFileSource(vaultFile)) {
            throw IllegalStateException("Failed to delete Vault file")
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

    private fun skipFully(input: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped <= 0L) {
                if (input.read() < 0) throw IllegalStateException("Corrupt vault file")
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }
}
