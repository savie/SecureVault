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

object ContainerEngine {
    private val MAGIC = "SVC2".toByteArray(Charsets.UTF_8)
    private const val SALT_LENGTH = 16
    private const val HEADER_SIZE = 4 + SALT_LENGTH // 20 bytes

    // === PACK: Folder -> Temp ZIP -> Encrypt -> .svct (Double-pass, memory ~1MB) ===
    suspend fun packFolder(sourceFolder: File, destContainer: File, password: String): Boolean = withContext(Dispatchers.IO) {
        var tempZip: File? = null
        try {
            // Pass 1: ZIP folder ke temp file (streaming, no OOM)
            tempZip = File(destContainer.parentFile ?: sourceFolder, "temp_pack_${System.currentTimeMillis()}.zip")
            if (tempZip.parentFile?.exists() == false) tempZip.parentFile?.mkdirs()
            zipFolder(sourceFolder, tempZip)

            // Pass 2: Encrypt temp ZIP ke .svct (streaming chunk-based)
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

    // === UNPACK: .svct -> Decrypt -> Temp ZIP -> Extract (Double-pass, memory ~1MB) ===
    suspend fun unpackContainer(sourceContainer: File, destFolder: File, password: String): Boolean = withContext(Dispatchers.IO) {
        var tempZip: File? = null
        try {
            if (!sourceContainer.exists()) return@withContext false

            // Read header: MAGIC (4B) + SALT (16B) = 20B
            val header = ByteArray(HEADER_SIZE)
            FileInputStream(sourceContainer).use { fis ->
                if (fis.read(header) != HEADER_SIZE) return@withContext false
            }

            val magic = header.copyOfRange(0, 4)
            if (!magic.contentEquals(MAGIC)) {
                // Cek apakah ini v1 (SVC1) - kalau iya, beri tanda khusus
                if (magic.contentEquals("SVC1".toByteArray(Charsets.UTF_8))) {
                    // File v1 tidak kompatibel dengan engine v2. User harus bikin ulang.
                    return@withContext false
                }
                return@withContext false
            }

            val salt = header.copyOfRange(4, 4 + SALT_LENGTH)
            val key = CryptoManager.deriveKey(password.toCharArray(), salt)

            // Pass 1: Decrypt .svct ke temp ZIP (streaming chunk-based)
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

            // Pass 2: Extract temp ZIP ke folder (streaming)
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

    // === HELPER: ZIP Folder (streaming, buffer 64KB) ===
    private fun zipFolder(folder: File, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val buffer = ByteArray(64 * 1024)
            folder.walkTopDown().filter { it.isFile }.forEach { file ->
                val entryName = file.relativeTo(folder).path
                zos.putNextEntry(ZipEntry(entryName))
                FileInputStream(file).use { input ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        zos.write(buffer, 0, read)
                    }
                }
                zos.closeEntry()
            }
        }
    }

    // === HELPER: UNZIP (streaming, buffer 64KB) ===
    private fun unzipFolder(zipFile: File, destFolder: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val outFile = File(destFolder, entry.name)
                // Zip Slip protection
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
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
