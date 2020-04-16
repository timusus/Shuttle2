package com.simplecityapps.mediaprovider.repository

import com.simplecityapps.mediaprovider.model.Album
import io.reactivex.Completable
import io.reactivex.Observable
import java.io.Serializable

interface AlbumRepository {

    fun populate(): Completable {
        return Completable.complete()
    }

    fun getAlbums(): Observable<List<Album>>

    fun getAlbums(query: AlbumQuery): Observable<List<Album>>
}

sealed class AlbumQuery(
    val predicate: ((Album) -> Boolean),
    val sortOrder: AlbumSortOrder? = null

) {
    class AlbumArtistId(val albumArtistId: Long) : AlbumQuery({ album -> album.albumArtistId == albumArtistId })
    class AlbumIds(val albumIds: List<Long>) : AlbumQuery({ album -> albumIds.contains(album.id) })
    class Search(private val query: String) : AlbumQuery({ album -> album.name.contains(query, true) || album.albumArtistName.contains(query, true) })
    class PlayCount(private val count: Int, sortOrder: AlbumSortOrder) : AlbumQuery({ album -> album.playCount >= count }, sortOrder)
    class Year(private val year: Int) : AlbumQuery({ album -> album.year == year })
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