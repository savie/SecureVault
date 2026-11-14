package com.securevault.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File

object FileHelper {
    
    fun getFileInfoFromUri(context: Context, uri: Uri): Triple<String, String, String> {
        var fileName = "UnknownFile"
        var mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        var absolutePath = ""

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) fileName = cursor.getString(nameIndex)
            }
        }

        try {
            // 1. Coba ekstrak dari DocumentsContract (Standar File Picker / MT Manager)
            if (DocumentsContract.isDocumentUri(context, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                when {
                    // Untuk storage utama / SD Card
                    uri.authority == "com.android.externalstorage.documents" -> {
                        val split = docId.split(":")
                        val type = split[0]
                        val path = split.getOrElse(1) { "" }
                        absolutePath = if ("primary".equals(type, ignoreCase = true)) {
                            Environment.getExternalStorageDirectory().absolutePath + "/" + path
                        } else {
                            "/storage/$type/$path"
                        }
                    }
                    // Untuk Download
                    uri.authority == "com.android.providers.downloads.documents" -> {
                        if (docId.startsWith("raw:")) {
                            absolutePath = docId.substring(4)
                        } else {
                            absolutePath = Environment.getExternalStorageDirectory().absolutePath + "/Download/" + fileName
                        }
                    }
                    // Untuk Media (Galeri)
                    uri.authority == "com.android.providers.media.documents" -> {
                        val id = docId.split(":").getOrElse(1) { "" }
                        val projection = arrayOf(MediaStore.MediaColumns.RELATIVE_PATH)
                        val selection = MediaStore.Images.Media._ID + "=?"
                        context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, arrayOf(id), null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val relPath = cursor.getString(0) ?: ""
                                absolutePath = Environment.getExternalStorageDirectory().absolutePath + "/" + relPath + fileName
                            }
                        }
                    }
                }
            }
            
            // 2. Kalau gagal atau bukan Document URI (misal MT Manager pake custom provider)
            if (absolutePath.isEmpty()) {
                context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        if (dataIndex >= 0) absolutePath = cursor.getString(dataIndex) ?: ""
                    }
                }
            }
        } catch (e: Exception) {
            // Abaikan error dan lanjut ke fallback
        }

        // 3. Kalau tetep gagal, baru fallback ke Download/SecureVault
        if (absolutePath.isEmpty()) {
            absolutePath = Environment.getExternalStorageDirectory().absolutePath + "/Download/SecureVault/" + fileName
        }

        return Triple(fileName, mimeType, absolutePath)
    }
}
