package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.simplecityapps.shuttle.model.MediaProviderType
import kotlin.time.Instant

/**
 * A favourite/unfavourite made on a remote-provider song, not yet pushed to its server (#497): the local outbox. Keyed
 * by [songId] alone, so a later toggle before this row is sent replaces it rather than piling up -
 * only the latest desired state is ever sent. [mediaProvider] and [externalId] are a copy of the song's at write time;
 * the sender looks the song up by [songId] and doesn't read them. Deleted along with its song ([ForeignKey.CASCADE]).
 */
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
    val mediaProvider: MediaProviderType,
    val externalId: String?,
    val favourite: Boolean,
    val changedAt: Instant
)
