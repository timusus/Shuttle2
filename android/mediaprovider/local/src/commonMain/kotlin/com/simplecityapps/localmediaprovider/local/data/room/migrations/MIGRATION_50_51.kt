package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** `resume_points`: where each play context's queue was left, so Home's Jump back in can resume it (#670). Starts empty. */
val MIGRATION_50_51 =
    object : Migration(50, 51) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `resume_points` (" +
                    "`contextType` TEXT NOT NULL, " +
                    "`contextId` TEXT NOT NULL, " +
                    "`mediaProvider` TEXT NOT NULL, " +
                    "`songPath` TEXT NOT NULL, " +
                    "`positionMs` INTEGER NOT NULL, " +
                    "`track` INTEGER NOT NULL, " +
                    "`trackCount` INTEGER NOT NULL, " +
                    "`shuffled` INTEGER NOT NULL, " +
                    "`finished` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`contextType`, `contextId`))"
            )
        }
    }
