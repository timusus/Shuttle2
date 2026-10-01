package com.simplecityapps.localmediaprovider.local.data.room.database

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.simplecityapps.localmediaprovider.local.data.room.entity.IDENTITY_GENERATION_TABLE
import com.simplecityapps.localmediaprovider.local.data.room.entity.SONG_IDENTITY_QUERY

/**
 * Installs, on each open, the triggers that bump `identity_generation` when a song is added or removed, or one of the
 * columns [SONG_IDENTITY_QUERY] reads changes value, then seeds its one row. The row is only there once the triggers
 * are, so a database opened without this callback has no generation, and the album index rebuilds on every read rather
 * than going stale.
 */
object IdentityGenerationTriggers : RoomDatabase.Callback() {
    override fun onOpen(connection: SQLiteConnection) {
        statements.forEach(connection::execSQL)
    }

    /** The columns besides `id` the album index is built from, read off the query so the two can't drift apart. */
    internal val identityColumns: List<String> = SONG_IDENTITY_QUERY
        .substringAfter("SELECT ")
        .substringBefore(" FROM")
        .split(",")
        .map { it.trim() }
        .filter { it != "id" }

    private const val BUMP = "UPDATE $IDENTITY_GENERATION_TABLE SET generation = generation + 1"

    private val statements = listOf(
        "CREATE TRIGGER IF NOT EXISTS songs_identity_insert AFTER INSERT ON songs BEGIN $BUMP; END",
        "CREATE TRIGGER IF NOT EXISTS songs_identity_delete AFTER DELETE ON songs BEGIN $BUMP; END",
        // Recreated on every open: the trigger persists in the file, and its columns follow SONG_IDENTITY_QUERY.
        "DROP TRIGGER IF EXISTS songs_identity_update",
        "CREATE TRIGGER songs_identity_update AFTER UPDATE OF ${identityColumns.joinToString { "`$it`" }} ON songs " +
            "WHEN ${identityColumns.joinToString(" OR ") { "OLD.`$it` IS NOT NEW.`$it`" }} BEGIN $BUMP; END",
        "INSERT OR IGNORE INTO $IDENTITY_GENERATION_TABLE (id, generation) VALUES (0, 0)"
    )
}

/** [builder] with the album identity triggers installed on open: every real database is built this way. */
fun RoomDatabase.Builder<MediaDatabase>.trackingIdentityChanges(): RoomDatabase.Builder<MediaDatabase> = addCallback(IdentityGenerationTriggers)
