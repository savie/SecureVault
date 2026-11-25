package com.securevault.utils

import android.system.Os
import com.securevault.crypto.CryptoManager
import com.securevault.crypto.CryptoStreamV3
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
    private val MAGIC_V1 = "SVC1".toByteArray(Charsets.US_ASCII)
    private val MAGIC_V2 = "SVC2".toByteArray(Charsets.US_ASCII)
    private const val SALT_LENGTH = 16
    private const val HEADER_SIZE = 4 + SALT_LENGTH

    suspend fun packFolder(
        sourceFolder: File,
        destContainer: File,
        password: String,
        onDiagnostic: ((String, String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        fun report(event: String, detail: String = "") {
            try {
                onDiagnostic?.invoke(event, detail)
            } catch (_: Exception) {
                // Diagnostic callback must never interrupt container packing.
            }
        }

        val parent = destContainer.parentFile ?: run {
            report("PACK_PARENT_MISSING")
            return@withContext false
        }

        if (!sourceFolder.isDirectory) {
            report("PACK_SOURCE_NOT_DIRECTORY")
            return@withContext false
        }

        if (!parent.exists() && !parent.mkdirs()) {
            report("PACK_PARENT_CREATE_FAILED")
            return@withContext false
        }

        var tempZip: File? = null
        var encryptedTemp: File? = null
        var verifyZip: File? = null

        try {
            tempZip = File.createTempFile("svct-source-", ".zip", parent)
            encryptedTemp = File.createTempFile("svct-encrypted-", ".tmp", parent)
            verifyZip = File.createTempFile("svct-verify-", ".zip", parent)

            zipFolder(sourceFolder, tempZip)

            if (!tempZip.isFile || tempZip.length() <= 0L) {
                report("PACK_EMPTY_ARCHIVE")
                return@withContext false
            }

            val passwordChars = password.toCharArray()

            try {
                FileOutputStream(encryptedTemp).use { output ->
                    FileInputStream(tempZip).use { input ->
                        CryptoStreamV3.encrypt(
                            CryptoStreamV3.Format.CONTAINER,
                            passwordChars,
                            input,
                            output
                        )
                    }
                    output.fd.sync()
                }
            } finally {
                passwordChars.fill('\u0000')
            }

            // Verify the entire authenticated V3 stream before replacement.
            val verifyPassword = password.toCharArray()
            val verified = try {
                FileInputStream(encryptedTemp).use { input ->
                    FileOutputStream(verifyZip).use { output ->
                        CryptoStreamV3.decrypt(
                            CryptoStreamV3.Format.CONTAINER,
                            verifyPassword,
                            input,
                            output
                        )
                    }
                }
            } finally {
                verifyPassword.fill('\u0000')
            }

            if (!verified || !verifyZip.isFile) {
                report("PACK_VERIFY_FAILED")
                return@withContext false
            }

            val sourceHash = sha256(tempZip)
            val verifiedHash = sha256(verifyZip)

            if (!sourceHash.contentEquals(verifiedHash)) {
                report("PACK_HASH_MISMATCH")
                return@withContext false
            }

            if (tempZip.length() != verifyZip.length()) {
                report("PACK_LENGTH_MISMATCH")
                return@withContext false
            }

            // Same-directory rename: atomically replaces the old destination.
            // The original remains untouched if verification or rename fails.
            Os.rename(
                encryptedTemp.absolutePath,
                destContainer.absolutePath
            )

            report(
                "PACK_ATOMIC_RENAME_SUCCESS",
                "size=${destContainer.length()}"
            )
            true
        } catch (e: Exception) {
            android.util.Log.e("ContainerEngine", "Container pack failed", e)
            report(
                "PACK_EXCEPTION",
                "type=${e.javaClass.simpleName}"
            )
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

            val prefix = ByteArray(4)
            FileInputStream(sourceContainer).use { input ->
                if (!readFully(input, prefix)) {
                    return@withContext false
                }
            }

            val magic = String(prefix, Charsets.US_ASCII)

            val parent = destFolder.parentFile ?: destFolder
            if (!parent.exists() && !parent.mkdirs()) {
                return@withContext false
            }

            tempZip = File.createTempFile("svct-unpack-", ".zip", parent)

            val decryptSuccess = when (magic) {
                "SVC3" -> {
                    val passwordChars = password.toCharArray()
                    try {
                        FileInputStream(sourceContainer).use { input ->
                            FileOutputStream(tempZip).use { output ->
                                CryptoStreamV3.decrypt(
                                    CryptoStreamV3.Format.CONTAINER,
                                    passwordChars,
                                    input,
                                    output
                                )
                            }
                        }
                    } finally {
                        passwordChars.fill('\u0000')
                    }
                }

                "SVC2", "SVC1" -> {
                    val header = ByteArray(HEADER_SIZE)

                    FileInputStream(sourceContainer).use { input ->
                        if (!readFully(input, header)) {
                            return@withContext false
                        }
                    }

                    val expectedMagic =
                        if (magic == "SVC2") MAGIC_V2 else MAGIC_V1

                    if (!header.copyOfRange(0, 4).contentEquals(expectedMagic)) {
                        return@withContext false
                    }

                    val salt = header.copyOfRange(4, HEADER_SIZE)
                    val passwordChars = password.toCharArray()

                    try {
                        val key = CryptoManager.deriveKey(passwordChars, salt)

                        FileInputStream(sourceContainer).use { input ->
                            var remaining = HEADER_SIZE.toLong()

                            while (remaining > 0L) {
                                val skipped = input.skip(remaining)
                                if (skipped > 0L) {
                                    remaining -= skipped
                                } else {
                                    if (input.read() < 0) {
                                        return@use false
                                    }
                                    remaining--
                                }
                            }

                            FileOutputStream(tempZip).use { output ->
                                if (magic == "SVC2") {
                                    CryptoManager.decryptStream(key, input, output)
                                } else {
                                    CryptoManager.decryptLegacyStream(key, input, output)
                                }
                            }
                        }
                    } finally {
                        passwordChars.fill('\u0000')
                        salt.fill(0)
                        header.fill(0)
                    }
                }

                else -> false
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

                    val inputStream: InputStream = try {
                        FileInputStream(file)
                    } catch (e: Exception) {
                        if (FileAccessManager.rootOperations.isAvailable()) {
                            val process = Runtime.getRuntime().exec(
                                arrayOf(
                                    "su",
                                    "-c",
                                    "cat \"" + file.absolutePath + "\""
                                )
                            )
                            process.inputStream
                        } else {
                            throw e
                        }
                    }

                    inputStream.use { input ->
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
