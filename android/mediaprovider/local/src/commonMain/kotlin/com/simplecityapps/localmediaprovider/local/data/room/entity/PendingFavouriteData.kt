package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import kotlin.time.Instant

/** A favourite toggle on a remote song not yet sent to its server; a sync's merge skips songs with one, so it isn't undone. */
@Entity(
    tableName = "pending_favourites",
    foreignKeys = [
        ForeignKey(
            entity = SongData::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PendingFavouriteData(
    @PrimaryKey val songId: Long,
    val favourite: Boolean,
    val changedAt: Instant
)
