package com.simplecityapps.shuttle.query

import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.SongFolder
import com.simplecityapps.shuttle.smartplaylist.SmartRules
import com.simplecityapps.shuttle.smartplaylist.SmartRulesContext
import com.simplecityapps.shuttle.smartplaylist.predicate
import com.simplecityapps.shuttle.sorting.SongSortOrder
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

sealed class SongQuery(
    open val predicate: (Song) -> Boolean,
    open val sortOrder: SongSortOrder = SongSortOrder.Default,
    open val includeExcluded: Boolean = false,
    open val providerType: MediaProviderType? = null
) {
    data class All(
        override val includeExcluded: Boolean = false,
        override val sortOrder: SongSortOrder = SongSortOrder.Default,
        override val providerType: MediaProviderType? = null
    ) : SongQuery(
        predicate = { true },
        sortOrder = sortOrder,
        includeExcluded = includeExcluded,
        providerType = providerType
    )

    data class ArtistGroupKey(
        val key: AlbumArtistGroupKey?
    ) : SongQuery(
        predicate = { song -> song.albumArtistGroupKey == key }
    )

    data class ArtistGroupKeys(
        private val artistGroupKeys: List<ArtistGroupKey>
    ) : SongQuery(
        predicate = { song -> artistGroupKeys.any { albumArtist -> albumArtist.predicate(song) } },
        sortOrder = SongSortOrder.Track
    )

    data class AlbumGroupKey(
        val key: com.simplecityapps.shuttle.model.AlbumGroupKey?
    ) : SongQuery(
        predicate = { song -> song.albumGroupKey == key }
    )

    data class AlbumGroupKeys(
        val albumGroupKeys: List<AlbumGroupKey>
    ) : SongQuery(
        predicate = { song -> albumGroupKeys.any { it.predicate(song) } },
        sortOrder = SongSortOrder.Track
    )

    /** The songs with these ids, in no particular order. */
    data class SongIds(
        val songIds: List<Long>
    ) : SongQuery(
        predicate = { song -> songIds.contains(song.id) }
    )

    /** Local songs in the folder at [path] (see [SongFolder]) or any of its subfolders. */
    data class Folder(
        val path: List<String>
    ) : SongQuery(
        predicate = { song -> !song.mediaProvider.remote && SongFolder.isUnder(song.path, path) }
    )

    data class LastPlayed(
        val after: Instant
    ) : SongQuery(
        predicate = { song -> song.lastPlayed?.let { it > after } ?: false },
        sortOrder = SongSortOrder.LastCompleted
    )

    data class LastCompleted(
        val after: Instant
    ) : SongQuery(
        predicate = { song -> song.lastCompleted?.let { it > after } ?: false },
        sortOrder = SongSortOrder.LastCompleted
    )

    data class Search(
        val query: String
    ) : SongQuery(
        predicate = { song -> song.name?.contains(query, true) ?: false || song.album?.contains(query, true) ?: false || song.albumArtist?.contains(query, true) ?: false }
    )

    data class PlayCount(
        val count: Int,
        override val sortOrder: SongSortOrder
    ) : SongQuery(
        predicate = { song -> song.playCount >= count },
        sortOrder = sortOrder
    )

    /** The favourite songs, most recently made one first. */
    data object Favourites : SongQuery(
        predicate = { song -> song.isFavourite },
        sortOrder = SongSortOrder.Favourited
    )

    /**
     * The songs matching a user smart playlist's [rules], in no particular order: `SongQuery` has neither a sort
     * direction nor a limit, so `EvaluateSmartPlaylist` sorts and limits them after.
     */
    data class Rules(
        val rules: SmartRules,
        val context: SmartRulesContext = SmartRulesContext()
    ) : SongQuery(
        predicate = rules.predicate(context)
    )

    // Todo: This isn't really 'recently added', any songs which have had their contents modified will show up here.
    //   Best to add a 'dateAdded' column.
    @OptIn(ExperimentalTime::class)
    data class RecentlyAdded(val days: Int = 14) :
        SongQuery(
            predicate = { song -> song.lastModified?.let { it > Clock.System.now().minus(days.days) } ?: false },
            sortOrder = SongSortOrder.LastModified
        ) // 2 weeks
}
