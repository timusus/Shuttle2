@file:OptIn(ExperimentalForeignApi::class)

package com.simplecityapps.localmediaprovider.local.data.room

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * [MediaDatabase] in the app's Documents directory, on the bundled SQLite: iOS has no framework SQLite for Room to use.
 * iOS starts at the current schema version, so there's no pre-existing database for the migrations to replay on.
 */
fun databaseBuilder(): RoomDatabase.Builder<MediaDatabase> = Room
    .databaseBuilder<MediaDatabase>(name = documentDirectory() + "/" + DATABASE_NAME)
    .setDriver(BundledSQLiteDriver())

private fun documentDirectory(): String {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null
    )
    return requireNotNull(documentDirectory?.path)
}
