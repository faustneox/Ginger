package com.ginger.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RequestEntity::class, UserEntity::class],
    version = 10,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun requestDao(): RequestDao
    abstract fun userDao(): UserDao

    companion object {
        private const val DB_NAME = "requests.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {}
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE users ADD COLUMN google_id TEXT")
                database.execSQL("ALTER TABLE users ADD COLUMN google_email TEXT")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE users_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, full_name TEXT NOT NULL, phone TEXT, password TEXT, is_admin INTEGER NOT NULL, google_id TEXT, google_email TEXT)")
                database.execSQL("INSERT INTO users_new (id, full_name, phone, password, is_admin, google_id, google_email) SELECT id, full_name, phone, password, is_admin, google_id, google_email FROM users WHERE id IN (SELECT MIN(id) FROM users GROUP BY phone)")
                database.execSQL("DROP TABLE users")
                database.execSQL("ALTER TABLE users_new RENAME TO users")
            }
        }

        @JvmStatic
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
