package com.securevault.utils

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ContainerSessionManager {
    // Menyimpan password container yang lagi kebuka di memori
    private val passwords = ConcurrentHashMap<String, String>()
    
    // Event buat ngasih tau UI kalo ada auto-lock
    private val _autoLockTrigger = MutableSharedFlow<Unit>()
    val autoLockTrigger = _autoLockTrigger.asSharedFlow()

    internal fun addSession(containerId: String, password: String) {
        passwords[containerId] = password
    }

    internal fun removeSession(containerId: String) {
        passwords.remove(containerId)
    }

    internal fun getPassword(containerId: String): String? {
        return passwords[containerId]
    }

    internal fun hasSession(containerId: String): Boolean {
        return passwords.containsKey(containerId)
    }

    internal fun hasAnySession(): Boolean {
        return passwords.isNotEmpty()
    }

    fun triggerAutoLockEvent() {
        _autoLockTrigger.tryEmit(Unit)
    }
}
