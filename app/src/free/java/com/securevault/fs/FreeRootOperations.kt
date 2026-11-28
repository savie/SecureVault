package com.securevault.fs

/** Stub untuk free flavor — tidak ada root. */
class FreeRootOperations : RootOperations {
    override fun isAvailable(): Boolean = false
    override fun copyAsRoot(sourcePath: String, destPath: String): Boolean = false
    override fun verifyCopyAsRoot(sourcePath: String, destPath: String): Boolean = false
    override fun deleteAsRoot(path: String): Boolean = false
    override fun moveAsRoot(fromPath: String, toPath: String): Boolean = false
    override fun execAsRoot(command: String): Boolean = false
    override fun listWithRoot(path: String): List<FileInfo>? = null
}
