package com.securevault.utils

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

enum class ArchiveFormat {
    ZIP,
    TAR_GZ
}

sealed class ArchiveResult {
    data class Success(val output: File) : ArchiveResult()
    data class Failure(val reason: String) : ArchiveResult()
}

object ArchiveHelper {

    private const val BUFFER_SIZE = 8192

    fun detectFormat(file: File): ArchiveFormat? {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".tar.gz") -> ArchiveFormat.TAR_GZ
            name.endsWith(".zip") -> ArchiveFormat.ZIP
            else -> null
        }
    }

    suspend fun compress(
        source: File,
        format: ArchiveFormat
    ): ArchiveResult = withContext(Dispatchers.IO) {
        var output: File? = null

        try {
            require(source.exists()) { "Source tidak ditemukan." }
            require(source.isFile || source.isDirectory) {
                "Source bukan file atau folder yang valid."
            }
            require(!java.nio.file.Files.isSymbolicLink(source.toPath())) {
                "Symbolic link tidak didukung."
            }

            val parent = source.absoluteFile.parentFile
                ?: throw IllegalArgumentException("Folder tujuan tidak valid.")

            val extension = when (format) {
                ArchiveFormat.ZIP -> ".zip"
                ArchiveFormat.TAR_GZ -> ".tar.gz"
            }

            output = uniqueArchiveFile(parent, source.name + extension)

            when (format) {
                ArchiveFormat.ZIP -> writeZip(source, output)
                ArchiveFormat.TAR_GZ -> writeTarGz(source, output)
            }

            ArchiveResult.Success(output)
        } catch (e: CancellationException) {
            output?.delete()
            throw e
        } catch (e: Exception) {
            output?.delete()
            ArchiveResult.Failure(e.message ?: "Gagal membuat archive.")
        } catch (e: LinkageError) {
            output?.delete()
            return@withContext ArchiveResult.Failure(e.message ?: "Komponen archive tidak tersedia.")
        }
    }

    suspend fun extract(archiveFile: File): ArchiveResult =
        withContext(Dispatchers.IO) {
            val created = mutableListOf<File>()

            try {
                require(archiveFile.isFile) {
                    "File archive tidak ditemukan."
                }

                val format = detectFormat(archiveFile)
                    ?: throw IllegalArgumentException("Format archive tidak didukung.")

                val parent = archiveFile.absoluteFile.parentFile
                    ?: throw IllegalArgumentException("Folder tujuan tidak valid.")

                extractDirectoryMap.set(mutableMapOf())
                val baseName = when (format) {
                    ArchiveFormat.ZIP -> archiveFile.name.dropLast(4)
                    ArchiveFormat.TAR_GZ -> archiveFile.name.dropLast(7)
                }

                when (format) {
                    ArchiveFormat.ZIP -> readZip(archiveFile, parent, baseName, created)
                    ArchiveFormat.TAR_GZ -> readTarGz(archiveFile, parent, baseName, created)
                }

                extractDirectoryMap.remove()
                ArchiveResult.Success(parent)
            } catch (e: CancellationException) {
                cleanupCreated(created)
                extractDirectoryMap.remove()
                throw e
            } catch (e: Exception) {
                cleanupCreated(created)
                extractDirectoryMap.remove()
                ArchiveResult.Failure(e.message ?: "Gagal extract archive.")
            } catch (e: LinkageError) {
                cleanupCreated(created)
                extractDirectoryMap.remove()
                return@withContext ArchiveResult.Failure(e.message ?: "Komponen archive tidak tersedia.")
            }
        }

    private fun uniqueArchiveFile(parent: File, requestedName: String): File {
        val extension = when {
            requestedName.endsWith(".tar.gz", ignoreCase = true) -> ".tar.gz"
            requestedName.endsWith(".zip", ignoreCase = true) -> ".zip"
            else -> ""
        }

        val base = requestedName.dropLast(extension.length)
        var candidate = File(parent, requestedName)
        var counter = 1

        while (candidate.exists()) {
            candidate = File(parent, "$base ($counter)$extension")
            counter++
        }
        return candidate
    }

    private fun uniqueSibling(
        parent: File,
        requestedName: String,
        isDirectory: Boolean
    ): File {
        var candidate = File(parent, requestedName)
        if (!candidate.exists()) return candidate

        val dot = if (isDirectory) -1 else requestedName.lastIndexOf('.')
        val hasExtension = dot > 0
        val base = if (hasExtension) requestedName.substring(0, dot) else requestedName
        val extension = if (hasExtension) requestedName.substring(dot) else ""

        var counter = 1
        while (candidate.exists()) {
            candidate = File(parent, "$base ($counter)$extension")
            counter++
        }
        return candidate
    }

    private fun writeZip(source: File, destination: File) {
        ZipArchiveOutputStream(
            BufferedOutputStream(FileOutputStream(destination))
        ).use { zip ->
            zip.setLevel(6)
            addToZip(zip, source, source.name)
        }
    }

    private fun addToZip(
        zip: ZipArchiveOutputStream,
        file: File,
        entryName: String
    ) {
        require(!java.nio.file.Files.isSymbolicLink(file.toPath())) {
            "Archive tidak mendukung symbolic link: ${file.name}"
        }

        if (file.isDirectory) {
            val directoryName =
                if (entryName.endsWith("/")) entryName else "$entryName/"

            val entry = ZipArchiveEntry(directoryName)
            entry.time = file.lastModified()
            zip.putArchiveEntry(entry)
            zip.closeArchiveEntry()

            val children = file.listFiles()
                ?: throw IllegalStateException("Tidak bisa membaca folder: ${file.name}")

            children.forEach { child ->
                addToZip(zip, child, "$entryName/${child.name}")
            }
        } else {
            val entry = ZipArchiveEntry(entryName)
            entry.size = file.length()
            entry.time = file.lastModified()

            zip.putArchiveEntry(entry)
            file.inputStream().buffered().use { input ->
                input.copyTo(zip, BUFFER_SIZE)
            }
            zip.closeArchiveEntry()
        }
    }

    private fun writeTarGz(source: File, destination: File) {
        val gzip = GzipCompressorOutputStream(
            BufferedOutputStream(FileOutputStream(destination))
        )

        TarArchiveOutputStream(gzip).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
            addToTar(tar, source, source.name)
        }
    }

    private fun addToTar(
        tar: TarArchiveOutputStream,
        file: File,
        entryName: String
    ) {
        require(!java.nio.file.Files.isSymbolicLink(file.toPath())) {
            "Archive tidak mendukung symbolic link: ${file.name}"
        }

        val entry = TarArchiveEntry(file, entryName)
        tar.putArchiveEntry(entry)

        if (file.isFile) {
            file.inputStream().buffered().use { input ->
                input.copyTo(tar, BUFFER_SIZE)
            }
        }

        tar.closeArchiveEntry()

        if (file.isDirectory) {
            val children = file.listFiles()
                ?: throw IllegalStateException("Tidak bisa membaca folder: ${file.name}")

            children.forEach { child ->
                addToTar(tar, child, "$entryName/${child.name}")
            }
        }
    }

    private fun readZip(
        archive: File,
        destination: File,
        archiveBaseName: String,
        created: MutableList<File>
    ) {
        ZipArchiveInputStream(
            BufferedInputStream(FileInputStream(archive))
        ).use { zip ->
            while (true) {
                val entry = zip.getNextZipEntry() ?: break

                require(!entry.isUnixSymlink) {
                    "Symbolic link di dalam ZIP tidak diizinkan."
                }

                extractEntry(
                    destination, archiveBaseName, entry.name,
                    entry.isDirectory, zip, created
                )
            }
        }
    }

    private fun readTarGz(
        archive: File,
        destination: File,
        archiveBaseName: String,
        created: MutableList<File>
    ) {
        val gzip = GzipCompressorInputStream(
            BufferedInputStream(FileInputStream(archive))
        )

        TarArchiveInputStream(gzip).use { tar ->
            while (true) {
                val entry = tar.getNextTarEntry() ?: break

                require(!entry.isSymbolicLink && !entry.isLink) {
                    "Link di dalam TAR tidak diizinkan."
                }
                require(entry.isDirectory || entry.isFile) {
                    "Tipe entry TAR tidak didukung: ${entry.name}"
                }

                extractEntry(
                    destination, archiveBaseName, entry.name,
                    entry.isDirectory, tar, created
                )
            }
        }
    }

    private fun extractEntry(
        root: File,
        archiveBaseName: String,
        entryName: String,
        isDirectory: Boolean,
        input: InputStream,
        created: MutableList<File>
    ) {
        val originalSegments = safeSegments(entryName)
        val wrapperName = archiveBaseName.replace(Regex(" \\(\\d+\\)$"), "")

        // Hilangkan satu folder pembungkus yang namanya sama dengan arsip.
        val segments = if (
            originalSegments.firstOrNull() == wrapperName &&
            (originalSegments.size > 1 || isDirectory)
        ) {
            originalSegments.drop(1)
        } else {
            originalSegments
        }

        // Entry ini hanya folder pembungkus, bukan isi yang perlu dibuat.
        if (segments.isEmpty()) return

        val directoryMap = extractDirectoryMap.get()
            ?: mutableMapOf<String, File>().also { extractDirectoryMap.set(it) }


        if (isDirectory) {
            ensureDirectory(root, segments, directoryMap, created)
            return
        }

        val parent = ensureParentDirectory(
            root, segments.dropLast(1), directoryMap, created
        )
        val target = uniqueSibling(parent, segments.last(), false)

        require(target.createNewFile()) {
            "Tidak bisa membuat file: ${target.name}"
        }
        created.add(target)

        try {
            FileOutputStream(target).buffered().use { output ->
                input.copyTo(output, BUFFER_SIZE)
            }
        } catch (e: Exception) {
            target.delete()
            created.remove(target)
            throw e
        }
    }

    private val extractDirectoryMap =
        ThreadLocal<MutableMap<String, File>>()

    private fun ensureParentDirectory(
        root: File,
        segments: List<String>,
        directoryMap: MutableMap<String, File>,
        created: MutableList<File>
    ): File {
        var parent = root
        val prefix = mutableListOf<String>()

        for (segment in segments) {
            prefix.add(segment)
            val key = prefix.joinToString("/")

            val mapped = directoryMap[key]
            if (mapped != null) {
                parent = mapped
                continue
            }

            val directory = uniqueSibling(parent, segment, true)
            require(directory.mkdir()) {
                "Tidak bisa membuat folder: ${directory.name}"
            }

            created.add(directory)
            directoryMap[key] = directory
            parent = directory
        }

        return parent
    }

    private fun ensureDirectory(
        root: File,
        segments: List<String>,
        directoryMap: MutableMap<String, File>,
        created: MutableList<File>
    ): File {
        return ensureParentDirectory(root, segments, directoryMap, created)
    }

    private fun safeSegments(entryName: String): List<String> {
        require(entryName.isNotBlank()) { "Nama entry kosong." }
        require(!entryName.startsWith("/") && !entryName.startsWith("\\")) {
            "Absolute path di dalam archive ditolak."
        }
        require(!entryName.contains('\\') && !entryName.contains('\u0000')) {
            "Nama entry tidak valid."
        }

        val normalized = entryName.trimEnd('/')
        require(normalized.isNotEmpty()) { "Nama entry tidak valid." }

        val segments = normalized.split("/")
        require(segments.none { it.isEmpty() || it == ".." || it == "." }) {
            "Path traversal atau nama entry tidak valid."
        }

        return segments
    }

    private fun cleanupCreated(created: MutableList<File>) {
        created.asReversed().forEach { it.deleteRecursively() }
        created.clear()
    }
}
