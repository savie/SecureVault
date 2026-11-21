package com.securevault.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.securevault.fs.FileAccessManager
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID

object StorageHelper {

    fun saveFile(context: Context, originalName: String, mimeType: String, absolutePath: String, data: ByteArray): String? {
        if (absolutePath.isNotBlank() && File(absolutePath).isAbsolute && FileAccessManager.rootOperations.isAvailable()) {
            val tempFile = File(context.cacheDir, "temp_restore")
            tempFile.writeBytes(data)
            val success = FileAccessManager.rootOperations.copyAsRoot(tempFile.absolutePath, absolutePath)
            tempFile.delete()
            if (success) return absolutePath
        }

        if (absolutePath.isNotBlank() && File(absolutePath).isAbsolute && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            try {
                val destFile = File(absolutePath)
                destFile.parentFile?.mkdirs()
                destFile.writeBytes(data)
                return absolutePath
            } catch (e: Exception) { }
        }

        return saveViaMediaStore(context, originalName, mimeType, data)
    }

    fun saveFileFromTemp(
        context: Context,
        originalName: String,
        mimeType: String,
        absolutePath: String,
        tempFile: File,
        originalTimestamp: Long = 0L
    ): String? {
        val rootAvailable = FileAccessManager.rootOperations.isAvailable()
        val allFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Environment.isExternalStorageManager()

        val destination = File(absolutePath)


        if (!tempFile.isFile || absolutePath.isBlank()) {
            return null
        }

        if (!destination.isAbsolute) {
            return null
        }

        if (rootAvailable) {
            val rootSaved = saveWithRootVerified(tempFile, destination, originalTimestamp)
            if (rootSaved) return absolutePath
        }

        if (allFilesAccess) {
            val fileSaved = saveWithFileVerified(tempFile, destination, originalTimestamp)
            if (fileSaved) return absolutePath
        } else {
        }

        return null
    }

    private fun saveWithFileVerified(
        source: File,
        destination: File,
        originalTimestamp: Long
    ): Boolean {
        val parent = destination.parentFile
        if (parent == null) {
            return false
        }

        val token = UUID.randomUUID().toString()
        val staging = File(parent, ".${destination.name}.restore-$token.tmp")
        val backup = File(parent, ".${destination.name}.backup-$token")
        var backupCreated = false
        var destinationChanged = false
        var stage = "prepare"

        fun rollback() {
            if (destinationChanged && !destination.delete()) {
            }
            if (backupCreated && !backup.renameTo(destination)) {
            }
        }

        try {
            stage = "create_parent"
            if (!parent.exists() && !parent.mkdirs()) {
                return false
            }

            stage = "copy_to_staging"
            source.copyTo(staging, overwrite = true)

            stage = "verify_staging_content"
            if (!sameContent(source, staging)) {
                return false
            }

            stage = "set_staging_timestamp"
            if (originalTimestamp > 0L) {
                if (!staging.setLastModified(originalTimestamp)) {
                    return false
                }
                val actual = staging.lastModified()
                if (Math.abs(actual - originalTimestamp) > 1000L) {
                    return false
                }
            }

            stage = "backup_existing_destination"
            if (destination.exists()) {
                if (!destination.renameTo(backup)) {
                    return false
                }
                backupCreated = true
            }

            stage = "rename_staging_to_destination"
            if (!staging.renameTo(destination)) {
                rollback()
                return false
            }
            destinationChanged = true

            stage = "verify_final_content"
            val contentValid = sameContent(source, destination)
            if (!contentValid) {
                rollback()
                return false
            }

            stage = "verify_final_timestamp"
            val actualTimestamp = destination.lastModified()
            val timestampValid = originalTimestamp <= 0L ||
                Math.abs(actualTimestamp - originalTimestamp) <= 1000L

            if (!timestampValid) {
                rollback()
                return false
            }

            if (backupCreated && !backup.delete()) {
            }

            return true
        } catch (e: Exception) {
            if (backupCreated || destinationChanged) rollback()
            return false
        } finally {
            staging.delete()
        }
    }

