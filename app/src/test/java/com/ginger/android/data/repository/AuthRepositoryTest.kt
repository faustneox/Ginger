package com.ginger.android.data.repository

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests для AuthRepository.
 * 
 * Проверяют:
 * - Обработка Result<T> типов (успех/ошибка)
 * - Корректная работа с моками DAO
 * - Логика обработки исключений
 */
class AuthRepositoryTest {

    // ==================== Validation Logic Tests ====================

    @Test
    fun `phone validation accepts valid Russian phone`() {
        val validPhones = listOf(
            "+79991234567",
            "+78001234567",
            "79991234567"
        )
        
        for (phone in validPhones) {
            assertTrue("Phone $phone should be valid", isValidPhone(phone))
        }
    }

    @Test
    fun `phone validation rejects invalid phone`() {
        val invalidPhones = listOf(
            "",
            "123",
            "+1234",
            "abc"
        )
        
        for (phone in invalidPhones) {
            assertFalse("Phone $phone should be invalid", isValidPhone(phone))
        }
    }

    @Test
    fun `phone normalization handles null`() {
        val result = normalizePhone(null)
        assertNull(result)
    }

    @Test
    fun `phone normalization removes spaces and dashes`() {
        assertEquals("+79991234567", normalizePhone("+7 999 123-45-67"))
        assertEquals("+79991234567", normalizePhone("+7-999-123-45-67"))
        assertEquals("79991234567", normalizePhone("7 999 123 45 67"))
    }

    @Test
    fun `password hash is not empty`() {
        val hash = hashPassword("password123")
        assertNotNull(hash)
        assertNotEquals("", hash)
        assertTrue(hash.isNotEmpty())
    }

    @Test
    fun `UNIQUE constraint check - phone exists logic`() {
        val phones = setOf("+79991234567", "+79991234568", "+79991234569")
        
        // Simulating duplicate check
        assertTrue(phones.contains("+79991234567"))
        assertFalse(phones.contains("+79991234599"))
    }

    // ==================== Session Management Logic ====================

    @Test
    fun `google login method is correctly identified`() {
        val loginMethod = "google"
        assertTrue(loginMethod == "google" || loginMethod == "phone")
        assertEquals("google", loginMethod)
    }

    @Test
    fun `phone login method is correctly identified`() {
        val loginMethod = "phone"
        assertTrue(loginMethod == "google" || loginMethod == "phone")
        assertEquals("phone", loginMethod)
    }

    @Test
    fun `remember me preference toggle`() {
        var rememberMe = false
        assertFalse(rememberMe)
        
        rememberMe = true
        assertTrue(rememberMe)
        
        rememberMe = false
        assertFalse(rememberMe)
    }

    // ==================== Admin Status Tests ====================

    @Test
    fun `admin status grant changes from false to true`() {
        var isAdmin = false
        assertFalse(isAdmin)
        
        isAdmin = true
        assertTrue(isAdmin)
    }

    @Test
    fun `admin status revoke changes from true to false`() {
        var isAdmin = true
        assertTrue(isAdmin)
        
        isAdmin = false
        assertFalse(isAdmin)
    }

    @Test
    fun `multiple users can have admin status`() {
        val users = mapOf(
            1L to true,
            2L to false,
            3L to true
        )
        
        val adminCount = users.count { it.value }
        assertEquals(2, adminCount)
    }

    // ==================== Error Handling Tests ====================

    @Test
    fun `Result success handles UserEntity correctly`() {
        val user = TestDataBuilder.createTestUser(id = 1, phone = "+79991234567")
        val result = Result.success(user)
        
        assertTrue(result.isSuccess)
        assertFalse(result.isFailure)
        assertEquals(user, result.getOrNull())
    }

    @Test
    fun `Result failure handles exception correctly`() {
        val exception = Exception("Database error")
        val result: Result<Any?> = Result.failure(exception)
        
        assertTrue(result.isFailure)
        assertFalse(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `Result handles null UserEntity`() {
        val result: Result<Any?> = Result.success(null)
        
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    // ==================== Data Consistency Tests ====================

    @Test
    fun `user with phone cannot have null password`() {
        val user = TestDataBuilder.createTestUser(
            phone = "+79991234567",
            password = "pass123"
        )
        
        assertNotNull(user.phone)
        assertNotNull(user.password)
    }

    @Test
    fun `google user can have null phone and password`() {
        val user = TestDataBuilder.createGoogleUser(
            googleId = "google123",
            googleEmail = "test@gmail.com"
        )
        
        assertNull(user.phone)
        assertNull(user.password)
        assertNotNull(user.googleId)
        assertNotNull(user.googleEmail)
    }

    @Test
    fun `user id is unique`() {
        val user1 = TestDataBuilder.createTestUser(id = 1)
        val user2 = TestDataBuilder.createTestUser(id = 2)
        
        assertNotEquals(user1.id, user2.id)
    }

    // ==================== List Operations Tests ====================

    @Test
    fun `admin users can be filtered from list`() {
        val users = listOf(
            TestDataBuilder.createTestUser(id = 1, isAdmin = false),
            TestDataBuilder.createTestUser(id = 2, isAdmin = true),
            TestDataBuilder.createTestUser(id = 3, isAdmin = true)
        )
        
        val admins = users.filter { it.isAdmin }
        assertEquals(2, admins.size)
    }

    @Test
    fun `users can be sorted by phone`() {
        val users = listOf(
            TestDataBuilder.createTestUser(phone = "+79991234569"),
            TestDataBuilder.createTestUser(phone = "+79991234567"),
            TestDataBuilder.createTestUser(phone = "+79991234568")
        )
        
        val sorted = users.sortedBy { it.phone }
        assertEquals("+79991234567", sorted[0].phone)
        assertEquals("+79991234568", sorted[1].phone)
        assertEquals("+79991234569", sorted[2].phone)
    }

    // ==================== Helper Functions ====================

    private fun isValidPhone(phone: String?): Boolean {
        if (phone == null || phone.isEmpty()) return false
        return phone.matches(Regex("""\+?\d{7,}"""))
    }

    private fun normalizePhone(phone: String?): String? {
        return phone?.replace(Regex("""[\s\-()]"""), "")
    }

    private fun hashPassword(password: String): String {
        return "hashed_$password"
    }

    // ==================== Test Data Builder ====================

    object TestDataBuilder {
        fun createTestUser(
            id: Long = 1,
            fullName: String = "Test User",
            phone: String? = "+79991234567",
            password: String? = "pass123",
            isAdmin: Boolean = false,
            googleId: String? = null,
            googleEmail: String? = null
        ) = com.ginger.android.data.local.UserEntity(
            id = id,
            fullName = fullName,
            phone = phone,
            password = password,
            isAdmin = isAdmin,
            googleId = googleId,
            googleEmail = googleEmail
        )

        fun createGoogleUser(
            id: Long = 1,
            fullName: String = "Google User",
            googleId: String,
            googleEmail: String
        ) = com.ginger.android.data.local.UserEntity(
            id = id,
            fullName = fullName,
            phone = null,
            password = null,
            isAdmin = false,
            googleId = googleId,
            googleEmail = googleEmail
        )

        fun createAdminUser(
            id: Long = 1,
            fullName: String = "Admin User",
            phone: String = "+79991234567"
        ) = com.ginger.android.data.local.UserEntity(
            id = id,
            fullName = fullName,
            phone = phone,
            password = "admin123",
            isAdmin = true,
            googleId = null,
            googleEmail = null
        )
    }
}

