package com.securevault.utils

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Enumeration
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Extracts a ZIP archive only after validating its central-directory metadata,
 * entry sizes, and CRC values.
 *
 * The destination must be a newly-created staging directory.
 */
object ZipArchiveIntegrity {

    @Throws(IOException::class)
    fun extract(zipFile: File, destination: File) {
        if (!zipFile.isFile) {
            throw IOException("ZIP source is not a file")
        }

        if (!destination.isDirectory) {
            throw IOException("Extraction destination is not a directory")
        }

        val root = destination.canonicalFile
        val rootPath = root.path
        val rootPrefix = rootPath + File.separator
        val seenPaths = HashSet<String>()
        var totalBytes = 0L

        ZipFile(zipFile).use { archive ->
            val entries: Enumeration<out ZipEntry> = archive.entries()

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name

                if (
                    name.isBlank() ||
                    name.startsWith("/") ||
                    name.contains('\\') ||
                    name.contains('\u0000')
                ) {
                    throw IOException("Invalid ZIP entry path")
                }

                val segments = name.split('/')
                if (segments.any { it == ".." || it == "." }) {
                    throw IOException("ZIP path traversal rejected")
                }

                if (entry.size < 0L || entry.crc < 0L) {
                    throw IOException("ZIP entry metadata is incomplete")
                }

                val output = File(root, name).canonicalFile
                val outputPath = output.path

                if (!outputPath.startsWith(rootPrefix)) {
                    throw IOException("ZIP entry escapes destination")
                }

                val relativePath = outputPath.substring(rootPrefix.length)
                if (relativePath.isEmpty() || !seenPaths.add(relativePath)) {
                    throw IOException("Duplicate or invalid ZIP entry")
                }

                if (entry.isDirectory) {
                    if (entry.size != 0L) {
                        throw IOException("Directory ZIP entry has data")
                    }

                    if (output.exists()) {
                        if (!output.isDirectory) {
                            throw IOException("Directory conflicts with file")
                        }
                    } else if (!output.mkdirs()) {
                        throw IOException("Cannot create ZIP directory")
                    }

                    continue
                }

                val parent = output.parentFile
                    ?: throw IOException("ZIP entry has no parent")

                if (!parent.exists() && !parent.mkdirs()) {
                    throw IOException("Cannot create ZIP parent directory")
                }

                if (!parent.isDirectory || output.exists()) {
                    throw IOException("ZIP output path conflict")
                }

                val crc = CRC32()
                var entryBytes = 0L

                archive.getInputStream(entry).use { input ->
                    FileOutputStream(output).use { fileOutput ->
                        val buffer = ByteArray(64 * 1024)

                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue

                            entryBytes = Math.addExact(
                                entryBytes,
                                count.toLong()
                            )
                            totalBytes = Math.addExact(
                                totalBytes,
                                count.toLong()
                            )

                            if (entryBytes > entry.size) {
                                throw IOException("ZIP entry exceeds declared size")
                            }

                            crc.update(buffer, 0, count)
                            fileOutput.write(buffer, 0, count)
                        }

                        fileOutput.fd.sync()
                    }
                }

                if (entryBytes != entry.size || crc.value != entry.crc) {
                    throw IOException("ZIP entry size or CRC mismatch")
                }

                if (entry.time >= 0L) {
                    output.setLastModified(entry.time)
                }
            }
        }
    }
}
