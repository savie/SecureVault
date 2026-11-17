package com.securevault.utils

import com.securevault.crypto.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.securevault.fs.FileAccessManager

object ContainerEngine {
    private val MAGIC = "SVC2".toByteArray(Charsets.UTF_8)
    private const val SALT_LENGTH = 16
    private const val HEADER_SIZE = 4 + SALT_LENGTH // 20 bytes

    suspend fun packFolder(sourceFolder: File, destContainer: File, password: String): Boolean = withContext(Dispatchers.IO) {
        var tempZip: File? = null
        try {
            tempZip = File(destContainer.parentFile ?: sourceFolder, "temp_pack_${System.currentTimeMillis()}.zip")
            if (tempZip.parentFile?.exists() == false) tempZip.parentFile?.mkdirs()
            zipFolder(sourceFolder, tempZip)

            val salt = CryptoManager.generateSalt()
            val key = CryptoManager.deriveKey(password.toCharArray(), salt)

            FileOutputStream(destContainer).use { fos ->
                fos.write(MAGIC)
                fos.write(salt)
                FileInputStream(tempZip).use { input ->
                    CryptoManager.encryptStream(key, input, fos)
                }
            }

            tempZip.delete()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            tempZip?.delete()
            if (destContainer.exists()) destContainer.delete()
            false
        }
    }

    suspend fun unpackContainer(sourceContainer: File, destFolder: File, password: String): Boolean = withContext(Dispatchers.IO) {
        var tempZip: File? = null
        try {
            if (!sourceContainer.exists()) return@withContext false

            val header = ByteArray(HEADER_SIZE)
            FileInputStream(sourceContainer).use { fis ->
                if (fis.read(header) != HEADER_SIZE) return@withContext false
            }

            val magic = header.copyOfRange(0, 4)
            if (!magic.contentEquals(MAGIC)) return@withContext false

            val salt = header.copyOfRange(4, 4 + SALT_LENGTH)
            val key = CryptoManager.deriveKey(password.toCharArray(), salt)

            tempZip = File(destFolder.parentFile ?: destFolder, "temp_unpack_${System.currentTimeMillis()}.zip")
            if (tempZip.parentFile?.exists() == false) tempZip.parentFile?.mkdirs()

            val decryptSuccess = FileInputStream(sourceContainer).use { fis ->
                fis.skip(HEADER_SIZE.toLong())
                FileOutputStream(tempZip).use { fos ->
                    CryptoManager.decryptStream(key, fis, fos)
                }
            }

            if (!decryptSuccess) {
                tempZip.delete()
                return@withContext false
            }

            if (!destFolder.exists()) destFolder.mkdirs()
            unzipFolder(tempZip, destFolder)

            tempZip.delete()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            tempZip?.delete()
            destFolder.deleteRecursively()
            false
        }
    }

    private fun zipFolder(folder: File, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val buffer = ByteArray(64 * 1024)
            folder.walkTopDown().filter { it.isFile }.forEach { file ->
                val entryName = file.relativeTo(folder).path
                val entry = ZipEntry(entryName)
                entry.time = file.lastModified() // Preserve timestamp

                zos.putNextEntry(entry)

                // FIX: Coba FileInputStream biasa. Kalau gagal (permission/owner root), fallback ke su cat
                var inputStream: InputStream? = null
                try {
                    inputStream = FileInputStream(file)
                } catch (e: Exception) {
                    if (FileAccessManager.rootOperations.isAvailable()) {
                        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat \"" + file.absolutePath + "\""))
                        inputStream = process.inputStream
                    } else {
                        throw e
                    }
                }

                inputStream?.use { input ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        zos.write(buffer, 0, read)
                    }
                }
                zos.closeEntry()
            }
        }
    }

    private fun unzipFolder(zipFile: File, destFolder: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val outFile = File(destFolder, entry.name)
                if (!outFile.canonicalPath.startsWith(destFolder.canonicalPath + File.separator)) {
                    throw SecurityException("Zip entry outside target dir: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        zis.copyTo(fos, 64 * 1024)
                    }
                }
                outFile.setLastModified(entry.time) // Restore timestamp
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
