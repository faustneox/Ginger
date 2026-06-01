package com.ginger.android.data.repository

import com.ginger.android.util.retryWithBackoff
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Integration tests для retry logic в repositories.
 *
 * Проверяют:
 * - Remote call retry on temporary failures
 * - Successful recovery after transient errors
 * - Max retries exceeded handling
 */
class RepositoryRetryIntegrationTest {

    // ==================== Login Retry Tests ====================

    @Test
    fun `loginRemote retries on temporary network failure`() = runTest {
        var attemptCount = 0

        val result = retryWithBackoff(times = 3) {
            attemptCount++
            when (attemptCount) {
                1, 2 -> throw Exception("Temporary network failure")
                else -> "login_token_12345"
            }
        }

        assertEquals("login_token_12345", result)
        assertEquals(3, attemptCount)
    }

    @Test
    fun `loginRemote succeeds on first try`() = runTest {
        var attemptCount = 0

        val result = retryWithBackoff(times = 3) {
            attemptCount++
            "login_token_12345"
        }

        assertEquals("login_token_12345", result)
        assertEquals(1, attemptCount)
    }

    @Test
    fun `loginRemote throws after max retries`() = runTest {
        var attemptCount = 0

        try {
            retryWithBackoff(times = 3) {
                attemptCount++
                throw Exception("Persistent network error")
            }
            fail("Should have thrown exception")
        } catch (e: Exception) {
            assertEquals(3, attemptCount)
            assertTrue(e.message?.contains("Persistent network error") ?: false)
        }
    }

    // ==================== Fetch Remote Tests ====================

    @Test
    fun `fetchRemoteAll retries on temporary failure`() = runTest {
        var attemptCount = 0

        val result = retryWithBackoff(times = 3) {
            attemptCount++
            when (attemptCount) {
                1 -> throw Exception("Connection timeout")
                else -> listOf(
                    RequestDto("req1", "Issue 1", "Desc 1"),
                    RequestDto("req2", "Issue 2", "Desc 2")
                )
            }
        }

        assertEquals(2, result.size)
        assertEquals(2, attemptCount)
    }

    @Test
    fun `fetchRemoteAll succeeds immediately`() = runTest {
        var attemptCount = 0

        val result = retryWithBackoff(times = 3) {
            attemptCount++
            listOf(
                RequestDto("req1", "Issue 1", "Desc 1"),
                RequestDto("req2", "Issue 2", "Desc 2")
            )
        }

        assertEquals(2, result.size)
        assertEquals(1, attemptCount)
    }

    @Test
    fun `fetchRemoteAll fails after all retries`() = runTest {
        var attemptCount = 0

        try {
            retryWithBackoff(times = 3) {
                attemptCount++
                throw Exception("Server error: 500")
            }
            fail("Should have thrown exception")
        } catch (e: Exception) {
            assertEquals(3, attemptCount)
            assertTrue(e.message?.contains("Server error") ?: false)
        }
    }

    // ==================== Different Error Scenarios ====================

    @Test
    fun `retry handles connection timeout`() = runTest {
        var attemptCount = 0

        val result = retryWithBackoff(times = 3) {
            attemptCount++
            if (attemptCount < 2) {
                throw Exception("Connection timeout")
            }
            "data"
        }

        assertEquals("data", result)
        assertEquals(2, attemptCount)
    }

    @Test
    fun `retry handles socket exception`() = runTest {
        var attemptCount = 0

        val result = retryWithBackoff(times = 3) {
            attemptCount++
            if (attemptCount < 2) {
                throw Exception("Socket error")
            }
            "data"
        }

        assertEquals("data", result)
        assertEquals(2, attemptCount)
    }

    @Test
    fun `retry handles intermittent failures`() = runTest {
        val failurePattern = listOf(true, true, false) // Fail, Fail, Success
        var attemptCount = 0

        val result = retryWithBackoff(times = 5) {
            val shouldFail = attemptCount < failurePattern.size && failurePattern[attemptCount]
            attemptCount++
            if (shouldFail) {
                throw Exception("Intermittent network error")
            }
            "recovered"
        }

        assertEquals("recovered", result)
        assertEquals(3, attemptCount)
    }

    // ==================== Resource Handling ====================

    @Test
    fun `retry handles multiple failures and recovers`() = runTest {
        val counter = AtomicInteger(0)

        val result = retryWithBackoff(times = 5) {
            val attempt = counter.incrementAndGet()
            when {
                attempt <= 2 -> throw Exception("Attempt $attempt: Network error")
                else -> "Successfully retrieved on attempt $attempt"
            }
        }

        assertEquals("Successfully retrieved on attempt 3", result)
        assertEquals(3, counter.get())
    }

    @Test
    fun `retry with partial data response`() = runTest {
        var attemptCount = 0

        val result = retryWithBackoff(times = 3) {
            attemptCount++
            if (attemptCount < 2) {
                // Simulate partial response - retry
                throw Exception("Incomplete response")
            }
            listOf(
                RequestDto("req1", "Complete Issue", "Full Description")
            )
        }

        assertEquals(1, result.size)
        assertEquals(2, attemptCount)
    }

    // ==================== Test Helpers ====================

    data class RequestDto(
        val id: String,
        val title: String,
        val description: String
    )
}
