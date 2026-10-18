package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import kotlin.time.Clock

/** Every translation the Favorites playlist was created under, since a playlist keeps the name it was given. */
internal val FAVORITES_PLAYLIST_NAMES = listOf(
    "Favorites",
    "Favourites",
    "Favoriten",
    "Favorieten",
    "Favoriler",
    "Favoritos",
    "Preferiti",
    "Préféré",
    "Ulubione",
    "Избранное",
    "पसंदीदा",
    "お気に入り",
    "收藏",
    "收藏夹"
)

/** Turns Favourites playlists into a per-song favouritedAt flag, stamped so the most recently added song still sorts first. */
val MIGRATION_45_46 =
    object : Migration(45, 46) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `songs` ADD COLUMN `favouritedAt` INTEGER")

            val favorites = "SELECT `id` FROM `playlists` WHERE `mediaProvider` = 'Shuttle' AND `externalId` IS NULL AND `name` IN (" +
                FAVORITES_PLAYLIST_NAMES.joinToString(", ") { "?" } + ")"

            // Oldest entry first; a song in more than one of the playlists takes the place of its latest entry.
            val songIds = LinkedHashSet<Long>()
            connection.prepare("SELECT `songId` FROM `playlist_song_join` WHERE `playlistId` IN ($favorites) ORDER BY `playlistId`, `sortOrder`, `id`").use { statement ->
                statement.bindNames()
                while (statement.step()) {
                    val songId = statement.getLong(0)
                    songIds.remove(songId)
                    songIds.add(songId)
                }
            }
            val now = Clock.System.now().toEpochMilliseconds()
            connection.prepare("UPDATE `songs` SET `favouritedAt` = ? WHERE `id` = ?").use { statement ->
                songIds.toList().asReversed().forEachIndexed { index, songId ->
                    statement.bindLong(1, now - index)
                    statement.bindLong(2, songId)
                    statement.step()
                    statement.reset()
                }
            }

            connection.prepare("DELETE FROM `playlist_song_join` WHERE `playlistId` IN ($favorites)").use { statement ->
                statement.bindNames()
                statement.step()
            }
            connection.prepare("DELETE FROM `playlists` WHERE `id` IN ($favorites)").use { statement ->
                statement.bindNames()
                statement.step()
            }
        }
    }

/** Binds [FAVORITES_PLAYLIST_NAMES] to the statement's parameters, from the first. */
private fun SQLiteStatement.bindNames() {
    FAVORITES_PLAYLIST_NAMES.forEachIndexed { index, name -> bindText(index + 1, name) }
}
