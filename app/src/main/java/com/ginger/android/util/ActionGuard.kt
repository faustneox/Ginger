package com.ginger.android.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withTimeoutOrNull

class ActionGuard {

    private val lock = Any()
    private val processingIds = mutableSetOf<Long>()
    private var isGlobalProcessing = false

    fun startGlobal(): Boolean = synchronized(lock) {
        if (isGlobalProcessing) return false
        isGlobalProcessing = true
        true
    }

    fun finishGlobal() = synchronized(lock) {
        isGlobalProcessing = false
    }

    fun isGlobalProcessing(): Boolean = synchronized(lock) { isGlobalProcessing }

    fun start(id: Long): Boolean = synchronized(lock) {
        if (processingIds.contains(id) || isGlobalProcessing) return false
        processingIds.add(id)
        true
    }

    fun finish(id: Long) = synchronized(lock) {
        processingIds.remove(id)
    }

    fun isProcessing(id: Long): Boolean = synchronized(lock) { processingIds.contains(id) }

    fun isAnyProcessing(): Boolean = synchronized(lock) { isGlobalProcessing || processingIds.isNotEmpty() }

    fun getCurrentProcessingId(): Long? = synchronized(lock) { processingIds.firstOrNull() }

    fun clear() = synchronized(lock) {
        processingIds.clear()
        isGlobalProcessing = false
    }

    inline fun <T> with(id: Long, block: () -> T): T? {
        if (!start(id)) return null
        return try {
            block()
        } finally {
            finish(id)
        }
    }

    inline fun <T> withGlobal(block: () -> T): T? {
        if (!startGlobal()) return null
        return try {
            block()
        } finally {
            finishGlobal()
        }
    }

    suspend inline fun <T> withGlobalSuspend(block: suspend () -> T): T? {
        if (!startGlobal()) return null
        return try {
            block()
        } finally {
            finishGlobal()
        }
    }

    /**
     * Suspend-версия withGlobal с опциональным таймаутом.
     * При timeoutMs = 0 (по умолчанию) таймаут не применяется.
     */
    suspend inline fun <T> withGlobalSuspendSafe(
        timeoutMs: Long = 0,
        crossinline block: suspend () -> T
    ): T? {
        if (!startGlobal()) return null
        return try {
            if (timeoutMs > 0) {
                withTimeoutOrNull(timeoutMs) { block() }
            } else {
                block()
            }
        } finally {
            finishGlobal()
        }
    }
}

fun ViewModel.launchSafeWithGuard(
    guard: ActionGuard,
    block: suspend CoroutineScope.() -> Unit
) {
    launchSafe {
        guard.withGlobalSuspendSafe {
            block()
        }
    }
}
