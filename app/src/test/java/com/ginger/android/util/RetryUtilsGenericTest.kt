package com.ginger.android.util

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

class RetryUtilsGenericTest {

    @Test
    fun `works with String type`() = runTest {
        val result = retryWithBackoff { "hello" }
        assertEquals("hello", result)
    }

    @Test
    fun `works with nullable type`() = runTest {
        val result: String? = retryWithBackoff { null }
        assertNull(result)
    }

    @Test
    fun `works with list type`() = runTest {
        val result = retryWithBackoff { listOf(1, 2, 3) }
        assertEquals(3, result.size)
    }

    @Test
    fun `works with custom type`() = runTest {
        data class Foo(val v: Int)
        val result = retryWithBackoff { Foo(42) }
        assertEquals(42, result.v)
    }

    @Test
    fun `propagates exception after single attempt`() = runTest {
        val ex = RuntimeException("boom")
        try {
            retryWithBackoff(times = 1) { throw ex }
            fail("Expected exception to be thrown")
        } catch (e: RuntimeException) {
            assertSame(ex, e)
        }
    }
}
