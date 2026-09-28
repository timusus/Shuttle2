package com.simplecityapps.mediaprovider.repository.artists

import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.sorting.AlbumArtistSortOrder

sealed class AlbumArtistQuery(
    val predicate: ((AlbumArtist) -> Boolean),
    val sortOrder: AlbumArtistSortOrder = AlbumArtistSortOrder.Default
) {
    /** The Artists list (#637): everyone who is the album artist of an album, "Various Artists" (the compilations) too. */
    class All(sortOrder: AlbumArtistSortOrder = AlbumArtistSortOrder.Default) :
        AlbumArtistQuery(
            predicate = { artist -> artist.isAlbumArtist },
            sortOrder = sortOrder
        )

    /** Every artist, album artists and those only credited on songs (featured, on compilations) alike: what search finds. */
    class Credited(sortOrder: AlbumArtistSortOrder = AlbumArtistSortOrder.Default) :
        AlbumArtistQuery(
            predicate = { true },
            sortOrder = sortOrder
        )

    class AlbumArtistGroupKey(val key: com.simplecityapps.shuttle.model.AlbumArtistGroupKey?) :
        AlbumArtistQuery(
            predicate = { albumArtist -> albumArtist.groupKey == key }
        )

    /** Every artist whose name holds [query], credited-only artists too. */
    class Search(private val query: String) :
        AlbumArtistQuery(
            predicate = { albumArtist -> albumArtist.name?.contains(query, ignoreCase = true) ?: false }
        )

    class PlayCount(private val count: Int) :
        AlbumArtistQuery(
            predicate = { albumArtist -> albumArtist.playCount >= count },
            sortOrder = AlbumArtistSortOrder.PlayCount
        )
}
