package com.ginger.android.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    companion object {
        private const val TEST_DB = "migration-test"
    }

    @Test
    fun migrate_9_to_10_manual() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dbFile = ctx.getDatabasePath(TEST_DB)
        dbFile.delete()

        // Create DB at version 9 and insert duplicate-phone rows
        val sqliteDb = ctx.openOrCreateDatabase(TEST_DB, Context.MODE_PRIVATE, null)
        sqliteDb.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, full_name TEXT NOT NULL, phone TEXT, password TEXT, is_admin INTEGER NOT NULL, google_id TEXT, google_email TEXT)")
        sqliteDb.execSQL("INSERT INTO users (id, full_name, phone, password, is_admin) VALUES (1, 'Alice', '+79991234567', 'hash', 0)")
        sqliteDb.execSQL("INSERT INTO users (id, full_name, phone, password, is_admin) VALUES (2, 'Bob', '+79991234567', 'hash', 0)")
        sqliteDb.execSQL("PRAGMA user_version = 9")
        sqliteDb.close()

        // Open with SupportSQLite helper and run migration logic
        val configuration = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(TEST_DB)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(9) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val db = helper.writableDatabase

        // Apply migration 9 -> 10 (same SQL as in AppDatabase)
        db.execSQL("CREATE TABLE users_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, full_name TEXT NOT NULL, phone TEXT, password TEXT, is_admin INTEGER NOT NULL, google_id TEXT, google_email TEXT)")
        db.execSQL("INSERT INTO users_new (id, full_name, phone, password, is_admin, google_id, google_email) SELECT id, full_name, phone, password, is_admin, google_id, google_email FROM users WHERE id IN (SELECT MIN(id) FROM users GROUP BY phone)")
        db.execSQL("DROP TABLE users")
        db.execSQL("ALTER TABLE users_new RENAME TO users")

        db.close()
        helper.close()

        // Verify duplicates removed
        val verifyDb = ctx.openOrCreateDatabase(TEST_DB, Context.MODE_PRIVATE, null)
        val cursor = verifyDb.rawQuery("SELECT COUNT(*) FROM users WHERE phone = '+79991234567'", null)
        cursor.moveToFirst()
        val count = cursor.getInt(0)
        cursor.close()
        verifyDb.close()

        assertEquals(1, count)
    }
}
