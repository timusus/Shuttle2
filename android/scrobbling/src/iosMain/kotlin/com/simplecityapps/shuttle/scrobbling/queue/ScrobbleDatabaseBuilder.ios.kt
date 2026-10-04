@file:OptIn(ExperimentalForeignApi::class)

package com.simplecityapps.shuttle.scrobbling.queue

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** [ScrobbleDatabase] in the app's Application Support directory, on the bundled SQLite (iOS has no framework SQLite for Room). */
fun scrobbleDatabaseBuilder(): RoomDatabase.Builder<ScrobbleDatabase> = Room
    .databaseBuilder<ScrobbleDatabase>(name = applicationSupportDirectory() + "/" + ScrobbleDatabase.DATABASE_NAME)
    .setDriver(BundledSQLiteDriver())

/** [ScrobbleDatabase] in memory, on the bundled SQLite: empty each time, for a graph that mustn't see the app's queue. */
fun inMemoryScrobbleDatabaseBuilder(): RoomDatabase.Builder<ScrobbleDatabase> = Room
    .inMemoryDatabaseBuilder<ScrobbleDatabase>()
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
