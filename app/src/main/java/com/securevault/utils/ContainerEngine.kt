package com.securevault.utils

import android.system.Os
import com.securevault.crypto.CryptoManager
import com.securevault.fs.FileAccessManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ContainerEngine {
    private val MAGIC = "SVC2".toByteArray(Charsets.UTF_8)
    private const val SALT_LENGTH = 16
    private const val HEADER_SIZE = 4 + SALT_LENGTH

    suspend fun packFolder(
        sourceFolder: File,
        destContainer: File,
        password: String
    ): Boolean = withContext(Dispatchers.IO) {
        val parent = destContainer.parentFile ?: return@withContext false

        if (!sourceFolder.isDirectory) return@withContext false
        if (!parent.exists() && !parent.mkdirs()) return@withContext false

        var tempZip: File? = null
        var encryptedTemp: File? = null
        var verifyZip: File? = null

        try {
            tempZip = File.createTempFile("svct-source-", ".zip", parent)
            encryptedTemp = File.createTempFile("svct-encrypted-", ".tmp", parent)
            verifyZip = File.createTempFile("svct-verify-", ".zip", parent)

            zipFolder(sourceFolder, tempZip)

            if (!tempZip.isFile || tempZip.length() <= 0L) {
                return@withContext false
            }

            val salt = CryptoManager.generateSalt()
            val key = CryptoManager.deriveKey(password.toCharArray(), salt)

            FileOutputStream(encryptedTemp).use { output ->
                output.write(MAGIC)
                output.write(salt)

                FileInputStream(tempZip).use { input ->
                    CryptoManager.encryptStream(key, input, output)
                }

                output.fd.sync()
            }

            // Verify the complete encrypted temporary file before replacement.
            val verified = FileInputStream(encryptedTemp).use { input ->
                val header = ByteArray(HEADER_SIZE)

                if (!readFully(input, header)) {
                    false
                } else {
                    val magic = header.copyOfRange(0, MAGIC.size)
                    val storedSalt = header.copyOfRange(MAGIC.size, HEADER_SIZE)

                    if (!magic.contentEquals(MAGIC)) {
                        false
                    } else {
                        val verifyKey = CryptoManager.deriveKey(
                            password.toCharArray(),
                            storedSalt
                        )

                        FileOutputStream(verifyZip).use { output ->
                            CryptoManager.decryptStream(verifyKey, input, output)
                        }
                    }
                }
            }

            if (!verified || !verifyZip.isFile) {
                return@withContext false
            }

            val sourceHash = sha256(tempZip)
            val verifiedHash = sha256(verifyZip)

            if (!sourceHash.contentEquals(verifiedHash)) {
                return@withContext false
            }

            if (tempZip.length() != verifyZip.length()) {
                return@withContext false
            }

            // Same-directory rename: atomically replaces the old destination.
            // The original remains untouched if verification or rename fails.
            Os.rename(
                encryptedTemp.absolutePath,
                destContainer.absolutePath
            )

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            tempZip?.delete()
            encryptedTemp?.delete()
            verifyZip?.delete()
        }
    }

    suspend fun unpackContainer(
        sourceContainer: File,
        destFolder: File,
        password: String
    ): Boolean = withContext(Dispatchers.IO) {
        var tempZip: File? = null

        try {
            if (!sourceContainer.isFile) return@withContext false

            val header = ByteArray(HEADER_SIZE)

            FileInputStream(sourceContainer).use { input ->
                if (!readFully(input, header)) {
                    return@withContext false
                }
            }

            val magic = header.copyOfRange(0, MAGIC.size)
            if (!magic.contentEquals(MAGIC)) {
                return@withContext false
            }

            val salt = header.copyOfRange(MAGIC.size, HEADER_SIZE)
            val key = CryptoManager.deriveKey(password.toCharArray(), salt)

            val parent = destFolder.parentFile ?: destFolder
            if (!parent.exists() && !parent.mkdirs()) {
                return@withContext false
            }

            tempZip = File.createTempFile("svct-unpack-", ".zip", parent)

            val decryptSuccess = FileInputStream(sourceContainer).use { input ->
                var skipped = 0L

                while (skipped < HEADER_SIZE) {
                    val count = input.skip(HEADER_SIZE - skipped)
                    if (count <= 0L) return@use false
                    skipped += count
                }

                FileOutputStream(tempZip).use { output ->
                    CryptoManager.decryptStream(key, input, output)
                }
            }

            if (!decryptSuccess) {
                return@withContext false
            }

            if (!destFolder.exists() && !destFolder.mkdirs()) {
                return@withContext false
            }

            unzipFolder(tempZip, destFolder)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            destFolder.deleteRecursively()
            false
        } finally {
            tempZip?.delete()
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
        var offset = 0

        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) return false
            if (count == 0) continue
            offset += count
        }

        return true
    }

    private fun sha256(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")

        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)

            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }

        return digest.digest()
    }

    private fun zipFolder(folder: File, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val buffer = ByteArray(64 * 1024)

            folder.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val entryName = file.relativeTo(folder).path
                    val entry = ZipEntry(entryName)
                    entry.time = file.lastModified()

                    zos.putNextEntry(entry)

                    var inputStream: InputStream? = null

                    try {
                        inputStream = FileInputStream(file)
                    } catch (e: Exception) {
                        if (FileAccessManager.rootOperations.isAvailable()) {
                            val process = Runtime.getRuntime().exec(
                                arrayOf(
                                    "su",
                                    "-c",
                                    "cat \"" + file.absolutePath + "\""
                                )
                            )
                            inputStream = process.inputStream
                        } else {
                            throw e
                        }
                    }

                    inputStream?.use { input ->
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count > 0) zos.write(buffer, 0, count)
                        }
                    }

                    zos.closeEntry()
                }
        }
    }

    private fun unzipFolder(zipFile: File, destFolder: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry

            while (entry != null) {
                val outFile = File(destFolder, entry.name)

                if (!outFile.canonicalPath.startsWith(
                        destFolder.canonicalPath + File.separator
                    )
                ) {
                    throw SecurityException(
                        "Zip entry outside target dir: ${entry.name}"
                    )
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()

                    FileOutputStream(outFile).use { output ->
                        zis.copyTo(output, 64 * 1024)
                    }
                }

                outFile.setLastModified(entry.time)
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
