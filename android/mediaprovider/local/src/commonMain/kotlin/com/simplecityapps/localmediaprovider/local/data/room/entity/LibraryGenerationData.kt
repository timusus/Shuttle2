package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The library's generation: one row, whose [generation] SQLite triggers on the songs table bump whenever a song is added
 * or removed or a column besides its play stats and favourite changes value (see `IdentityGenerationTriggers`). A play,
 * a playback position or a favourite leaves it be, so what's built from the whole library isn't re-read for one.
 */
@Entity(tableName = LIBRARY_GENERATION_TABLE)
data class LibraryGenerationData(
    @PrimaryKey @ColumnInfo(name = "id") val id: Int,
    @ColumnInfo(name = "generation") val generation: Long
)

const val LIBRARY_GENERATION_TABLE = "library_generation"
