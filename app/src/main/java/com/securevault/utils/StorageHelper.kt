package com.securevault.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream

object StorageHelper {

    // Existing (untuk file kecil, backward compat)
    fun saveFile(context: Context, originalName: String, mimeType: String, absolutePath: String, data: ByteArray): String? {
        if (RootUtils.isRootAvailable()) {
            val tempFile = File(context.cacheDir, "temp_restore")
            tempFile.writeBytes(data)
            val success = RootUtils.copyFileAsRoot(tempFile, absolutePath)
            tempFile.delete()
            if (success) return absolutePath
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            try {
                val destFile = File(absolutePath)
                destFile.parentFile?.mkdirs()
                destFile.writeBytes(data)
                return absolutePath
            } catch (e: Exception) { }
        }

        return saveViaMediaStore(context, originalName, mimeType, data)
    }

    // NEW: Streaming from temp file (untuk file besar, anti-OOM)
    fun saveFileFromTemp(context: Context, originalName: String, mimeType: String, absolutePath: String, tempFile: File): String? {
        // Tier 1: Root copy (streaming via 'cp')
        if (RootUtils.isRootAvailable()) {
            val success = RootUtils.copyFileAsRoot(tempFile, absolutePath)
            if (success) return absolutePath
        }

        // Tier 2: All Files Access (direct copy, streaming via File.copyTo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            try {
                val destFile = File(absolutePath)
                destFile.parentFile?.mkdirs()
                tempFile.copyTo(destFile, overwrite = true)
                return absolutePath
            } catch (e: Exception) { }
        }

        // Tier 3: MediaStore (streaming via input/output streams)
        return saveViaMediaStoreFromFile(context, originalName, mimeType, tempFile)
    }

    private fun saveViaMediaStore(context: Context, originalName: String, mimeType: String, data: ByteArray): String? {
        val folder = getFolderForMime(mimeType)
        val values = buildContentValues(originalName, mimeType, folder)
        val collection = getCollectionForMime(mimeType)
        val uri = context.contentResolver.insert(collection, values) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { it.write(data) }
        return "Internal Storage/$folder/$originalName"
    }

    private fun saveViaMediaStoreFromFile(context: Context, originalName: String, mimeType: String, tempFile: File): String? {
        val folder = getFolderForMime(mimeType)
        val values = buildContentValues(originalName, mimeType, folder)
        val collection = getCollectionForMime(mimeType)
        val uri = context.contentResolver.insert(collection, values) ?: return null

        context.contentResolver.openOutputStream(uri)?.use { output ->
            FileInputStream(tempFile).use { input ->
                input.copyTo(output)
            }
        }
        return "Internal Storage/$folder/$originalName"
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

    private fun getCollectionForMime(mimeType: String): android.net.Uri {
        return if (mimeType.startsWith("image/")) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        else if (mimeType.startsWith("video/")) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Downloads.EXTERNAL_CONTENT_URI
    }
}
