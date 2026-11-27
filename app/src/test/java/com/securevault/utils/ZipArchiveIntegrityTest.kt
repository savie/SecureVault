package com.securevault.utils

import java.io.File
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ZipArchiveIntegrityTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun createZip(
        file: File,
        entryName: String,
        content: ByteArray
    ) {
        ZipOutputStream(file.outputStream()).use { output ->
            output.putNextEntry(ZipEntry(entryName))
            output.write(content)
            output.closeEntry()
        }
    }

    private fun newDestination(name: String): File {
        return temporaryFolder.newFolder(name)
    }

    @Test
    fun validArchive_extractsContent() {
        val zip = File(temporaryFolder.root, "valid.zip")
        val destination = newDestination("valid-output")
        val content = "SecureVault archive".toByteArray()

        createZip(zip, "folder/data.txt", content)

        ZipArchiveIntegrity.extract(zip, destination)

        assertTrue(File(destination, "folder/data.txt").isFile)
        assertTrue(
            File(destination, "folder/data.txt")
                .readBytes()
                .contentEquals(content)
        )
    }

    @Test
    fun truncatedCentralDirectory_isRejected() {
        val zip = File(temporaryFolder.root, "truncated.zip")
        val destination = newDestination("truncated-output")

        createZip(zip, "data.txt", "payload".toByteArray())

        val bytes = zip.readBytes()
        zip.writeBytes(bytes.copyOf(bytes.size - 12))

        try {
            ZipArchiveIntegrity.extract(zip, destination)
            throw AssertionError("Truncated ZIP must be rejected")
        } catch (_: IOException) {
            // Expected.
        }
    }

    @Test
    fun corruptedStoredEntryCrc_isRejected() {
        val zip = File(temporaryFolder.root, "corrupt-crc.zip")
        val destination = newDestination("corrupt-output")
        val content = "CRC-CHECK-PAYLOAD".toByteArray()
        val crc = CRC32().apply { update(content) }.value

        ZipOutputStream(zip.outputStream()).use { output ->
            val entry = ZipEntry("data.txt")
            entry.method = ZipEntry.STORED
            entry.size = content.size.toLong()
            entry.compressedSize = content.size.toLong()
            entry.crc = crc
            output.putNextEntry(entry)
            output.write(content)
            output.closeEntry()
        }

        val bytes = zip.readBytes()
        val marker = content
        var position = -1

        for (i in 0..(bytes.size - marker.size)) {
            var matches = true
            for (j in marker.indices) {
                if (bytes[i + j] != marker[j]) {
                    matches = false
                    break
                }
            }
            if (matches) {
                position = i
                break
            }
        }

        assertTrue("Stored payload must exist in ZIP", position >= 0)
        bytes[position] = (bytes[position].toInt() xor 0x01).toByte()
        zip.writeBytes(bytes)

        try {
            ZipArchiveIntegrity.extract(zip, destination)
            throw AssertionError("Corrupted CRC must be rejected")
        } catch (_: IOException) {
            // Expected.
        }
    }

    @Test
    fun pathTraversal_isRejected() {
        val zip = File(temporaryFolder.root, "traversal.zip")
        val destination = newDestination("traversal-output")

        createZip(zip, "../escape.txt", "blocked".toByteArray())

        try {
            ZipArchiveIntegrity.extract(zip, destination)
            throw AssertionError("Traversal path must be rejected")
        } catch (_: IOException) {
            // Expected.
        }

        assertTrue(
            "No file may be created outside the destination",
            !File(temporaryFolder.root, "escape.txt").exists()
        )
    }
}
