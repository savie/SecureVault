package com.securevault.fs

import java.io.File

/**
 * Implementasi default pakai java.io.File — full non-root.
 * Behavior IDENTIK dengan kode existing (FileManagerViewModel & FileOperationHelper).
 * Dipakai di semua flavor (free & pro).
 */
class StandardFileAccessProvider : FileAccessProvider {

    override fun exists(path: String): Boolean = File(path).exists()

    override fun isDirectory(path: String): Boolean = File(path).isDirectory

    override fun isFile(path: String): Boolean = File(path).isFile

    override fun canRead(path: String): Boolean = File(path).canRead()

    override fun canWrite(path: String): Boolean = File(path).canWrite()

    override fun length(path: String): Long = File(path).length()

    override fun lastModified(path: String): Long = File(path).lastModified()

    override fun setLastModified(path: String, time: Long): Boolean =
        File(path).setLastModified(time)

    override fun parent(path: String): String? = File(path).parent

    override fun name(path: String): String = File(path).name

    override fun list(path: String): List<FileInfo> {
        val files = File(path).listFiles() ?: return emptyList()
        return files.map {
            FileInfo(
                name = it.name,
                path = it.absolutePath,
                isDirectory = it.isDirectory,
                size = it.length(),
                lastModified = it.lastModified()
            )
        }
    }

    override fun mkdirs(path: String): Boolean = File(path).mkdirs()

    override fun delete(path: String): Boolean = File(path).delete()

    override fun deleteRecursively(path: String): Boolean = File(path).deleteRecursively()

    override fun rename(fromPath: String, toPath: String): Boolean =
        File(fromPath).renameTo(File(toPath))

    override fun copy(fromPath: String, toPath: String, overwrite: Boolean): Boolean {
        return try {
            File(fromPath).copyTo(File(toPath), overwrite = overwrite)
            true
        } catch (e: Exception) {
            false
        }
    }
}