    private fun saveWithRootVerified(
        source: File,
        destination: File,
        originalTimestamp: Long
    ): Boolean {
        val root = FileAccessManager.rootOperations
        val parent = destination.parent ?: return false
        val token = UUID.randomUUID().toString()
        val staging = "$parent/.${destination.name}.restore-$token.tmp"
        val backup = "$parent/.${destination.name}.backup-$token"

        if (!root.execAsRoot("mkdir -p ${shellQuote(parent)}")) return false

        val sourcePath = shellQuote(source.absolutePath)
        val destinationPath = shellQuote(destination.absolutePath)
        val stagingPath = shellQuote(staging)
        val backupPath = shellQuote(backup)

        fun cleanupStage() {
            root.execAsRoot("rm -f $stagingPath")
        }

        fun applyTimestamp(path: String): Boolean {
            if (originalTimestamp <= 0L) return true
            val seconds = originalTimestamp / 1000L
            val quotedPath = shellQuote(path)
            val touch = "touch -t $(date -d @$seconds +%Y%m%d%H%M.%S) $quotedPath"
            val verify = "test \"$(stat -c %Y $quotedPath)\" = '$seconds'"
            return root.execAsRoot(touch) && root.execAsRoot(verify)
        }

        if (!root.copyAsRoot(source.absolutePath, staging) ||
            !root.execAsRoot("cmp -s $sourcePath $stagingPath")
        ) {
            cleanupStage()
            return false
        }

        if (!applyTimestamp(staging)) {
            cleanupStage()
            return false
        }

        val hadDestination = root.execAsRoot("test -e $destinationPath")
        if (hadDestination &&
            !root.execAsRoot("mv -f $destinationPath $backupPath")
        ) {
            cleanupStage()
            return false
        }

        var destinationChanged = false

        fun rollback() {
            if (destinationChanged) root.execAsRoot("rm -f $destinationPath")
            if (hadDestination) {
                root.execAsRoot("mv -f $backupPath $destinationPath")
            }
        }

        if (!root.copyAsRoot(staging, destination.absolutePath)) {
            rollback()
            cleanupStage()
            return false
        }
        destinationChanged = true

        val contentValid = root.execAsRoot("cmp -s $sourcePath $destinationPath")
        val timestampValid = applyTimestamp(destination.absolutePath)

        if (!contentValid || !timestampValid) {
            rollback()
            cleanupStage()
            return false
        }

        // Keep a leftover backup rather than undoing a verified restore.
        if (hadDestination) root.execAsRoot("rm -f $backupPath")

        cleanupStage()
        return true
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }

    private fun sameContent(first: File, second: File): Boolean {
        if (!first.isFile || !second.isFile || first.length() != second.length()) {
            return false
        }

        return try {
            digest(first).contentEquals(digest(second))
        } catch (e: Exception) {
            false
        }
    }

    private fun digest(file: File): ByteArray {
        val messageDigest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                messageDigest.update(buffer, 0, count)
            }
        }
        return messageDigest.digest()
    }

    private fun saveViaMediaStore(context: Context, originalName: String, mimeType: String, data: ByteArray): String? {
        val folder = getFolderForMime(mimeType)
        val values = buildContentValues(originalName, mimeType, folder)
        val collection = getCollectionForMime(mimeType)
        val uri = context.contentResolver.insert(collection, values) ?: return null

        return try {
            val output = context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("MediaStore output stream unavailable")

            output.use { it.write(data) }
            "Internal Storage/$folder/$originalName"
        } catch (e: Exception) {
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (_: Exception) { }
            null
        }
    }

    private fun saveViaMediaStoreFromFile(context: Context, originalName: String, mimeType: String, tempFile: File): String? {
        val folder = getFolderForMime(mimeType)
        val values = buildContentValues(originalName, mimeType, folder)
        val collection = getCollectionForMime(mimeType)
        val uri = context.contentResolver.insert(collection, values) ?: return null

        return try {
            val output = context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("MediaStore output stream unavailable")

            output.use { outputStream ->
                FileInputStream(tempFile).use { input ->
                    input.copyTo(outputStream)
                }
            }

            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("MediaStore verification stream unavailable")

            val actualDigest = input.use { stream ->
                val messageDigest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    messageDigest.update(buffer, 0, count)
                }
                messageDigest.digest()
            }

            if (!actualDigest.contentEquals(digest(tempFile))) {
                throw IllegalStateException("MediaStore content verification failed")
            }

            "Internal Storage/$folder/$originalName"
        } catch (e: Exception) {
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (_: Exception) { }
            null
        }
    }

    fun setFileTimestamp(absolutePath: String, originalTimestamp: Long): Boolean {
        if (absolutePath.isBlank() || !File(absolutePath).isAbsolute || originalTimestamp <= 0L) {
            return false
        }

        val file = File(absolutePath)
        if (!file.isFile) return false

        try {
            if (file.setLastModified(originalTimestamp) &&
                Math.abs(file.lastModified() - originalTimestamp) <= 1000L
            ) {
                return true
            }

            val root = FileAccessManager.rootOperations
            if (root.isAvailable()) {
                val seconds = originalTimestamp / 1000L
                val path = shellQuote(absolutePath)
                val command = "touch -t $(date -d @$seconds +%Y%m%d%H%M.%S) $path"
                root.execAsRoot(command)
            }

            return file.exists() &&
                Math.abs(file.lastModified() - originalTimestamp) <= 1000L
        } catch (_: Exception) {
            return false
        }
    }

    private fun getFolderForMime(mimeType: String): String {
        return if (mimeType.startsWith("image/")) Environment.DIRECTORY_PICTURES + "/SecureVault"
        else if (mimeType.startsWith("video/")) Environment.DIRECTORY_MOVIES + "/SecureVault"
        else Environment.DIRECTORY_DOWNLOADS + "/SecureVault"
    }
    private fun buildContentValues(name: String, mime: String, folder: String): ContentValues {
        return ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            }
        }
    }

    private fun getCollectionForMime(mimeType: String): Uri {
        return if (mimeType.startsWith("image/")) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        else if (mimeType.startsWith("video/")) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Downloads.EXTERNAL_CONTENT_URI
    }
}
