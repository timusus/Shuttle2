package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.simplecityapps.localmediaprovider.local.data.room.entity.copyBlockingHash

/** Adds each song's copy blocking hash, computed here for the songs already stored since SQL can't normalise an album. */
val MIGRATION_61_62 =
    object : Migration(61, 62) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `songs` ADD COLUMN `copyBlockingHash` INTEGER")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_copyBlockingHash` ON `songs` (`copyBlockingHash`)")

            val hashes = mutableListOf<Pair<Long, Long>>()
            connection.prepare("SELECT `id`, `album`, `disc`, `track` FROM `songs`").use { statement ->
                while (statement.step()) {
                    val album = if (statement.isNull(1)) null else statement.getText(1)
                    val disc = if (statement.isNull(2)) null else statement.getInt(2)
                    val track = if (statement.isNull(3)) null else statement.getInt(3)
                    copyBlockingHash(album, disc, track)?.let { hash -> hashes += statement.getLong(0) to hash }
                }
            }
            connection.prepare("UPDATE `songs` SET `copyBlockingHash` = ? WHERE `id` = ?").use { statement ->
                hashes.forEach { (id, hash) ->
                    statement.bindLong(1, hash)
                    statement.bindLong(2, id)
                    statement.step()
                    statement.reset()
                }
            }
        }
    }
