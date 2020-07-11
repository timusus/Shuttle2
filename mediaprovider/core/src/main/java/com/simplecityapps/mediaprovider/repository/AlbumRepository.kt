package com.simplecityapps.mediaprovider.repository

import com.simplecityapps.mediaprovider.model.Album
import kotlinx.coroutines.flow.Flow
import java.io.Serializable

interface AlbumRepository {
    fun getAlbums(): Flow<List<Album>>
    fun getAlbums(query: AlbumQuery): Flow<List<Album>>
}

sealed class AlbumQuery(
    val predicate: ((com.simplecityapps.mediaprovider.model.Album) -> Boolean),
    val sortOrder: AlbumSortOrder? = null

) {
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
            predicate = { album -> albums.any { Album(name = album.name, albumArtistName = album.albumArtist).predicate(album) } }
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
    PlayCount;

    val comparator: Comparator<Album>
        get() {
            return when (this) {
                PlayCount -> Comparator { a, b -> b.playCount.compareTo(a.playCount) }
            }
        }
}