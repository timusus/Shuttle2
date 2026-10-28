package com.simplecityapps.localmediaprovider.local.data.room.database

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.simplecityapps.localmediaprovider.local.data.room.entity.IDENTITY_GENERATION_TABLE
import com.simplecityapps.localmediaprovider.local.data.room.entity.LIBRARY_GENERATION_TABLE
import com.simplecityapps.localmediaprovider.local.data.room.entity.SONG_COLUMNS
import com.simplecityapps.localmediaprovider.local.data.room.entity.SONG_IDENTITY_QUERY
import com.simplecityapps.localmediaprovider.local.data.room.entity.SONG_STATS_COLUMNS

/**
 * Installs, on each open, the triggers that bump `identity_generation` when a song is added or removed, or one of the
 * columns [SONG_IDENTITY_QUERY] reads changes value, then seeds its one row; and likewise `library_generation`, for
 * any column the song lists read but play stats and favourites. A row is only there once its triggers are, so a
 * database opened without this callback has no generation, and what's built on one reads afresh rather than going stale.
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

    /** The columns the song lists read, besides `id` and those a play or a favourite writes: what bumps `library_generation`. */
    internal val libraryColumns: List<String> = SONG_COLUMNS
        .split(",")
        .map { it.trim() }
        .minus(SONG_STATS_COLUMNS.split(",").map { it.trim() }.toSet() + "id")

    private const val BUMP = "UPDATE $IDENTITY_GENERATION_TABLE SET generation = generation + 1"

    private const val BUMP_LIBRARY = "UPDATE $LIBRARY_GENERATION_TABLE SET generation = generation + 1"

    private val statements = listOf(
        "CREATE TRIGGER IF NOT EXISTS songs_identity_insert AFTER INSERT ON songs BEGIN $BUMP; END",
        "CREATE TRIGGER IF NOT EXISTS songs_identity_delete AFTER DELETE ON songs BEGIN $BUMP; END",
        // Recreated on every open: the trigger persists in the file, and its columns follow SONG_IDENTITY_QUERY.
        "DROP TRIGGER IF EXISTS songs_identity_update",
        "CREATE TRIGGER songs_identity_update AFTER UPDATE OF ${identityColumns.joinToString { "`$it`" }} ON songs " +
            "WHEN ${identityColumns.joinToString(" OR ") { "OLD.`$it` IS NOT NEW.`$it`" }} BEGIN $BUMP; END",
        "INSERT OR IGNORE INTO $IDENTITY_GENERATION_TABLE (id, generation) VALUES (0, 0)",
        "CREATE TRIGGER IF NOT EXISTS songs_library_insert AFTER INSERT ON songs BEGIN $BUMP_LIBRARY; END",
        "CREATE TRIGGER IF NOT EXISTS songs_library_delete AFTER DELETE ON songs BEGIN $BUMP_LIBRARY; END",
        "DROP TRIGGER IF EXISTS songs_library_update",
        "CREATE TRIGGER songs_library_update AFTER UPDATE OF ${libraryColumns.joinToString { "`$it`" }} ON songs " +
            "WHEN ${libraryColumns.joinToString(" OR ") { "OLD.`$it` IS NOT NEW.`$it`" }} BEGIN $BUMP_LIBRARY; END",
        "INSERT OR IGNORE INTO $LIBRARY_GENERATION_TABLE (id, generation) VALUES (0, 0)"
    )
}

/** [builder] with the album identity and library triggers installed on open: every real database is built this way. */
fun RoomDatabase.Builder<MediaDatabase>.trackingIdentityChanges(): RoomDatabase.Builder<MediaDatabase> = addCallback(IdentityGenerationTriggers)
