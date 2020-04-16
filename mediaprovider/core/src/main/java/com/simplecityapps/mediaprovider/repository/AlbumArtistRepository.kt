package com.simplecityapps.mediaprovider.repository

import com.simplecityapps.mediaprovider.model.AlbumArtist
import io.reactivex.Completable
import io.reactivex.Observable
import java.io.Serializable

interface AlbumArtistRepository {

    fun populate(): Completable {
        return Completable.complete()
    }

    fun getAlbumArtists(): Observable<List<AlbumArtist>>

    fun getAlbumArtists(query: AlbumArtistQuery): Observable<List<AlbumArtist>>
}

sealed class AlbumArtistQuery(
    val predicate: ((AlbumArtist) -> Boolean),
    val sortOrder: AlbumArtistSortOrder? = null
) {
    class AlbumArtistId(val albumArtistId: Long) : AlbumArtistQuery({ albumArtist -> albumArtist.id == albumArtistId })
    class Search(private val query: String) : AlbumArtistQuery({ albumArtist -> albumArtist.name.contains(query, true) })
    class PlayCount(private val count: Int, sortOrder: AlbumArtistSortOrder) : AlbumArtistQuery({ albumArtist -> albumArtist.playCount >= count }, sortOrder)
}

enum class AlbumArtistSortOrder : Serializable {
    PlayCount;

    val comparator: Comparator<AlbumArtist>
        get() {
            return when (this) {
                PlayCount -> Comparator { a, b -> a.playCount.compareTo(b.playCount) }
            }
        }
}