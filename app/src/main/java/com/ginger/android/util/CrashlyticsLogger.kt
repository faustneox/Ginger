package com.ginger.android.util

interface CrashlyticsLogger {
    fun recordException(throwable: Throwable)
    fun log(message: String)
}
