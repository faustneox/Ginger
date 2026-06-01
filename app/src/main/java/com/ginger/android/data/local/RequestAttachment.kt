package com.ginger.android.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "request_attachments",
    indices = [Index(value = ["request_id"]) ]
)
data class RequestAttachment(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "request_id")
    val requestId: Long,

    @ColumnInfo(name = "uri")
    val uri: String,

    @ColumnInfo(name = "mime_type")
    val mimeType: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
