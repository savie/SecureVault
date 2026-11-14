package com.securevault.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object StorageHelper {

    // Ubah return jadi String? (Null = gagal, String = lokasi berhasil)
    fun saveFile(context: Context, originalName: String, mimeType: String, absolutePath: String, data: ByteArray): String? {
        // TIER 1: COBA PAKE ROOT
        if (RootUtils.isRootAvailable()) {
            val tempFile = File(context.cacheDir, "temp_restore")
            tempFile.writeBytes(data)
            val success = RootUtils.copyFileAsRoot(tempFile, absolutePath)
            tempFile.delete()
            if (success) return absolutePath // Berhasil via Root
        }

        // TIER 2: MANAGE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            try {
                val destFile = File(absolutePath)
                destFile.parentFile?.mkdirs()
                destFile.writeBytes(data)
                return absolutePath // Berhasil via All Files Access
            } catch (e: Exception) { /* Gagal, lanjut Tier 3 */ }
        }

        // TIER 3: FALLBACK MEDIASTORE (Aman dari Scoped Storage)
        return saveViaMediaStore(context, originalName, mimeType, data)
    }

    private fun saveViaMediaStore(context: Context, originalName: String, mimeType: String, data: ByteArray): String? {
        val folder = if (mimeType.startsWith("image/")) Environment.DIRECTORY_PICTURES + "/SecureVault"
        else if (mimeType.startsWith("video/")) Environment.DIRECTORY_MOVIES + "/SecureVault"
        else Environment.DIRECTORY_DOWNLOADS + "/SecureVault"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, originalName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            }
        }

        val collection = if (mimeType.startsWith("image/")) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        else if (mimeType.startsWith("video/")) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Downloads.EXTERNAL_CONTENT_URI

        val uri = context.contentResolver.insert(collection, values) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { it.write(data) }
        
        // Balikin path visual buat UI
        return "Internal Storage/$folder/$originalName"
    }
}
