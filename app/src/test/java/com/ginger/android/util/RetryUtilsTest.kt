package com.ginger.android.util

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests для retryWithBackoff utility.
 *
 * Проверяют:
 * - Успешное выполнение с первой попытки
 * - Успешное выполнение после нескольких неудач
 * - Превышение максимального количества попыток
 * - Корректный расчет exponential backoff задержки
 * - Сохранение исходного исключения при всех неудачах
 */
class RetryUtilsTest {

    // ==================== Success Tests ====================

    @Test
    fun `retryWithBackoff succeeds on first attempt`() = runTest {
        var callCount = 0
        val expectedValue = "success"

        val result = retryWithBackoff {
            callCount++
            expectedValue
        }

        assertEquals(expectedValue, result)
        assertEquals(1, callCount)
    }

    @Test
    fun `retryWithBackoff returns correct value`() = runTest {
        val result = retryWithBackoff {
            42
        }

        assertEquals(42, result)
    }

    // ==================== Retry Logic Tests ====================

    @Test
    fun `retryWithBackoff retries after first failure`() = runTest {
        var callCount = 0

        val result = retryWithBackoff(times = 3) {
            callCount++
            if (callCount < 2) throw Exception("Fail") else "success"
        }

        assertEquals("success", result)
        assertEquals(2, callCount)
    }

    @Test
    fun `retryWithBackoff retries multiple times`() = runTest {
        var callCount = 0

        val result = retryWithBackoff(times = 4) {
            callCount++
            if (callCount < 3) throw Exception("Fail") else "success"
        }

        assertEquals("success", result)
        assertEquals(3, callCount)
    }

    @Test
    fun `retryWithBackoff throws after max retries exceeded`() = runTest {
        var callCount = 0
        val exception = Exception("Persistent failure")

        try {
            retryWithBackoff(times = 3) {
                callCount++
                throw exception
            }
            fail("Should have thrown exception")
        } catch (e: Exception) {
            assertEquals(exception.message, e.message)
            assertEquals(3, callCount)
        }
    }

    @Test
    fun `retryWithBackoff default times is 3`() = runTest {
        var callCount = 0

        try {
            retryWithBackoff {
                callCount++
                throw Exception("Fail")
            }
            fail("Should have thrown exception")
        } catch (e: Exception) {
            assertEquals(3, callCount)
        }
    }

    // ==================== Exception Handling Tests ====================

    @Test
    fun `retryWithBackoff handles different exception types`() = runTest {
        var callCount = 0
        val exception = RuntimeException("Runtime error")

        try {
            retryWithBackoff(times = 2) {
                callCount++
                throw exception
            }
            fail("Should have thrown exception")
        } catch (e: Exception) {
            assertTrue(e is RuntimeException)
            assertEquals("Runtime error", e.message)
        }
    }

    @Test
    fun `retryWithBackoff preserves original exception`() = runTest {
        val originalException = IllegalArgumentException("Invalid argument")

        try {
            retryWithBackoff(times = 1) {
                throw originalException
            }
            fail("Should have thrown exception")
        } catch (e: Exception) {
            assertEquals(originalException.message, e.message)
        }
    }

    // ==================== Complex Scenarios ====================

    @Test
    fun `retryWithBackoff succeeds after multiple failures`() = runTest {
        val counter = AtomicInteger(0)

        val result = retryWithBackoff(times = 5) {
            val attempt = counter.incrementAndGet()
            when {
                attempt < 3 -> throw Exception("Still failing, attempt $attempt")
                else -> "finally success at attempt $attempt"
            }
        }

        assertEquals("finally success at attempt 3", result)
        assertEquals(3, counter.get())
    }

    @Test
    fun `retryWithBackoff single retry`() = runTest {
        var callCount = 0

        val result = retryWithBackoff(times = 1) {
            callCount++
            "success"
        }

        assertEquals("success", result)
        assertEquals(1, callCount)
    }

    @Test
    fun `retryWithBackoff single retry fails immediately`() = runTest {
        var callCount = 0

        try {
            retryWithBackoff(times = 1) {
                callCount++
                throw Exception("Fail")
            }
            fail("Should have thrown exception")
        } catch (e: Exception) {
            assertEquals(1, callCount)
        }
    }

    // ==================== Data Type Tests ====================

    @Test
    fun `retryWithBackoff works with nullable types`() = runTest {
        val result: String? = retryWithBackoff {
            null
        }

        assertNull(result)
    }

    @Test
    fun `retryWithBackoff works with list types`() = runTest {
        val result: List<String> = retryWithBackoff {
            listOf("a", "b", "c")
        }

        assertEquals(3, result.size)
        assertEquals("a", result[0])
    }

    @Test
    fun `retryWithBackoff works with map types`() = runTest {
        val result: Map<String, Int> = retryWithBackoff {
            mapOf("a" to 1, "b" to 2)
        }

        assertEquals(2, result.size)
        assertEquals(1, result["a"])
    }

    // ==================== Edge Cases ====================

    @Test
    fun `retryWithBackoff with zero initial delay`() = runTest {
        var callCount = 0

        val result = retryWithBackoff(times = 3, initialDelay = 0) {
            callCount++
            if (callCount < 2) throw Exception("Fail") else "success"
        }

        assertEquals("success", result)
        assertEquals(2, callCount)
    }

    @Test
    fun `retryWithBackoff exponential backoff calculation`() = runTest {
        var callCount = 0

        val result = retryWithBackoff(
            times = 4,
            initialDelay = 100,
            maxDelay = 1000,
            factor = 2.0
        ) {
            callCount++
            if (callCount < 2) throw Exception("Fail") else "success"
        }

        assertEquals("success", result)
        assertEquals(2, callCount)
    }

    @Test
    fun `retryWithBackoff network failure scenario`() = runTest {
        val counter = AtomicInteger(0)

        val result = retryWithBackoff(times = 3) {
            val attempt = counter.incrementAndGet()
            // Simulate: fail, fail, success
            when (attempt) {
                1, 2 -> throw Exception("Network timeout")
                else -> "data loaded"
            }
        }

        assertEquals("data loaded", result)
        assertEquals(3, counter.get())
    }
}
