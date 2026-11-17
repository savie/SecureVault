package com.securevault.fs

import java.io.File

/**
 * Real implementation — pakai Runtime.exec("su").
 * Backend behavior identik dengan RootUtils lama.
 */
class ProRootOperations : RootOperations {

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
        // Implementasi di 6.6D-3c (bersama Shell block FileManagerViewModel)
        return null
    }
}
