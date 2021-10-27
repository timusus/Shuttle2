package com.simplecityapps.shuttle.query

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.key.AlbumKey
import com.simplecityapps.shuttle.parcel.InstantParceler
import com.simplecityapps.shuttle.parcel.Parcelable
import com.simplecityapps.shuttle.parcel.Parcelize
import com.simplecityapps.shuttle.parcel.TypeParceler
import com.simplecityapps.shuttle.sorting.SongSortOrder
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

sealed class SongQuery(
    open val predicate: (Song) -> Boolean,
    open val sortOrder: SongSortOrder = SongSortOrder.Default,
    open val includeExcluded: Boolean = false,
    open val providerType: MediaProviderType? = null
) : Parcelable {

    @Parcelize
    data class All(
        override val includeExcluded: Boolean = false,
        override val sortOrder: SongSortOrder = SongSortOrder.Default,
        override val providerType: MediaProviderType? = null
    ) :
        SongQuery(
            predicate = { true },
            sortOrder = sortOrder,
            includeExcluded = includeExcluded,
            providerType = providerType
        )

    @Parcelize
    data class ArtistGroupKey(
        val key: AlbumArtistKey?
    ) :
        SongQuery(
            predicate = { song -> song.albumArtistGroupKey == key }
        )

    @Parcelize
    data class ArtistGroupKeys(
        private val artistGroupKeys: List<ArtistGroupKey>
    ) :
        SongQuery(
            predicate = { song -> artistGroupKeys.any { albumArtist -> albumArtist.predicate(song) } },
            sortOrder = SongSortOrder.Track
        )

    @Parcelize
    data class AlbumGroupKey(
        val key: AlbumKey?
    ) :
        SongQuery(
            predicate = { song -> song.albumGroupKey == key }
        )

    @Parcelize
    data class AlbumGroupKeys(
        val albumGroupKeys: List<AlbumGroupKey>
    ) :
        SongQuery(
            predicate = { song -> albumGroupKeys.any { it.predicate(song) } },
            sortOrder = SongSortOrder.Track
        )

    @Parcelize
    data class SongIds(
        val songIds: List<Long>
    ) :
        SongQuery(
            predicate = { song -> songIds.contains(song.id) }
        )

    @Parcelize
    @TypeParceler<Instant, InstantParceler>
    data class LastPlayed(
        val after: Instant
    ) :
        SongQuery(
            predicate = { song -> song.lastPlayed?.let { it > after } ?: false },
            sortOrder = SongSortOrder.LastCompleted
        )

    @Parcelize
    @TypeParceler<Instant, InstantParceler>
    data class LastCompleted(
        val after: Instant
    ) :
        SongQuery(
            predicate = { song -> song.lastCompleted?.let { it > after } ?: false },
            sortOrder = SongSortOrder.LastCompleted
        )

    @Parcelize
    data class Search(
        val query: String
    ) :
        SongQuery(
            predicate = { song -> song.name?.contains(query, true) ?: false || song.album?.contains(query, true) ?: false || song.albumArtist?.contains(query, true) ?: false }
        )

    @Parcelize
    data class PlayCount(
        val count: Int,
        override val sortOrder: SongSortOrder
    ) :
        SongQuery(
            predicate = { song -> song.playCount >= count },
            sortOrder = sortOrder
        )

    // Todo: This isn't really 'recently added', any songs which have had their contents modified will show up here.
    //   Best to add a 'dateAdded' column.
    @OptIn(ExperimentalTime::class)
    @Parcelize
    data class RecentlyAdded(val days: Int = 14) :
        SongQuery(
            predicate = { song -> song.dateModified?.let { it > Clock.System.now().minus(Duration.days(days)) } ?: false },
            sortOrder = SongSortOrder.LastModified
        ) // 2 weeks
}