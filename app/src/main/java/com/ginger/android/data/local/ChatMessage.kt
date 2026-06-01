package com.ginger.android.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["request_id"]), Index(value = ["user_id"]) ]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "firestore_id")
    val firestoreId: String? = null,

    @ColumnInfo(name = "request_id")
    val requestId: Long,

    @ColumnInfo(name = "user_id")
    val userId: Long,

    @ColumnInfo(name = "text")
    val text: String? = null,

    @ColumnInfo(name = "attachment_url")
    val attachmentUrl: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
