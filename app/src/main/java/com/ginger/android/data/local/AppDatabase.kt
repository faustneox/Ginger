package com.ginger.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RequestEntity::class, UserEntity::class, RequestAttachment::class, ChatMessage::class],
    version = 12,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun requestDao(): RequestDao
    abstract fun userDao(): UserDao
    abstract fun requestAttachmentDao(): RequestAttachmentDao
    abstract fun chatMessageDao(): ChatMessageDao

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
        
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS request_attachments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        request_id INTEGER NOT NULL,
                        uri TEXT NOT NULL,
                        mime_type TEXT,
                        created_at INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS chat_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        firestore_id TEXT,
                        request_id INTEGER NOT NULL,
                        user_id INTEGER NOT NULL,
                        text TEXT,
                        attachment_url TEXT,
                        created_at INTEGER NOT NULL
                    )
                """.trimIndent())
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
                        .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
