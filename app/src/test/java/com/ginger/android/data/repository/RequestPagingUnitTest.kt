package com.ginger.android.data.repository

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.ginger.android.data.local.RequestEntity
import com.ginger.android.data.local.RequestDao
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

class RequestPagingUnitTest {

    class InMemoryPagingSource<T : Any>(private val items: List<T>) : PagingSource<Int, T>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
            val page = params.key ?: 0
            val from = page * params.loadSize
            val to = kotlin.math.min(from + params.loadSize, items.size)
            if (from >= items.size) {
                return LoadResult.Page<Int, T>(emptyList(), prevKey = if (page == 0) null else page - 1, nextKey = null)
            }
            val data = items.subList(from, to)
            val nextKey = if (to >= items.size) null else page + 1
            return LoadResult.Page<Int, T>(data, prevKey = if (page == 0) null else page - 1, nextKey = nextKey)
        }

        override fun getRefreshKey(state: PagingState<Int, T>): Int? = null
    }

    @Test
    fun repository_selects_owner_paging_source_correctly() = runTest {
        val sample = listOf(
            RequestEntity(id = 1, title = "t1", description = "d", category = "c", contact = "+100", status = RequestEntity.STATUS_NEW, ownerUserId = 1, ownerPhone = "+100", createdAt = 1000),
            RequestEntity(id = 2, title = "t2", description = "d2", category = "c", contact = "+100", status = RequestEntity.STATUS_NEW, ownerUserId = 1, ownerPhone = "+100", createdAt = 2000)
        )

        var ownerPagingCalled = false

        val fakeDao = object : RequestDao {
            override fun getByOwnerPhonePaging(ownerPhone: String): PagingSource<Int, RequestEntity> {
                assertEquals("+100", ownerPhone)
                ownerPagingCalled = true
                return InMemoryPagingSource(sample)
            }

            override fun getByOwnerPhoneAndStatusPaging(ownerPhone: String, status: String): PagingSource<Int, RequestEntity> {
                assertEquals("+100", ownerPhone)
                assertEquals(RequestEntity.STATUS_NEW, status)
                return InMemoryPagingSource(sample.filter { it.status == status })
            }

            // other methods unimplemented / no-op for test
            override fun getAll(): List<RequestEntity> = throw NotImplementedError()
            override fun getByStatus(status: String): List<RequestEntity> = throw NotImplementedError()
            override fun getByOwnerPhone(ownerPhone: String): List<RequestEntity> = throw NotImplementedError()
            override fun getByOwnerPhoneAndStatus(ownerPhone: String, status: String): List<RequestEntity> = throw NotImplementedError()
            override fun getAllPaging(): PagingSource<Int, RequestEntity> = throw NotImplementedError()
            override fun getByStatusPaging(status: String): PagingSource<Int, RequestEntity> = throw NotImplementedError()
            override fun getById(id: Long): RequestEntity? = throw NotImplementedError()
            override fun insert(request: RequestEntity) { /* no-op */ }
            override fun update(request: RequestEntity) { /* no-op */ }
            override fun updateFields(id: Long, title: String, description: String, category: String, contact: String, status: String): Int = throw NotImplementedError()
            override fun deleteById(id: Long) { /* no-op */ }
        }

        val repo = RequestRepository(fakeDao)
        val pagingSource = repo.pagingSourceFactoryForOwner("+100")()
        val params = PagingSource.LoadParams.Refresh<Int>(key = 0, loadSize = 20, placeholdersEnabled = false)
        val result = pagingSource.load(params)

        assertTrue(result is PagingSource.LoadResult.Page)
        val page = result as PagingSource.LoadResult.Page<Int, RequestEntity>
        assertEquals(2, page.data.size)
        assertEquals("+100", page.data[0].ownerPhone)
        assertTrue(ownerPagingCalled)
    }

    @Test
    fun inMemoryPagingSource_loads_without_pager() = runTest {
        val sample = listOf(
            RequestEntity(id = 1, title = "t1", description = "d", category = "c", contact = "+100", status = RequestEntity.STATUS_NEW, ownerUserId = 1, ownerPhone = "+100", createdAt = 1000),
            RequestEntity(id = 2, title = "t2", description = "d2", category = "c", contact = "+100", status = RequestEntity.STATUS_NEW, ownerUserId = 1, ownerPhone = "+100", createdAt = 2000)
        )

        val pagingSource = InMemoryPagingSource(sample)
        val params = PagingSource.LoadParams.Refresh<Int>(key = 0, loadSize = 20, placeholdersEnabled = false)
        val result = pagingSource.load(params)

        assertTrue(result is PagingSource.LoadResult.Page)
        val page = result as PagingSource.LoadResult.Page<Int, RequestEntity>
        assertEquals(2, page.data.size)
        assertEquals("t1", page.data[0].title)
    }
}

