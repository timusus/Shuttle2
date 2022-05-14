package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE songs ADD COLUMN grouping TEXT")
    }
}
