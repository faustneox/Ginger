package com.ginger.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessage): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<ChatMessage>): List<Long>

    @Query("SELECT * FROM chat_messages WHERE request_id = :requestId ORDER BY created_at ASC")
    fun getMessagesStream(requestId: Long): Flow<List<ChatMessage>>

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM chat_messages WHERE firestore_id = :firestoreId")
    suspend fun deleteByFirestoreId(firestoreId: String)
}
