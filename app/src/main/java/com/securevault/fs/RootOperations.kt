package com.securevault.fs

/**
 * Interface operasi yang butuh root.
 *
 * Impl:
 * - free flavor : FreeRootOperations -> stub, semua return false/null
 * - pro flavor  : ProRootOperations  -> real (libsu / su), fase 6.6D-3b
 */
interface RootOperations {
    fun isAvailable(): Boolean
    fun copyAsRoot(sourcePath: String, destPath: String): Boolean
    fun deleteAsRoot(path: String): Boolean
    fun moveAsRoot(fromPath: String, toPath: String): Boolean
    fun execAsRoot(command: String): Boolean
    /** Return null kalau tidak bisa list via root (no root / gagal). */
    fun listWithRoot(path: String): List<FileInfo>?
}
