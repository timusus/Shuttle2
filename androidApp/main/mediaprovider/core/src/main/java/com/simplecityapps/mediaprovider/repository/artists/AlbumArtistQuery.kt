package com.simplecityapps.mediaprovider.repository.artists

import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.sorting.AlbumArtistSortOrder


sealed class AlbumArtistQuery(
    val predicate: ((AlbumArtist) -> Boolean),
    val sortOrder: AlbumArtistSortOrder = AlbumArtistSortOrder.Default
) {
    class All(sortOrder: AlbumArtistSortOrder = AlbumArtistSortOrder.Default) :
        AlbumArtistQuery(
            predicate = { true },
            sortOrder = sortOrder
        )

    class AlbumArtistGroupKey(val key: com.simplecityapps.shuttle.model.AlbumArtistGroupKey?) :
        AlbumArtistQuery(
            predicate = { albumArtist -> albumArtist.groupKey == key }
        )

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