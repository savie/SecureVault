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

    fun addSession(containerId: String, password: String) {
        passwords[containerId] = password
    }

    fun removeSession(containerId: String) {
        passwords.remove(containerId)
    }

    fun getPassword(containerId: String): String? {
        return passwords[containerId]
    }

    fun getAllSessions(): Map<String, String> {
        return passwords.toMap()
    }

    fun triggerAutoLockEvent() {
        _autoLockTrigger.tryEmit(Unit)
    }
}
