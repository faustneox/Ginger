package com.ginger.android.util

import com.google.firebase.crashlytics.FirebaseCrashlytics

object CrashlyticsLoggerProvider {
    val instance: CrashlyticsLogger = object : CrashlyticsLogger {
        private val crashlytics by lazy { FirebaseCrashlytics.getInstance() }

        override fun recordException(throwable: Throwable) {
            crashlytics.recordException(throwable)
        }

        override fun log(message: String) {
            crashlytics.log(message)
        }
    }
}
