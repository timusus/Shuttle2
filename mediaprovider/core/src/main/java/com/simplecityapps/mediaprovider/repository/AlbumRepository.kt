package com.simplecityapps.mediaprovider.repository

import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.removeArticles
import kotlinx.coroutines.flow.Flow
import java.io.Serializable
import java.text.Collator

interface AlbumRepository {
    fun getAlbums(query: AlbumQuery): Flow<List<Album>>
}

sealed class AlbumQuery(
    val predicate: ((com.simplecityapps.mediaprovider.model.Album) -> Boolean),
    val sortOrder: AlbumSortOrder = AlbumSortOrder.Default
) {

    class All(sortOrder: AlbumSortOrder = AlbumSortOrder.Default) :
        AlbumQuery(
            predicate = { true },
            sortOrder = sortOrder
        )

    class AlbumArtist(val name: String) :
        AlbumQuery(
            predicate = { album -> album.albumArtist.equals(name, ignoreCase = true) }
        )

    class Album(val name: String, val albumArtistName: String) :
        AlbumQuery(
            predicate = { album -> album.name.equals(name, ignoreCase = true) && album.albumArtist.equals(albumArtistName, ignoreCase = true) }
        )

    class Albums(val albums: List<Album>) :
        AlbumQuery(
            predicate = { album -> albums.any { it.predicate(album) } }
        )

    class Search(val query: String) :
        AlbumQuery(
            predicate = { album -> album.name.contains(query, true) || album.albumArtist.contains(query, true) }
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

enum class AlbumSortOrder : Serializable {
    Default, AlbumName, ArtistName, Year, PlayCount;

    val comparator: Comparator<Album>
        get() {
            return when (this) {
                Default -> Comparator<Album> { a, b -> zeroLastComparator.compare(a.year, b.year) }.then(compareByDescending { album -> album.year })
                AlbumName -> Comparator<Album> { a, b -> Collator.getInstance().apply { strength = Collator.TERTIARY }.compare(a.sortKey, b.sortKey) }.then(Default.comparator)
                ArtistName -> Comparator<Album> { a, b -> Collator.getInstance().apply { strength = Collator.TERTIARY }.compare(a.albumArtist.removeArticles(), b.albumArtist.removeArticles()) }.then(
                    Default.comparator
                )
                PlayCount -> compareBy<Album> { song -> song.playCount }.then(Default.comparator)
                Year -> Default.comparator
            }
        }
}