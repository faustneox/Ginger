package com.ginger.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface UserDao {

    @Insert
    fun insert(user: UserEntity): Long

    @Query("SELECT COUNT(*) FROM users WHERE phone = :phone")
    fun countByPhone(phone: String): Int

    @Query("SELECT * FROM users WHERE phone = :phone LIMIT 1")
    fun getByPhone(phone: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    fun getById(id: Long): UserEntity?

    @Query("SELECT * FROM users WHERE google_id = :googleId LIMIT 1")
    fun getByGoogleId(googleId: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users WHERE google_id = :googleId")
    fun countByGoogleId(googleId: String): Int

    @Query("SELECT COUNT(*) FROM users WHERE is_admin = 1")
    fun countAdmins(): Int

    @Query("SELECT * FROM users ORDER BY full_name COLLATE NOCASE ASC")
    fun getAllUsers(): List<UserEntity>

    @Query("UPDATE users SET is_admin = :isAdmin WHERE id = :userId")
    fun updateAdminStatus(userId: Long, isAdmin: Boolean)

    @Query("UPDATE users SET phone = :phone WHERE id = :userId")
    fun updatePhone(userId: Long, phone: String)
}
