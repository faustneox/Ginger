package com.ginger.android.util

object CrashlyticsLoggerProvider {
    val instance: CrashlyticsLogger = object : CrashlyticsLogger {
        override fun recordException(throwable: Throwable) {
            // No-op in debug builds.
        }

        override fun log(message: String) {
            // No-op in debug builds.
        }
    }
}
