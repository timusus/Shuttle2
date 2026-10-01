package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The library's identity generation: one row, whose [generation] SQLite triggers on the songs table bump whenever a
 * song's album identity can change (see `IdentityGenerationTriggers`). Play counts, favourites and exclusion leave it be.
 */
@Entity(tableName = IDENTITY_GENERATION_TABLE)
data class IdentityGenerationData(
    @PrimaryKey @ColumnInfo(name = "id") val id: Int,
    @ColumnInfo(name = "generation") val generation: Long
)

const val IDENTITY_GENERATION_TABLE = "identity_generation"
