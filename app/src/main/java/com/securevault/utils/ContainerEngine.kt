package com.securevault.utils

import com.securevault.crypto.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ContainerEngine {
    private val MAGIC = "SVC1".toByteArray(Charsets.UTF_8)
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val HEADER_SIZE = 4 + SALT_LENGTH + IV_LENGTH // 32 bytes

    // --- COMPRESS & ENCRYPT ---
    suspend fun packFolder(sourceFolder: File, destContainer: File, password: String): Boolean = withContext(Dispatchers.IO) {
        var tempZip: File? = null
        try {
            // 1. Bikin ZIP temporary dari folder
            tempZip = File(destContainer.parentFile, "temp_${System.currentTimeMillis()}.zip")
            zipFolder(sourceFolder, tempZip)

            // 2. Baca ZIP, Enkripsi
            val zipBytes = tempZip.readBytes()
            val salt = CryptoManager.generateSalt()
            val key = CryptoManager.deriveKey(password.toCharArray(), salt)
            val encryptedData = CryptoManager.encrypt(key, zipBytes)

            // 3. Tulis ke .svct (Header + Data)
            FileOutputStream(destContainer).use { fos ->
                fos.write(MAGIC) // 4 bytes
                fos.write(salt)  // 16 bytes
                // CryptoManager.encrypt() ngasilin [IV 12 byte] + [Ciphertext]
                // Jadi kita tulis langsung semuanya
                fos.write(encryptedData)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            tempZip?.delete()
        }
    }

    // --- DECRYPT & EXTRACT ---
    suspend fun unpackContainer(sourceContainer: File, destFolder: File, password: String): Boolean = withContext(Dispatchers.IO) {
        var tempZip: File? = null
        try {
            if (!sourceContainer.exists()) return@withContext false

            val fileBytes = sourceContainer.readBytes()
            if (fileBytes.size < HEADER_SIZE) return@withContext false

            // 1. Verifikasi Magic
            val magic = fileBytes.copyOfRange(0, 4)
            if (!magic.contentEquals(MAGIC)) return@withContext false

            // 2. Extract Salt & Encrypted Data
            val salt = fileBytes.copyOfRange(4, 4 + SALT_LENGTH)
            val encryptedData = fileBytes.copyOfRange(4 + SALT_LENGTH, fileBytes.size)

            // 3. Decrypt
            val key = CryptoManager.deriveKey(password.toCharArray(), salt)
            val zipBytes = try {
                CryptoManager.decrypt(key, encryptedData)
            } catch (e: Exception) {
                // Password salah atau file corrupt
                return@withContext false 
            }

            // 4. Tulis ZIP temporary & Extract
            tempZip = File(destFolder.parentFile, "temp_extract_${System.currentTimeMillis()}.zip")
            tempZip.writeBytes(zipBytes)
            
            if (!destFolder.exists()) destFolder.mkdirs()
            unzipFolder(tempZip, destFolder)

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            tempZip?.delete()
        }
    }

    // --- HELPER: ZIP Folder ---
    private fun zipFolder(folder: File, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            folder.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val entryName = file.relativeTo(folder).path
                    zos.putNextEntry(ZipEntry(entryName))
                    FileInputStream(file).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
        }
    }

    // --- HELPER: UNZIP ---
    private fun unzipFolder(zipFile: File, destFolder: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val outFile = File(destFolder, entry.name)
                // Security check: Prevent Zip Slip
                if (!outFile.canonicalPath.startsWith(destFolder.canonicalPath + File.separator)) {
                    throw SecurityException("Zip entry outside target dir: ${entry.name}")
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
