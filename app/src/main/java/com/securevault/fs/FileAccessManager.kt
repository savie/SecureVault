package com.securevault.fs

/**
 * Singleton entry point untuk akses provider.
 *
 * Fase 6.6D-1 (sekarang): selalu Standard — belum ada wiring ke kode existing.
 * Fase 6.6D-3 (nanti): panggil ProviderFactory.create() yang di-resolve
 *                      per flavor (free = Standard, pro = Standard + Root).
 */
object FileAccessManager {

    private val standardProvider: FileAccessProvider = StandardFileAccessProvider()

    @Volatile
    private var activeProvider: FileAccessProvider = standardProvider

    fun getProvider(): FileAccessProvider = activeProvider

    /**
     * Internal setter — dipakai ProviderFactory di fase 6.6D-3.
     * Jangan dipanggil dari UI/VM langsung.
     */
    internal fun setProvider(provider: FileAccessProvider) {
        activeProvider = provider
    }
}
