package com.ginger.android.util

import android.util.Log
import com.ginger.android.util.CrashlyticsLoggerProvider
import timber.log.Timber

/**
 * Timber tree forwarding WARN+ logs and exceptions to Firebase Crashlytics.
 * Sanitizes messages to avoid leaking PII (phones, emails, passwords).
 */
class CrashlyticsTree : Timber.Tree() {

    private fun sanitize(input: String): String {
        var out = input
        // redact emails
        val emailRegex = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
        out = emailRegex.replace(out, "[REDACTED_EMAIL]")
        // redact phone-like sequences (at least 7 digits, may include +, spaces, dashes, parentheses)
        val phoneRegex = Regex("\\+?\\d[\\d\\-\\s()]{5,}\\d")
        out = phoneRegex.replace(out, "[REDACTED_PHONE]")
        // redact password patterns like password=xxx or password: xxx (case-insensitive)
        val pwdRegex = Regex("(?i)password\\s*[:=]\\s*\\S+")
        out = pwdRegex.replace(out, "password=[REDACTED_PASSWORD]")
        return out
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority >= Log.WARN) {
            t?.let { CrashlyticsLoggerProvider.instance.recordException(it) }
            val safeMessage = sanitize("${tag ?: "App"}: $message")
            CrashlyticsLoggerProvider.instance.log(safeMessage)
        }
    }
}
