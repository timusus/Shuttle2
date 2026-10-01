package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * `identity_generation`: what the album index rebuilds by, so a play no longer rebuilds it (#688). Starts empty: its row
 * and the songs triggers that bump it come with `IdentityGenerationTriggers` on open.
 */
val MIGRATION_51_52 =
    object : Migration(51, 52) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `identity_generation` (`id` INTEGER NOT NULL, `generation` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
        }
    }
