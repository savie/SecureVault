package com.securevault.utils

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.securevault.data.repository.ContainerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ContainerAutoLockCoordinator : LifecycleEventObserver {
    private var initialized = false
    private var appContext: Context? = null
    private var autoLockJob: Job? = null

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        initialized = true
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_STOP -> scheduleAutoLock()
            Lifecycle.Event.ON_START -> cancelAutoLock()
            else -> Unit
        }
    }

    private fun scheduleAutoLock() {
        val context = appContext ?: return
        if (ContainerSessionManager.getAllSessions().isEmpty()) return

        val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
        val delayMs = prefs.getLong("container_auto_lock_ms", 300_000L)
        if (delayMs <= 0L) return

        autoLockJob?.cancel()
        autoLockJob = ProcessLifecycleOwner.get().lifecycleScope.launch {
            delay(delayMs)
            val locked = ContainerRepository(context).autoLockAll()
            if (locked > 0) ContainerSessionManager.triggerAutoLockEvent()
        }
    }

    private fun cancelAutoLock() {
        autoLockJob?.cancel()
        autoLockJob = null
    }
}
