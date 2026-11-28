package com.securevault.utils

import com.securevault.fs.FileAccessManager
import com.securevault.fs.FileAccessProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID

enum class ConflictAction { REPLACE, SKIP, KEEP_BOTH }

sealed class FileResult {
    object Success : FileResult()
    object Conflict : FileResult()
    object Failure : FileResult()
}

object FileOperationHelper {

    private val provider: FileAccessProvider
        get() = FileAccessManager.getProvider()

    suspend fun copy(
        source: File,
        destinationDir: File,
        conflictAction: ConflictAction? = null
    ): FileResult = withContext(Dispatchers.IO) {
        transfer(source, destinationDir, conflictAction, false)
    }

    suspend fun move(
        source: File,
        destinationDir: File,
        conflictAction: ConflictAction? = null
    ): FileResult = withContext(Dispatchers.IO) {
        transfer(source, destinationDir, conflictAction, true)
    }

    private fun transfer(
        source: File,
        destinationDir: File,
        conflictAction: ConflictAction?,
        removeSource: Boolean
    ): FileResult {
        try {
            if (!exists(source)) return FileResult.Failure
            if (!exists(destinationDir) || !destinationDir.isDirectory) {
                return FileResult.Failure
            }

            val sourcePath = source.canonicalPath
            var destination = File(destinationDir, source.name)

            if (source.isDirectory &&
                destination.canonicalPath.startsWith(sourcePath + File.separator)
            ) {
                return FileResult.Failure
            }

            if (exists(destination)) {
                when (conflictAction) {
                    null -> return FileResult.Conflict
                    ConflictAction.SKIP -> return FileResult.Success
                    ConflictAction.REPLACE -> {
                        // Source and destination are the same file: nothing to replace.
                        if (sourcePath == destination.canonicalPath) {
                            return FileResult.Success
                        }
                    }
                    ConflictAction.KEEP_BOTH -> {
                        val extension = source.name.substringAfterLast(".", "")
                        val baseName = if (extension.isEmpty()) {
                            source.name
                        } else {
                            source.name.substringBeforeLast(".")
                        }

                        var index = 1
                        do {
                            val candidate = if (extension.isEmpty()) {
                                "$baseName ($index)"
                            } else {
                                "$baseName ($index).$extension"
                            }
                            destination = File(destinationDir, candidate)
                            index++
                        } while (exists(destination))
                    }
                }
            }

            val staging = File(
                destinationDir,
                ".${destination.name}.svlt-${UUID.randomUUID()}.tmp"
            )
            val backup = File(
                destinationDir,
                ".${destination.name}.svlt-${UUID.randomUUID()}.bak"
            )

            if (exists(staging) || exists(backup)) return FileResult.Failure

            var copied = copyTree(source, staging)
            var standardVerification = copied && verifyTree(source, staging)
            var rootCopy = false

            if (!standardVerification) {
                if (exists(staging) && !deletePath(staging)) {
                    return FileResult.Failure
                }

                copied = try {
                    FileAccessManager.rootOperations.isAvailable() &&
                        FileAccessManager.rootOperations.copyAsRoot(
                            source.absolutePath,
                            staging.absolutePath
                        )
                } catch (_: Exception) {
                    false
                }

                if (!copied) {
                    if (exists(staging)) deletePath(staging)
                    return FileResult.Failure
                }

                rootCopy = true
            }

            // Root fallback wajib diverifikasi sebelum staging dipromosikan.
            // Jika source tidak dapat diverifikasi oleh proses aplikasi,
            // operasi harus gagal secara aman.
            if (rootCopy) {
                val stagingVerified = try {
                    FileAccessManager.rootOperations.verifyCopyAsRoot(
                        source.absolutePath,
                        staging.absolutePath
                    )
                } catch (_: Exception) {
                    false
                }

                if (!stagingVerified) {
                    deletePath(staging)
                    return FileResult.Failure
                }
            }

            var backedUp = false

            if (exists(destination)) {
                if (!movePath(destination, backup)) {
                    deletePath(staging)
                    return FileResult.Failure
                }
                backedUp = true
            }

            if (!movePath(staging, destination)) {
                if (backedUp) movePath(backup, destination)
                deletePath(staging)
                return FileResult.Failure
            }

            // Semua jalur copy, termasuk root fallback, wajib diverifikasi
            // kembali setelah staging dipindahkan ke destination.
            val destinationVerified = if (rootCopy) {
                try {
                    FileAccessManager.rootOperations.verifyCopyAsRoot(
                        source.absolutePath,
                        destination.absolutePath
                    )
                } catch (_: Exception) {
                    false
                }
            } else {
                verifyTree(source, destination)
            }

            if (!destinationVerified) {
                deletePath(destination)
                if (backedUp) movePath(backup, destination)
                return FileResult.Failure
            }

            if (removeSource) {
                if (!deletePath(source)) {
                    // Jangan menghapus hasil salinan: sumber mungkin sudah
                    // terhapus sebagian dan hasil salinan menjadi recovery.
                    return FileResult.Failure
                }
            }

            if (backedUp) deletePath(backup)

            return FileResult.Success
        } catch (_: Exception) {
            return FileResult.Failure
        }
    }

