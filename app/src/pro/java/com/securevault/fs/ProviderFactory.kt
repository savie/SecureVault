package com.securevault.fs

object ProviderFactory {
    fun createRootOperations(): RootOperations = ProRootOperations()
}
