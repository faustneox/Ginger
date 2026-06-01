package com.ginger.android.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    @JvmField var id: Long = 0,

    @ColumnInfo(name = "full_name")
    @JvmField var fullName: String,

    @ColumnInfo(name = "phone")
    @JvmField var phone: String?,

    @ColumnInfo(name = "password")
    @JvmField var password: String?,

    @ColumnInfo(name = "is_admin")
    @JvmField var isAdmin: Boolean = false,

    /** Google ID для авторизации через Google (опционально) */
    @ColumnInfo(name = "google_id")
    @JvmField var googleId: String? = null,

    /** Email Google аккаунта */
    @ColumnInfo(name = "google_email")
    @JvmField var googleEmail: String? = null
) {
    /** Конструктор для создания обычного пользователя (телефон + пароль) */
    @Ignore
    constructor(
        fullName: String,
        phone: String,
        password: String,
        isAdmin: Boolean
    ) : this(
        id = 0,
        fullName = fullName,
        phone = phone,
        password = password,
        isAdmin = isAdmin,
        googleId = null,
        googleEmail = null
    )

    /** Конструктор для создания пользователя через Google Sign-In */
    @Ignore
    constructor(
        fullName: String,
        googleId: String,
        googleEmail: String
    ) : this(
        id = 0,
        fullName = fullName,
        phone = null,
        password = null,
        isAdmin = false,
        googleId = googleId,
        googleEmail = googleEmail
    )

    /** Конструктор, который использовался в LoginActivity для Google Sign-In */
    @Ignore
    constructor(
        fullName: String,
        phone: String?,
        password: String?,
        isAdmin: Boolean,
        googleId: String?,
        googleEmail: String?
    ) : this(
        id = 0,
        fullName = fullName,
        phone = phone,
        password = password,
        isAdmin = isAdmin,
        googleId = googleId,
        googleEmail = googleEmail
    )
}
