@file:OptIn(ExperimentalForeignApi::class)

package com.simplecityapps.localmediaprovider.local.data.room

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * [MediaDatabase] in the app's Application Support directory (not Documents, which the Files app can show), on the
 * bundled SQLite: iOS has no framework SQLite for Room to use.
 * iOS starts at the current schema version, so there's no pre-existing database for the migrations to replay on.
 */
fun databaseBuilder(): RoomDatabase.Builder<MediaDatabase> = Room
    .databaseBuilder<MediaDatabase>(name = applicationSupportDirectory() + "/" + DATABASE_NAME)
    .setDriver(BundledSQLiteDriver())

/** [MediaDatabase] in memory, on the bundled SQLite: empty each time, for tests that mustn't see the app's library. */
fun inMemoryDatabaseBuilder(): RoomDatabase.Builder<MediaDatabase> = Room
    .inMemoryDatabaseBuilder<MediaDatabase>()
    .setDriver(BundledSQLiteDriver())

/** Created on first launch: unlike Documents, iOS doesn't make Application Support until an app asks for it. */
private fun applicationSupportDirectory(): String {
    val directory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null
    )
    return requireNotNull(directory?.path)
}
