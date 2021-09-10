package com.simplecityapps.shuttle.query

import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.parcel.Parcelable
import com.simplecityapps.shuttle.parcel.Parcelize
import com.simplecityapps.shuttle.sorting.SongSortOrder
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@Parcelize
open class SongQuery(
    val predicate: (Song) -> Boolean,
    val sortOrder: SongSortOrder = SongSortOrder.Default,
    val includeExcluded: Boolean = false,
    val providerType: MediaProviderType? = null
) : Parcelable {

    class All(includeExcluded: Boolean = false, sortOrder: SongSortOrder = SongSortOrder.Default, providerType: MediaProviderType? = null) :
        SongQuery(
            predicate = { true },
            sortOrder = sortOrder,
            includeExcluded = includeExcluded,
            providerType = providerType
        )

    class ArtistGroupKey(val key: AlbumArtistGroupKey?) :
        SongQuery(
            predicate = { song -> song.albumArtistGroupKey == key }
        )

    class ArtistGroupKeys(private val artistGroupKeys: List<ArtistGroupKey>) :
        SongQuery(
            predicate = { song -> artistGroupKeys.any { albumArtist -> albumArtist.predicate(song) } },
            sortOrder = SongSortOrder.Track
        )

    class AlbumGroupKey(val key: com.simplecityapps.shuttle.model.AlbumGroupKey?) :
        SongQuery(
            predicate = { song -> song.albumGroupKey == key }
        )

    class AlbumGroupKeys(val albumGroupKeys: List<AlbumGroupKey>) :
        SongQuery(
            predicate = { song -> albumGroupKeys.any { it.predicate(song) } },
            sortOrder = SongSortOrder.Track
        )

    class SongIds(val songIds: List<Long>) :
        SongQuery(
            predicate = { song -> songIds.contains(song.id) }
        )

    class LastPlayed(val after: Instant) :
        SongQuery(
            predicate = { song -> song.lastPlayed?.let { it > after } ?: false },
            sortOrder = SongSortOrder.LastCompleted
        )

    class LastCompleted(val after: Instant) :
        SongQuery(
            predicate = { song -> song.lastCompleted?.let { it > after } ?: false },
            sortOrder = SongSortOrder.LastCompleted
        )

    class Search(val query: String) :
        SongQuery(
            predicate = { song -> song.name?.contains(query, true) ?: false || song.album?.contains(query, true) ?: false || song.albumArtist?.contains(query, true) ?: false }
        )

    class PlayCount(val count: Int, sortOrder: SongSortOrder) :
        SongQuery(
            predicate = { song -> song.playCount >= count },
            sortOrder = sortOrder
        )

    // Todo: This isn't really 'recently added', any songs which have had their contents modified will show up here.
    //   Best to add a 'dateAdded' column.
    @OptIn(ExperimentalTime::class)
    class RecentlyAdded :
        SongQuery(
            predicate = { song -> song.lastModified?.let { it < Clock.System.now().minus(Duration.days(14)) } ?: false },
            sortOrder = SongSortOrder.LastModified
        ) // 2 weeks
}