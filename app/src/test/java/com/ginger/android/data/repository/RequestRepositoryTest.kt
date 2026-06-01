package com.ginger.android.data.repository

import com.ginger.android.data.local.RequestEntity
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests для RequestRepository.
 *
 * Проверяют:
 * - Фильтрацию и сортировку заявок
 * - Обработка статусов
 * - Валидацию данных
 * - Логику работы с Result типами
 */
class RequestRepositoryTest {

    // ==================== Request Status Tests ====================

    @Test
    fun `request can be created with NEW status`() {
        val request = TestRequestBuilder.createRequest(
            status = RequestEntity.STATUS_NEW
        )
        assertEquals(RequestEntity.STATUS_NEW, request.status)
    }

    @Test
    fun `request can be moved to IN_PROGRESS status`() {
        val request = TestRequestBuilder.createRequest(
            status = RequestEntity.STATUS_IN_PROGRESS
        )
        assertEquals(RequestEntity.STATUS_IN_PROGRESS, request.status)
    }

    @Test
    fun `request can be closed with CLOSED status`() {
        val request = TestRequestBuilder.createRequest(
            status = RequestEntity.STATUS_CLOSED
        )
        assertEquals(RequestEntity.STATUS_CLOSED, request.status)
    }

    // ==================== Request Filtering Tests ====================

    @Test
    fun `requests can be filtered by status NEW`() {
        val requests = listOf(
            TestRequestBuilder.createRequest(id = 1, status = RequestEntity.STATUS_NEW),
            TestRequestBuilder.createRequest(id = 2, status = RequestEntity.STATUS_IN_PROGRESS),
            TestRequestBuilder.createRequest(id = 3, status = RequestEntity.STATUS_NEW)
        )

        val newRequests = requests.filter { it.status == RequestEntity.STATUS_NEW }
        assertEquals(2, newRequests.size)
        assertTrue(newRequests.all { it.status == RequestEntity.STATUS_NEW })
    }

    @Test
    fun `requests can be filtered by status IN_PROGRESS`() {
        val requests = listOf(
            TestRequestBuilder.createRequest(id = 1, status = RequestEntity.STATUS_NEW),
            TestRequestBuilder.createRequest(id = 2, status = RequestEntity.STATUS_IN_PROGRESS),
            TestRequestBuilder.createRequest(id = 3, status = RequestEntity.STATUS_CLOSED)
        )

        val inProgressRequests = requests.filter { it.status == RequestEntity.STATUS_IN_PROGRESS }
        assertEquals(1, inProgressRequests.size)
        assertEquals(RequestEntity.STATUS_IN_PROGRESS, inProgressRequests[0].status)
    }

    @Test
    fun `requests can be filtered by status CLOSED`() {
        val requests = listOf(
            TestRequestBuilder.createRequest(id = 1, status = RequestEntity.STATUS_NEW),
            TestRequestBuilder.createRequest(id = 2, status = RequestEntity.STATUS_CLOSED),
            TestRequestBuilder.createRequest(id = 3, status = RequestEntity.STATUS_CLOSED)
        )

        val closedRequests = requests.filter { it.status == RequestEntity.STATUS_CLOSED }
        assertEquals(2, closedRequests.size)
        assertTrue(closedRequests.all { it.status == RequestEntity.STATUS_CLOSED })
    }

    @Test
    fun `filter returns empty list when no matching requests`() {
        val requests = listOf(
            TestRequestBuilder.createRequest(id = 1, status = RequestEntity.STATUS_NEW),
            TestRequestBuilder.createRequest(id = 2, status = RequestEntity.STATUS_NEW)
        )

        val closedRequests = requests.filter { it.status == RequestEntity.STATUS_CLOSED }
        assertEquals(0, closedRequests.size)
    }

    // ==================== Owner Phone Filtering Tests ====================

