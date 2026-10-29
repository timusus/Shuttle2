package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-61-62-test"

@RunWith(AndroidJUnit4::class)
class Migration61To62Test {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MediaDatabase::class.java
        )

    @Test
    fun `migrate 61 to 62 gives copies of one album track one blocking hash and keeps play stats`() {
        helper.createDatabase(TEST_DB, 61).apply {
            insertSong("/music/a.flac", "Shuttle", "'OK Computer'", "1", "2", playCount = 7)
            insertSong("emby://item/a", "Emby", "'ok computer'", "NULL", "2", playCount = 3)
            insertSong("/music/b.flac", "Shuttle", "'OK Computer'", "1", "3", playCount = 0)
            insertSong("/music/c.flac", "Shuttle", "'OK Computer'", "1", "NULL", playCount = 0)
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 62, true, MIGRATION_61_62)

        val rows = mutableListOf<Pair<Long?, Int>>()
        migrated.query("SELECT copyBlockingHash, playCount FROM songs ORDER BY id").use { cursor ->
            while (cursor.moveToNext()) rows += (if (cursor.isNull(0)) null else cursor.getLong(0)) to cursor.getInt(1)
        }
        rows.map { it.second } shouldBe listOf(7, 3, 0, 0)
        val (localTrack2, embyTrack2, track3, untracked) = rows.map { it.first }
        localTrack2 shouldNotBe null
        embyTrack2 shouldBe localTrack2
        track3 shouldNotBe null
        track3 shouldNotBe localTrack2
        untracked shouldBe null
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertSong(
        path: String,
        provider: String,
        album: String,
        disc: String,
        track: String,
        playCount: Int
    ) = execSQL(
        "INSERT INTO songs (name, track, disc, duration, year, genres, path, albumArtist, artists, album, size, mimeType, lastModified, playbackPosition, playCount, lastPlayed, blacklisted, mediaProvider, lyrics, grouping, bitRate, bitDepth, sampleRate, channelCount, favouritedAt) " +
            "VALUES ('Song', $track, $disc, 180000, NULL, '', '$path', 'Artist', 'Artist', $album, 0, 'audio/flac', 0, 0, $playCount, NULL, 0, '$provider', NULL, NULL, NULL, NULL, NULL, NULL, NULL)"
    )
}
