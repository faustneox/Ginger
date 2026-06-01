package com.ginger.android.util

import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Retry network operations with exponential backoff.
 *
 * Example:
 * ```
 * retryWithBackoff(times = 3, initialDelay = 1000) {
 *     apiService.fetchData()
 * }
 * ```
 *
 * @param times Number of attempts (default 3)
 * @param initialDelay Initial delay in milliseconds (default 500ms)
 * @param maxDelay Maximum delay in milliseconds (default 5000ms)
 * @param factor Exponential backoff multiplier (default 2.0)
 * @param block Suspend function to execute
 * @return Result of block execution
 * @throws Last exception if all retries fail
 */
suspend fun <T> retryWithBackoff(
    times: Int = 3,
    initialDelay: Long = 500,
    maxDelay: Long = 5_000,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    var lastException: Exception? = null
    
    repeat(times - 1) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            lastException = e
            Timber.w(e, "Retry attempt ${attempt + 1}/$times failed. Waiting ${currentDelay}ms before retry...")
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
    }
    
    // Final attempt
    try {
        return block()
    } catch (e: Exception) {
        Timber.e(e, "All $times retry attempts exhausted. Last error: ${e.message}")
        throw e
    }
}
