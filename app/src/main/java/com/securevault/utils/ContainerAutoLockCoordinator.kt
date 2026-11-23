package com.securevault.utils

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.securevault.data.repository.ContainerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

object ContainerAutoLockCoordinator : LifecycleEventObserver {
    private var initialized = false
    private var appContext: Context? = null
    private var autoLockJob: Job? = null
    private var autoLockExecutingJob: Job? = null

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        initialized = true

        AutoLockDiagnosticsStore.record(
            context,
            "COORDINATOR_INITIALIZED"
        )
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_STOP -> scheduleAutoLock()
            Lifecycle.Event.ON_START -> cancelAutoLock()
            else -> Unit
        }
    }

    @Synchronized
    private fun scheduleAutoLock() {
        val context = appContext ?: return

        if (autoLockExecutingJob?.isActive == true) {
            AutoLockDiagnosticsStore.record(
                context,
                "TIMER_NOT_SCHEDULED",
                "Auto-Lock execution already in progress"
            )
            return
        }

        val hasSession = ContainerSessionManager.hasAnySession()
        AutoLockDiagnosticsStore.record(
            context,
            "LIFECYCLE_ON_STOP",
            "hasSession=$hasSession"
        )

        if (!hasSession) {
            AutoLockDiagnosticsStore.record(
                context,
                "TIMER_NOT_SCHEDULED",
                "No active container session"
            )
            return
        }

        val delayMs = AutoLockSettingsStore.getDurationMillis(context)

        AutoLockDiagnosticsStore.record(
            context,
            "AUTOLOCK_SETTING_READ",
            "delayMs=$delayMs"
        )

        if (delayMs <= 0L) {
            AutoLockDiagnosticsStore.record(
                context,
                "TIMER_DISABLED",
                "Auto-Lock is Off"
            )
            return
        }

        autoLockJob?.cancel()

        AutoLockDiagnosticsStore.record(
            context,
            "TIMER_SCHEDULED",
            "delayMs=$delayMs"
        )

        autoLockJob = ProcessLifecycleOwner.get().lifecycleScope.launch {
            val currentJob = currentCoroutineContext()[Job]

            try {
                delay(delayMs)

                synchronized(this@ContainerAutoLockCoordinator) {
                    val executingJob = currentJob ?: throw CancellationException()
                    if (!executingJob.isActive) {
                        throw CancellationException()
                    }
                    autoLockExecutingJob = executingJob
                }

                AutoLockDiagnosticsStore.record(
                    context,
                    "TIMER_COMPLETED",
                    "Calling autoLockAll"
                )

                val locked = ContainerRepository(context).autoLockAll()

                AutoLockDiagnosticsStore.record(
                    context,
                    "AUTOLOCK_FINISHED",
                    "lockedCount=$locked"
                )

                if (locked > 0) {
                    ContainerSessionManager.triggerAutoLockEvent()
                    AutoLockDiagnosticsStore.record(
                        context,
                        "AUTOLOCK_EVENT_TRIGGERED",
                        "lockedCount=$locked"
                    )
                }
            } catch (e: CancellationException) {
                AutoLockDiagnosticsStore.record(
                    context,
                    "TIMER_COROUTINE_CANCELLED"
                )
                throw e
            } catch (e: Exception) {
                AutoLockDiagnosticsStore.record(
                    context,
                    "AUTOLOCK_COROUTINE_EXCEPTION",
                    e.javaClass.simpleName
                )
            } finally {
                synchronized(this@ContainerAutoLockCoordinator) {
                    if (autoLockJob === currentJob) {
                        autoLockJob = null
                    }
                    if (autoLockExecutingJob === currentJob) {
                        autoLockExecutingJob = null
                    }
                }
            }
        }
    }

    @Synchronized
    private fun cancelAutoLock() {
        val context = appContext
        val wasActive = autoLockJob?.isActive == true
        val executionInProgress = autoLockExecutingJob?.isActive == true

        if (!executionInProgress) {
            autoLockJob?.cancel()
            autoLockJob = null
        }

        if (context != null) {
            AutoLockDiagnosticsStore.record(
                context,
                "LIFECYCLE_ON_START",
                "timerWasActive=$wasActive autoLockExecuting=$executionInProgress"
            )
        }
    }
}
