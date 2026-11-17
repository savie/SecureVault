package com.securevault.fs

/**
 * Singleton entry point untuk akses provider + root operations.
 * Impl di-resolve per flavor via ProviderFactory (free/ atau pro/).
 */
object FileAccessManager {

    private val standardProvider: FileAccessProvider = StandardFileAccessProvider()

    @Volatile
    private var activeProvider: FileAccessProvider = standardProvider

    fun getProvider(): FileAccessProvider = activeProvider

    val rootOperations: RootOperations by lazy { ProviderFactory.createRootOperations() }

    internal fun setProvider(provider: FileAccessProvider) {
        activeProvider = provider
    }
}
