package com.securevault.fs

/**
 * Stub 6.6D-3a — semua return false.
 * Real impl di 6.6D-3b (pakai libsu).
 */
class ProRootOperations : RootOperations {
    override fun isAvailable(): Boolean = false
    override fun copyAsRoot(sourcePath: String, destPath: String): Boolean = false
    override fun deleteAsRoot(path: String): Boolean = false
    override fun moveAsRoot(fromPath: String, toPath: String): Boolean = false
    override fun execAsRoot(command: String): Boolean = false
    override fun listWithRoot(path: String): List<FileInfo>? = null
}
