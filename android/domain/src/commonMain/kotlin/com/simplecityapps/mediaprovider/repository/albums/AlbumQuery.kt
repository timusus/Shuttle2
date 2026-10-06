package com.simplecityapps.mediaprovider.repository.albums

import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.sorting.AlbumSortOrder

sealed class AlbumQuery(
    val predicate: ((com.simplecityapps.shuttle.model.Album) -> Boolean),
    val sortOrder: AlbumSortOrder = AlbumSortOrder.Default
) {
    class All(sortOrder: AlbumSortOrder = AlbumSortOrder.Default) :
        AlbumQuery(
            predicate = { true },
            sortOrder = sortOrder
        )

    /** The albums the artist is the album artist of: their own, not those they only appear on. */
    class ArtistGroupKey(val key: AlbumArtistGroupKey?) :
        AlbumQuery(
            predicate = { album -> key in album.albumArtistKeys }
        )

    class AlbumGroupKey(val albumGroupKey: com.simplecityapps.shuttle.model.AlbumGroupKey?) :
        AlbumQuery(
            predicate = { album -> album.groupKey == albumGroupKey }
        )

    class AlbumGroupKeys(val albums: List<AlbumGroupKey>) :
        AlbumQuery(
            predicate = albums.mapTo(HashSet()) { it.albumGroupKey }.let { keys -> { album: com.simplecityapps.shuttle.model.Album -> album.groupKey in keys } }
        )

    class Search(val query: String) :
        AlbumQuery(
            predicate = { album -> album.name?.contains(query, true) ?: false || album.albumArtist?.contains(query, true) ?: false }
        )

    class PlayCount(val count: Int, sortOrder: AlbumSortOrder) :
        AlbumQuery(
            predicate = { album -> album.playCount >= count },
            sortOrder = sortOrder
        )

    class Year(val year: Int) :
        AlbumQuery(
            predicate = { album -> album.year == year }
        )
}
