package com.securevault.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class ConflictAction { REPLACE, SKIP, KEEP_BOTH }

sealed class FileResult {
    object Success : FileResult()
    object Conflict : FileResult()
    object Failure : FileResult()
}

object FileOperationHelper {

    suspend fun copy(source: File, destinationDir: File, conflictAction: ConflictAction? = null): FileResult = withContext(Dispatchers.IO) {
        var destFile = File(destinationDir, source.name)
        
        if (destFile.exists() && conflictAction == null) return@withContext FileResult.Conflict

        if (destFile.exists()) {
            when (conflictAction) {
                ConflictAction.SKIP -> return@withContext FileResult.Success
                ConflictAction.REPLACE -> destFile.deleteRecursively()
                ConflictAction.KEEP_BOTH -> {
                    val ext = source.name.substringAfterLast(".", "")
                    val baseName = source.name.substringBeforeLast(".")
                    var counter = 1
                    while (destFile.exists()) {
                        val newName = if (ext.isEmpty()) "$baseName ($counter)" else "$baseName ($counter).$ext"
                        destFile = File(destinationDir, newName)
                        counter++
                    }
                }
                null -> {}
            }
        }

        try {
            // Coba Root dulu buat preserve timestamp (cp -a)
            if (RootUtils.isRootAvailable()) {
                val cmd = "cp -a \"${source.absolutePath}\" \"${destFile.absolutePath}\""
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                if (process.waitFor() == 0) return@withContext FileResult.Success
            }

            // Fallback Non-Root: Copy manual + set timestamp
            copyWithTimestamp(source, destFile)
            return@withContext FileResult.Success
        } catch (e: Exception) {
            return@withContext FileResult.Failure
        }
    }

    suspend fun move(source: File, destinationDir: File, conflictAction: ConflictAction? = null): FileResult = withContext(Dispatchers.IO) {
        var destFile = File(destinationDir, source.name)
        
        if (destFile.exists() && conflictAction == null) return@withContext FileResult.Conflict

        if (destFile.exists()) {
            when (conflictAction) {
                ConflictAction.SKIP -> return@withContext FileResult.Success
                ConflictAction.REPLACE -> destFile.deleteRecursively()
                ConflictAction.KEEP_BOTH -> {
                    val ext = source.name.substringAfterLast(".", "")
                    val baseName = source.name.substringBeforeLast(".")
                    var counter = 1
                    while (destFile.exists()) {
                        val newName = if (ext.isEmpty()) "$baseName ($counter)" else "$baseName ($counter).$ext"
                        destFile = File(destinationDir, newName)
                        counter++
                    }
                }
                null -> {}
            }
        }

        try {
            // Coba renameTo dulu (biasanya instant & preserve timestamp)
            if (source.renameTo(destFile)) return@withContext FileResult.Success

            // Kalau gagal (beda partisi), pake Root move (mv -a)
            if (RootUtils.isRootAvailable()) {
                val cmd = "mv \"${source.absolutePath}\" \"${destFile.absolutePath}\""
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                if (process.waitFor() == 0) return@withContext FileResult.Success
            }

            // Fallback: copy + delete
            copyWithTimestamp(source, destFile)
            if (source.deleteRecursively()) return@withContext FileResult.Success
        } catch (e: Exception) {
            // ignore
        }
        return@withContext FileResult.Failure
    }

    suspend fun delete(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (file.deleteRecursively()) return@withContext true
        } catch (e: Exception) {
            if (RootUtils.isRootAvailable()) {
                val cmd = "rm -rf \"${file.absolutePath}\""
                return@withContext executeRootCommand(cmd)
            }
        }
        return@withContext false
    }

    suspend fun rename(file: File, newName: String): Boolean = withContext(Dispatchers.IO) {
        val newFile = File(file.parentFile, newName)
        try {
            if (file.renameTo(newFile)) return@withContext true
        } catch (e: Exception) {
            if (RootUtils.isRootAvailable()) {
                val cmd = "mv \"${file.absolutePath}\" \"${newFile.absolutePath}\""
                return@withContext executeRootCommand(cmd)
            }
        }
        return@withContext false
    }

    // Helper: Copy recursive + preserve timestamp (Non-Root)
    private fun copyWithTimestamp(source: File, dest: File) {
        if (source.isDirectory) {
            if (!dest.exists()) dest.mkdirs()
            source.listFiles()?.forEach { child ->
                copyWithTimestamp(child, File(dest, child.name))
            }
            dest.setLastModified(source.lastModified())
        } else {
            source.copyTo(dest, overwrite = true)
            dest.setLastModified(source.lastModified())
        }
    }

    private fun executeRootCommand(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
}
