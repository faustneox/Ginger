package com.ginger.android.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ginger.android.R

@Entity(
    tableName = "requests",
    indices = [
        Index(value = ["status"]),
        Index(value = ["owner_user_id"]),
        Index(value = ["owner_user_id", "status"]),
        Index(value = ["owner_phone"]),
        Index(value = ["owner_phone", "status"])
    ]
)
data class RequestEntity(
    @PrimaryKey(autoGenerate = true)
    @JvmField var id: Long = 0,

    @ColumnInfo(name = "title")
    @JvmField var title: String,

    @ColumnInfo(name = "description")
    @JvmField var description: String,

    @ColumnInfo(name = "category")
    @JvmField var category: String,

    @ColumnInfo(name = "contact")
    @JvmField var contact: String,

    @ColumnInfo(name = "status")
    @JvmField var status: String,

    /** Внутренний id пользователя-владельца (дублирует связь по телефону). */
    @ColumnInfo(name = "owner_user_id")
    @JvmField var ownerUserId: Long,

    /** Нормализованный телефон владельца заявки (основная привязка к пользователю). */
    @ColumnInfo(name = "owner_phone")
    @JvmField var ownerPhone: String,

    @ColumnInfo(name = "created_at")
    @JvmField var createdAt: Long
) {
    /** Конструктор, который использовался в старом Java-коде */
    @Ignore
    constructor(
        title: String,
        description: String,
        category: String,
        contact: String,
        status: String,
        ownerUserId: Long,
        ownerPhone: String,
        createdAt: Long
    ) : this(
        id = 0,
        title = title,
        description = description,
        category = category,
        contact = contact,
        status = status,
        ownerUserId = ownerUserId,
        ownerPhone = ownerPhone,
        createdAt = createdAt
    )

    companion object {
        const val STATUS_NEW = "Новая"
        const val STATUS_IN_PROGRESS = "В работе"
        const val STATUS_CLOSED = "Завершена"

        /**
         * Возвращает drawable-ресурс для отображения чипа статуса.
         * Единственный источник истины — используется в адаптере и DetailActivity.
         */
        @JvmStatic
        fun statusChipDrawable(status: String): Int {
            return when (status) {
                STATUS_NEW -> R.drawable.status_chip_new
                STATUS_IN_PROGRESS -> R.drawable.status_chip_progress
                else -> R.drawable.status_chip_closed
            }
        }
    }
}
