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

    class ArtistGroupKey(val key: AlbumArtistGroupKey?) :
        AlbumQuery(
            predicate = { album -> album.groupKey?.albumArtistGroupKey == key }
        )

    class AlbumGroupKey(private val albumGroupKey: com.simplecityapps.shuttle.model.AlbumGroupKey?, sortOrder: AlbumSortOrder = AlbumSortOrder.Default) :
        AlbumQuery(
            predicate = { album -> album.groupKey == albumGroupKey },
            sortOrder = sortOrder
        )

    class AlbumGroupKeys(val albums: List<AlbumGroupKey>, sortOrder: AlbumSortOrder = AlbumSortOrder.Default) :
        AlbumQuery(
            predicate = { album -> albums.any { it.predicate(album) } },
            sortOrder = sortOrder
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