package com.ginger.android.migration

import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ginger.android.data.local.AppDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class Migration16To17Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName
    )

    private val MIGRATION_16_17_TEST = object : Migration(16, 17) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("UPDATE chat_messages SET firestore_id = NULL WHERE firestore_id = ''")
            database.execSQL("""
                DELETE FROM chat_messages
                WHERE id NOT IN (
                  SELECT MIN(id) FROM chat_messages
                  WHERE firestore_id IS NOT NULL
                  GROUP BY firestore_id
                )
            """.trimIndent())
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_chat_messages_firestore_id ON chat_messages(firestore_id)")
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate16To17_dedupesFirestoreIdAndCreatesUniqueIndex() {
        val dbName = "test-migration.db"

        // Create DB with version 16 schema and insert records with duplicate firestore_id
        val db = helper.createDatabase(dbName, 16).apply {
            execSQL("""
                CREATE TABLE IF NOT EXISTS chat_messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    firestore_id TEXT,
                    request_id INTEGER NOT NULL,
                    client_id INTEGER NOT NULL,
                    user_id INTEGER NOT NULL,
                    sender_name TEXT,
                    sender_avatar_url TEXT,
                    text TEXT,
                    attachment_url TEXT,
                    created_at INTEGER NOT NULL
                )
            """.trimIndent())

            execSQL("INSERT INTO chat_messages (firestore_id, request_id, client_id, user_id, text, created_at) VALUES ('doc1', 1, 1, 1, 'hello', 1)")
            execSQL("INSERT INTO chat_messages (firestore_id, request_id, client_id, user_id, text, created_at) VALUES ('doc1', 1, 1, 1, 'duplicate', 2)")
            execSQL("INSERT INTO chat_messages (firestore_id, request_id, client_id, user_id, text, created_at) VALUES ('', 1, 1, 1, 'empty', 3)")
            close()
        }

        // Run migration and validate
        helper.runMigrationsAndValidate(dbName, 17, true, MIGRATION_16_17_TEST)
    }
}