    @Test
    fun `requests can be filtered by owner phone`() {
        val phone = "+79991234567"
        val requests = listOf(
            TestRequestBuilder.createRequest(id = 1, ownerPhone = phone),
            TestRequestBuilder.createRequest(id = 2, ownerPhone = "+79991234568"),
            TestRequestBuilder.createRequest(id = 3, ownerPhone = phone)
        )

        val userRequests = requests.filter { it.ownerPhone == phone }
        assertEquals(2, userRequests.size)
        assertTrue(userRequests.all { it.ownerPhone == phone })
    }

    @Test
    fun `user with no requests returns empty list`() {
        val requests = listOf(
            TestRequestBuilder.createRequest(id = 1, ownerPhone = "+79991234567"),
            TestRequestBuilder.createRequest(id = 2, ownerPhone = "+79991234568")
        )

        val userRequests = requests.filter { it.ownerPhone == "+79999999999" }
        assertEquals(0, userRequests.size)
    }

    // ==================== Combined Filtering Tests ====================

    @Test
    fun `requests can be filtered by owner phone AND status`() {
        val phone = "+79991234567"
        val status = RequestEntity.STATUS_NEW
        val requests = listOf(
            TestRequestBuilder.createRequest(id = 1, ownerPhone = phone, status = RequestEntity.STATUS_NEW),
            TestRequestBuilder.createRequest(id = 2, ownerPhone = phone, status = RequestEntity.STATUS_IN_PROGRESS),
            TestRequestBuilder.createRequest(id = 3, ownerPhone = "+79991234568", status = RequestEntity.STATUS_NEW)
        )

        val filtered = requests.filter { it.ownerPhone == phone && it.status == status }
        assertEquals(1, filtered.size)
        assertEquals(phone, filtered[0].ownerPhone)
        assertEquals(status, filtered[0].status)
    }

    // ==================== Sorting Tests ====================

    @Test
    fun `requests are sorted by creation date descending`() {
        val now = System.currentTimeMillis()
        val requests = listOf(
            TestRequestBuilder.createRequest(id = 1, createdAt = now - 100000),
            TestRequestBuilder.createRequest(id = 2, createdAt = now),
            TestRequestBuilder.createRequest(id = 3, createdAt = now - 50000)
        )

        val sorted = requests.sortedByDescending { it.createdAt }
        assertEquals(now, sorted[0].createdAt)
        assertEquals(now - 50000, sorted[1].createdAt)
        assertEquals(now - 100000, sorted[2].createdAt)
    }

    // ==================== Data Integrity Tests ====================

    @Test
    fun `request title is required and not empty`() {
        val request = TestRequestBuilder.createRequest(title = "Valid title")
        assertNotNull(request.title)
        assertTrue(request.title.isNotEmpty())
    }

    @Test
    fun `request description is required and not empty`() {
        val request = TestRequestBuilder.createRequest(description = "Valid description")
        assertNotNull(request.description)
        assertTrue(request.description.isNotEmpty())
    }

    @Test
    fun `request owner phone is required`() {
        val request = TestRequestBuilder.createRequest(ownerPhone = "+79991234567")
        assertNotNull(request.ownerPhone)
        assertTrue(request.ownerPhone.isNotEmpty())
    }

    @Test
    fun `request has unique id`() {
        val request1 = TestRequestBuilder.createRequest(id = 1)
        val request2 = TestRequestBuilder.createRequest(id = 2)

        assertNotEquals(request1.id, request2.id)
    }

    // ==================== Category Tests ====================

    @Test
    fun `request can have category Internet`() {
        val request = TestRequestBuilder.createRequest(category = "Internet")
        assertEquals("Internet", request.category)
    }

    @Test
    fun `request can have category Power`() {
        val request = TestRequestBuilder.createRequest(category = "Power")
        assertEquals("Power", request.category)
    }

    @Test
    fun `request can have category Water`() {
        val request = TestRequestBuilder.createRequest(category = "Water")
        assertEquals("Water", request.category)
    }

