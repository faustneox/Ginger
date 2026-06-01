package com.ginger.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RequestAttachmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: RequestAttachment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(attachments: List<RequestAttachment>): List<Long>

    @Query("SELECT * FROM request_attachments WHERE request_id = :requestId")
    suspend fun getByRequestId(requestId: Long): List<RequestAttachment>

    @Query("DELETE FROM request_attachments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM request_attachments WHERE request_id = :requestId")
    suspend fun deleteByRequestId(requestId: Long)
}
