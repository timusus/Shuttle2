package com.simplecityapps.localmediaprovider.local.data.room

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.database.trackingIdentityChanges
import com.simplecityapps.localmediaprovider.local.data.room.migrations.ALL_MIGRATIONS
import com.simplecityapps.shuttle.logging.Logger
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.TimeSource

/** The database's file name, the same on every platform. */
const val DATABASE_NAME = "song.db"

/** The oldest version [ALL_MIGRATIONS] can migrate from; earlier databases predate the migration chain. */
private const val OLDEST_MIGRATABLE_VERSION = 23

/**
 * Opens [MediaDatabase] from a platform's [builder] (see `databaseBuilder`): every migration registered. A missing
 * migration fails loudly rather than wiping play history, favourites and playlists; only databases older than
 * [OLDEST_MIGRATABLE_VERSION], which have no migration path, and downgrades are recreated empty.
 */
class DatabaseProvider(
    private val builder: RoomDatabase.Builder<MediaDatabase>
) {
    val database: MediaDatabase by lazy {
        val started = TimeSource.Monotonic.markNow()
        builder
            .addMigrations(*ALL_MIGRATIONS)
            .fallbackToDestructiveMigrationFrom(false, *(1 until OLDEST_MIGRATABLE_VERSION).toList().toIntArray())
            // A newer on-disk database than the app knows (e.g. sideloaded older APK) is recreated, not crash-looped.
            .fallbackToDestructiveMigrationOnDowngrade(false)
            .trackingIdentityChanges()
            .addCallback(OpenTiming(started))
            .build()
            // build() only sets Room up; the file opens on the first query (OpenTiming logs that).
            .also { logger.info { "Database: builder set up in ${started.elapsedNow()}" } }
    }

    /**
     * Logs when Room first opens the database (the file opened, its schema validated or migrated), which the first
     * query waits for, timed from the build: the cold-start measurements read it (docs/performance/ios-startup.md). Once,
     * whichever thread opens it first.
     */
    @OptIn(ExperimentalAtomicApi::class)
    private class OpenTiming(
        private val built: TimeSource.Monotonic.ValueTimeMark
    ) : RoomDatabase.Callback() {
        private val opened = AtomicBoolean(false)

        override fun onOpen(connection: SQLiteConnection) {
            if (!opened.compareAndSet(false, true)) return
            logger.info { "Database: opened ${built.elapsedNow()} after the build began" }
        }
    }

    private companion object {
        val logger = Logger.tagged("Startup")
    }
}
