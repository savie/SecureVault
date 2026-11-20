package com.securevault.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns

object FileHelper {

    fun getFileInfoFromUri(context: Context, uri: Uri): Triple<String, String, String> {
        var fileName = "UnknownFile"
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        var absolutePath = ""

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex) ?: fileName
                }
            }
        }

        try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)

                when {
                    uri.authority == "com.android.externalstorage.documents" -> {
                        val split = docId.split(":")
                        val type = split[0]
                        val path = split.getOrElse(1) { "" }

                        absolutePath = if (type.equals("primary", ignoreCase = true)) {
                            Environment.getExternalStorageDirectory().absolutePath + "/" + path
                        } else {
                            "/storage/$type/$path"
                        }
                    }

                    uri.authority == "com.android.providers.downloads.documents" -> {
                        if (docId.startsWith("raw:")) {
                            absolutePath = docId.substring(4)
                        }
                    }

                    uri.authority == "com.android.providers.media.documents" -> {
                        val parts = docId.split(":")
                        if (parts.size >= 2) {
                            val id = parts[1]
                            val collection = when (parts[0]) {
                                "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                                "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                                else -> null
                            }

                            if (collection != null) {
                                context.contentResolver.query(
                                    collection,
                                    arrayOf(MediaStore.MediaColumns.DATA),
                                    "${MediaStore.MediaColumns._ID}=?",
                                    arrayOf(id),
                                    null
                                )?.use { cursor ->
                                    if (cursor.moveToFirst()) {
                                        val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                                        if (index >= 0) {
                                            absolutePath = cursor.getString(index) ?: ""
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (absolutePath.isEmpty()) {
                context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.DATA),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        if (index >= 0) {
                            absolutePath = cursor.getString(index) ?: ""
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // URI providers may not expose a filesystem path.
        }

        return Triple(fileName, mimeType, absolutePath)
    }
}
