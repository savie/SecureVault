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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

class VaultRepository(private val context: Context) {
    private val dbHelper = VaultDbHelper(context)

    fun getVaultFiles(): List<VaultItem> = dbHelper.getAllFiles()

    suspend fun encryptAndSaveFile(masterPassword: String, sourceUri: Uri): String = withContext(Dispatchers.IO) {
        val (originalName, mimeType, absolutePath) = FileHelper.getFileInfoFromUri(context, sourceUri)

        val inputStream = context.contentResolver.openInputStream(sourceUri)
            ?: throw Exception("Failed to open file")

        val uuid = UUID.randomUUID().toString()
        val vaultDir = File(context.filesDir, "vault").apply { if (!exists()) mkdirs() }
        val destFile = File(vaultDir, "$uuid.svlt")

        val salt = CryptoManager.generateSalt()
        val key = CryptoManager.deriveKey(masterPassword.toCharArray(), salt)

        // Simpan timestamp asli sebelum file di-delete/overwrite
        val originalTimestamp = File(absolutePath).lastModified()

        var totalSize = 0L
        FileOutputStream(destFile).use { output ->
            output.write("SVLT".toByteArray(Charsets.UTF_8))
            output.write(salt)

            val countingInput = CountingInputStream(inputStream) { totalSize = it }
            CryptoManager.encryptStream(key, countingInput, output)
        }
        inputStream.close()

        if (totalSize == 0L) {
            destFile.delete()
            throw Exception("File is empty")
        }

        dbHelper.insertFile(uuid, originalName, mimeType, totalSize, absolutePath, originalTimestamp)

        var isDeleted = false
        try { if (context.contentResolver.delete(sourceUri, null, null) > 0) isDeleted = true } catch (e: Exception) {}
        if (!isDeleted) { try { if (File(absolutePath).delete()) isDeleted = true } catch (ex: Exception) {} }
        if (!isDeleted) RootUtils.deleteFileAsRoot(absolutePath)

        absolutePath
    }

    suspend fun decryptAndRestoreFile(masterPassword: String, item: VaultItem): String = withContext(Dispatchers.IO) {
        val vaultFile = File(File(context.filesDir, "vault"), "${item.uuid}.svlt")
        if (!vaultFile.exists()) throw Exception("File not found in vault")

        val tempFile = File(context.cacheDir, "restore_${System.currentTimeMillis()}.tmp")

        val header = ByteArray(20)
        FileInputStream(vaultFile).use { input ->
            if (input.read(header) != 20) throw Exception("Corrupt vault file")
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

        if (!success) {
            tempFile.delete()
            throw Exception("Failed to decrypt (wrong password or corrupt file)")
        }

        val savedLocation = StorageHelper.saveFileFromTemp(
            context, item.originalName, item.mimeType, item.originalPath, tempFile
        ) ?: run {
            tempFile.delete()
            throw Exception("Failed to restore. Access denied.")
        }

        // Set timestamp asli setelah file selesai ditulis
        StorageHelper.setFileTimestamp(context, item.originalPath, item.originalTimestamp)
        
        tempFile.delete()
        vaultFile.delete()
        dbHelper.deleteFile(item.uuid)
        savedLocation
    }

    suspend fun deleteVaultFile(item: VaultItem) = withContext(Dispatchers.IO) {
        val vaultFile = File(File(context.filesDir, "vault"), "${item.uuid}.svlt")
        if (vaultFile.exists()) {
            if (!vaultFile.delete()) RootUtils.deleteFileAsRoot(vaultFile.absolutePath)
        }
        dbHelper.deleteFile(item.uuid)
    }

    private class CountingInputStream(
        private val delegate: java.io.InputStream,
        private val onRead: (Long) -> Unit
    ) : java.io.InputStream() {
        private var total = 0L
        override fun read(): Int {
            val b = delegate.read()
            if (b != -1) { total++; onRead(total) }
            return b
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = delegate.read(b, off, len)
            if (n > 0) { total += n; onRead(total) }
            return n
        }
        override fun close() = delegate.close()
    }
}
