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
        } catch (_: Exception) {
            false
        }
    }

    private fun quote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    override fun copyAsRoot(sourcePath: String, destPath: String): Boolean {
        return try {
            val parentDir = File(destPath).parent ?: return false
            val command =
                "mkdir -p -- ${quote(parentDir)} && " +
                "cp -a -- ${quote(sourcePath)} ${quote(destPath)} && " +
                "test -e ${quote(destPath)}"
            execAsRoot(command)
        } catch (_: Exception) {
            false
        }
    }

    override fun deleteAsRoot(path: String): Boolean {
        return execAsRoot(
            "rm -rf -- ${quote(path)} && test ! -e ${quote(path)}"
        )
    }

    override fun moveAsRoot(fromPath: String, toPath: String): Boolean {
        return try {
            val parentDir = File(toPath).parent ?: return false
            val command =
                "mkdir -p -- ${quote(parentDir)} && " +
                "mv -- ${quote(fromPath)} ${quote(toPath)} && " +
                "test -e ${quote(toPath)} && test ! -e ${quote(fromPath)}"
            execAsRoot(command)
        } catch (_: Exception) {
            false
        }
    }

    override fun execAsRoot(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    override fun listWithRoot(path: String): List<FileInfo>? {
        return try {
            val quotedPath = quote(path)
            val cmd = if (path == "/data/data" || path.startsWith("/data/data/")) {
                "/system/bin/find $quotedPath -maxdepth 1 -mindepth 1 " +
                    "-exec /system/bin/stat -c '%n|%s|%F' {} + 2>/dev/null"
            } else {
                "/system/bin/stat -c '%n|%s|%F' $quotedPath/* 2>/dev/null"
            }

            val result = Shell.cmd(cmd).exec()
            if (!result.isSuccess) return null

            val output = mutableListOf<FileInfo>()
            result.out.forEach { line ->
                if (line.isNotEmpty()) {
                    val parts = line.split("|")
                    if (parts.size == 3) {
                        val fullPath = parts[0].trim()
                        val name = File(fullPath).name
                        val size = parts[1].trim().toLongOrNull() ?: 0L
                        val isDirectory =
                            parts[2].contains("directory", true) ||
                            parts[2].contains("link", true)

                        output.add(
                            FileInfo(
                                name = name,
                                path = fullPath,
                                isDirectory = isDirectory,
                                size = size,
                                lastModified = 0L
                            )
                        )
                    }
                }
            }
            output
        } catch (_: Exception) {
            null
        }
    }
}
