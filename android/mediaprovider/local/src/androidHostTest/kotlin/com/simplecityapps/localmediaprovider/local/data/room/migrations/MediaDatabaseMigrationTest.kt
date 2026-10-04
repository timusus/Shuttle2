package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.testing.MigrationTestHelper
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-chain-test"

// 23.json was never exported, so the chain starts at the oldest schema we can actually load.
private const val OLDEST_EXPORTED_SCHEMA_VERSION = 24

@RunWith(AndroidJUnit4::class)
class MediaDatabaseMigrationTest {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MediaDatabase::class.java
        )

    @Test
    fun `migrate through every registered migration from the oldest exported schema`() {
        helper.createDatabase(TEST_DB, OLDEST_EXPORTED_SCHEMA_VERSION).close()

        val chain = ALL_MIGRATIONS.filter { it.startVersion >= OLDEST_EXPORTED_SCHEMA_VERSION }
        val latestVersion = ALL_MIGRATIONS.last().endVersion

        helper.runMigrationsAndValidate(TEST_DB, latestVersion, true, *chain.toTypedArray())
    }

    @Test
    fun `the registered migrations reach the current database version`() {
        helper.createDatabase(TEST_DB, OLDEST_EXPORTED_SCHEMA_VERSION).close()

        // No destructive fallback: a missing migration to MediaDatabase's current version throws on open.
        val database =
            Room.databaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MediaDatabase::class.java, TEST_DB)
                .addMigrations(*ALL_MIGRATIONS)
                .allowMainThreadQueries()
                .build()
        try {
            database.openHelper.writableDatabase
        } finally {
            database.close()
        }
    }
}
