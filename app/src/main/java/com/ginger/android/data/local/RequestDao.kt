package com.ginger.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.paging.PagingSource
import com.ginger.android.data.local.RequestEntity

@Dao
interface RequestDao {

    @Query("SELECT * FROM requests ORDER BY created_at DESC")
    fun getAll(): List<RequestEntity>

    @Query("SELECT * FROM requests WHERE status = :status ORDER BY created_at DESC")
    fun getByStatus(status: String): List<RequestEntity>

    @Query("SELECT * FROM requests WHERE owner_phone = :ownerPhone ORDER BY created_at DESC")
    fun getByOwnerPhone(ownerPhone: String): List<RequestEntity>

    @Query("SELECT * FROM requests WHERE owner_phone = :ownerPhone AND status = :status ORDER BY created_at DESC")
    fun getByOwnerPhoneAndStatus(ownerPhone: String, status: String): List<RequestEntity>

    // Paging-enabled DAO methods
    @Query("SELECT * FROM requests ORDER BY created_at DESC")
    fun getAllPaging(): PagingSource<Int, RequestEntity>

    @Query("SELECT * FROM requests WHERE status = :status ORDER BY created_at DESC")
    fun getByStatusPaging(status: String): PagingSource<Int, RequestEntity>

    @Query("SELECT * FROM requests WHERE owner_phone = :ownerPhone ORDER BY created_at DESC")
    fun getByOwnerPhonePaging(ownerPhone: String): PagingSource<Int, RequestEntity>

    @Query("SELECT * FROM requests WHERE owner_phone = :ownerPhone AND status = :status ORDER BY created_at DESC")
    fun getByOwnerPhoneAndStatusPaging(ownerPhone: String, status: String): PagingSource<Int, RequestEntity>

    @Query("SELECT * FROM requests WHERE id = :id LIMIT 1")
    fun getById(id: Long): RequestEntity?

    @Insert
    fun insert(request: RequestEntity)

    @Update
    fun update(request: RequestEntity)

    @Query(
        """
        UPDATE requests SET 
            title = :title, 
            description = :description, 
            category = :category, 
            contact = :contact, 
            status = :status 
        WHERE id = :id
        """
    )
    fun updateFields(
        id: Long,
        title: String,
        description: String,
        category: String,
        contact: String,
        status: String
    ): Int

    @Query("DELETE FROM requests WHERE id = :id")
    fun deleteById(id: Long)
}
