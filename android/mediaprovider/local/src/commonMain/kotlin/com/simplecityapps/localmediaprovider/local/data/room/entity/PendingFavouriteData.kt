package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.simplecityapps.shuttle.model.MediaProviderType
import kotlin.time.Instant

/**
 * A favourite/unfavourite made on a remote-provider song, not yet pushed to its server (#497): the local outbox. Keyed
 * by [songId] alone, so a later toggle before this row is flushed (a later slice) replaces it rather than piling up -
 * only the latest desired state is ever sent. [mediaProvider] and [externalId] are copied from the song at write time
 * so the writer that drains this table doesn't need to look the song back up, and so a row survives even if the song
 * itself is later removed from a different provider context. Deleted along with its song ([ForeignKey.CASCADE]).
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
