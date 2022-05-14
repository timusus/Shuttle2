package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE songs ADD COLUMN lyrics TEXT")
    }
}
