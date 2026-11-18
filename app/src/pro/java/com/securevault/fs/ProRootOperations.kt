package com.securevault.fs

import com.topjohnwu.superuser.Shell
import java.io.File

class ProRootOperations : RootOperations {

    init {
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10)
        )
    }

    override fun isAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    override fun copyAsRoot(sourcePath: String, destPath: String): Boolean {
        return try {
            val parentDir = File(destPath).parent ?: ""
            val command = "mkdir -p \"$parentDir\" && cp \"$sourcePath\" \"$destPath\" && chmod 666 \"$destPath\""
            execAsRoot(command)
        } catch (e: Exception) {
            false
        }
    }

    override fun deleteAsRoot(path: String): Boolean {
        return execAsRoot("rm -f \"$path\"")
    }

    override fun moveAsRoot(fromPath: String, toPath: String): Boolean {
        return execAsRoot("mv \"$fromPath\" \"$toPath\"")
    }

    override fun execAsRoot(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    override fun listWithRoot(path: String): List<FileInfo>? {
        return try {
            val cmd = if (path == "/data/data" || path.startsWith("/data/data/")) {
                "/system/bin/find \"$path\" -maxdepth 1 -mindepth 1 -exec /system/bin/stat -c '%n|%s|%F' {} + 2>/dev/null"
            } else {
                "/system/bin/stat -c '%n|%s|%F' \"$path\"/* 2>/dev/null"
            }
            val result = Shell.cmd(cmd).exec()
            val out = mutableListOf<FileInfo>()
            result.out.forEach { line ->
                if (line.isNotEmpty()) {
                    val parts = line.split("|")
                    if (parts.size == 3) {
                        val fullPath = parts[0].trim()
                        val name = File(fullPath).name
                        val size = parts[1].trim().toLongOrNull() ?: 0L
                        val isDir = parts[2].contains("directory", true) || parts[2].contains("link", true)
                        out.add(FileInfo(name, fullPath, isDir, size, 0L))
                    }
                }
            }
            out
        } catch (e: Exception) {
            null
        }
    }
}
