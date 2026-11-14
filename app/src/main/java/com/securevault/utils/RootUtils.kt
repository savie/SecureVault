package com.securevault.utils

import java.io.File

object RootUtils {
    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            process.waitFor() == 0
        } catch (e: Exception) { false }
    }

    // Paksa bikin folder dulu pake root, baru copy
    fun copyFileAsRoot(sourceFile: File, destinationPath: String): Boolean {
        return try {
            val dest = File(destinationPath)
            val parentDir = dest.parent ?: ""
            
            // Perintah: Buat folder (mkdir -p), copy file (cp), ubah izin (chmod)
            val command = "mkdir -p \"$parentDir\" && cp \"${sourceFile.absolutePath}\" \"$destinationPath\" && chmod 666 \"$destinationPath\""
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            process.waitFor() == 0
        } catch (e: Exception) { false }
    }

    fun deleteFileAsRoot(filePath: String): Boolean {
        return try {
            val command = "rm -f \"$filePath\""
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            process.waitFor() == 0
        } catch (e: Exception) { false }
    }
}