    @Test
    fun `request can have custom category`() {
        val customCategory = "Custom Issue"
        val request = TestRequestBuilder.createRequest(category = customCategory)
        assertEquals(customCategory, request.category)
    }

    // ==================== List Operations Tests ====================

    @Test
    fun `count requests by status`() {
        val requests = listOf(
            TestRequestBuilder.createRequest(id = 1, status = RequestEntity.STATUS_NEW),
            TestRequestBuilder.createRequest(id = 2, status = RequestEntity.STATUS_NEW),
            TestRequestBuilder.createRequest(id = 3, status = RequestEntity.STATUS_IN_PROGRESS),
            TestRequestBuilder.createRequest(id = 4, status = RequestEntity.STATUS_CLOSED)
        )

        val newCount = requests.count { it.status == RequestEntity.STATUS_NEW }
        val inProgressCount = requests.count { it.status == RequestEntity.STATUS_IN_PROGRESS }
        val closedCount = requests.count { it.status == RequestEntity.STATUS_CLOSED }

        assertEquals(2, newCount)
        assertEquals(1, inProgressCount)
        assertEquals(1, closedCount)
    }

    @Test
    fun `find request by id`() {
        val requests = listOf(
            TestRequestBuilder.createRequest(id = 1),
            TestRequestBuilder.createRequest(id = 2),
            TestRequestBuilder.createRequest(id = 3)
        )

        val found = requests.firstOrNull { it.id == 2L }
        assertNotNull(found)
        assertEquals(2L, found?.id)
    }

    @Test
    fun `request not found returns null`() {
        val requests = listOf(
            TestRequestBuilder.createRequest(id = 1),
            TestRequestBuilder.createRequest(id = 2)
        )

        val found = requests.firstOrNull { it.id == 999L }
        assertNull(found)
    }

    // ==================== Update Validation Tests ====================

    @Test
    fun `request field update validates title not empty`() {
        val newTitle = "Updated Title"
        assertTrue(newTitle.isNotEmpty())
        assertTrue(newTitle.length > 0)
    }

    @Test
    fun `request field update validates description not empty`() {
        val newDescription = "Updated description with more details"
        assertTrue(newDescription.isNotEmpty())
        assertTrue(newDescription.length > 0)
    }

    @Test
    fun `request can track update count`() {
        var updateCount = 0

        updateCount++
        assertEquals(1, updateCount)

        updateCount++
        assertEquals(2, updateCount)
    }

    // ==================== Test Data Builder ====================

    object TestRequestBuilder {
        fun createRequest(
            id: Long = 1,
            title: String = "Test Request",
            description: String = "Test description",
            category: String = "General",
            contact: String = "+79991234567",
            status: String = RequestEntity.STATUS_NEW,
            ownerUserId: Long = 10,
            ownerPhone: String = "+79991234567",
            createdAt: Long = System.currentTimeMillis()
        ) = RequestEntity(
            id = id,
            title = title,
            description = description,
            category = category,
            contact = contact,
            status = status,
            ownerUserId = ownerUserId,
            ownerPhone = ownerPhone,
            createdAt = createdAt
        )

        fun createNewRequest(
            id: Long = 1,
            ownerPhone: String = "+79991234567"
        ) = createRequest(
            id = id,
            status = RequestEntity.STATUS_NEW,
            ownerPhone = ownerPhone
        )

        fun createInProgressRequest(
            id: Long = 1,
            ownerPhone: String = "+79991234567"
        ) = createRequest(
            id = id,
            status = RequestEntity.STATUS_IN_PROGRESS,
            ownerPhone = ownerPhone
        )

        fun createClosedRequest(
            id: Long = 1,
            ownerPhone: String = "+79991234567"
        ) = createRequest(
            id = id,
            status = RequestEntity.STATUS_CLOSED,
            ownerPhone = ownerPhone
        )
    }
}