    private fun copyTree(source: File, destination: File): Boolean {
        return try {
            if (!source.exists() || !source.canRead()) return false

            if (source.isDirectory) {
                if (destination.exists()) {
                    if (!destination.isDirectory) return false
                } else if (!destination.mkdirs() && !destination.isDirectory) {
                    return false
                }

                val children = source.listFiles() ?: return false

                for (child in children) {
                    if (!copyTree(child, File(destination, child.name))) {
                        return false
                    }
                }

                destination.setLastModified(source.lastModified())
                true
            } else {
                source.copyTo(destination, overwrite = false)
                destination.setLastModified(source.lastModified())
                destination.isFile &&
                    destination.length() == source.length() &&
                    sameContent(source, destination)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun verifyTree(source: File, destination: File): Boolean {
        return try {
            if (!source.exists() || !destination.exists()) return false
            if (source.isDirectory != destination.isDirectory) return false

            if (source.isDirectory) {
                val sourceChildren = source.listFiles() ?: return false
                val destinationChildren = destination.listFiles() ?: return false

                if (sourceChildren.size != destinationChildren.size) return false

                sourceChildren.all { child ->
                    verifyTree(child, File(destination, child.name))
                }
            } else {
                source.length() == destination.length() &&
                    sameContent(source, destination)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun sameContent(first: File, second: File): Boolean {
        return try {
            if (first.length() != second.length()) return false


            fun hash(file: File): ByteArray {
                val localDigest = MessageDigest.getInstance("SHA-256")
                FileInputStream(file).use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        localDigest.update(buffer, 0, count)
                    }
                }
                return localDigest.digest()
            }

            val firstHash = hash(first)
            val secondHash = hash(second)
            firstHash.contentEquals(secondHash)
        } catch (_: Exception) {
            false
        }
    }

    private fun exists(file: File): Boolean {
        return try {
            provider.exists(file.absolutePath) || file.exists()
        } catch (_: Exception) {
            file.exists()
        }
    }

    private fun movePath(source: File, destination: File): Boolean {
        return try {
            if (provider.rename(source.absolutePath, destination.absolutePath)) {
                return exists(destination) && !exists(source)
            }

            if (FileAccessManager.rootOperations.isAvailable() &&
                FileAccessManager.rootOperations.moveAsRoot(
                    source.absolutePath,
                    destination.absolutePath
                )
            ) {
                return true
            }

            false
        } catch (_: Exception) {
            false
        }
    }

    private fun deletePath(file: File): Boolean {
        return try {
            if (!exists(file)) return true

            if (provider.deleteRecursively(file.absolutePath) && !exists(file)) {
                return true
            }

            if (FileAccessManager.rootOperations.isAvailable() &&
                FileAccessManager.rootOperations.deleteAsRoot(file.absolutePath)
            ) {
                return true
            }

            !exists(file)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun delete(file: File): Boolean = withContext(Dispatchers.IO) {
        deletePath(file)
    }

    suspend fun rename(file: File, newName: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                if (!exists(file) || file.parentFile == null) return@withContext false
                if (newName.isBlank() || newName == "." || newName == "..") {
                    return@withContext false
                }
                if (newName.contains("/") || newName.contains("\\")) {
                    return@withContext false
                }

                val destination = File(file.parentFile, newName)
                if (file.canonicalPath == destination.canonicalPath) {
                    return@withContext true
                }
                if (exists(destination)) return@withContext false

                movePath(file, destination) &&
                    exists(destination) &&
                    !exists(file)
            } catch (_: Exception) {
                false
            }
        }
}
