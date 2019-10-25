package com.simplecityapps.mediaprovider.repository

import com.simplecityapps.mediaprovider.SongProvider
import com.simplecityapps.mediaprovider.model.Song
import io.reactivex.Completable
import io.reactivex.Observable
import java.io.Serializable

interface SongRepository {

    fun populate(songProvider: SongProvider, callback: ((Float, String) -> Unit)? = null): Completable {
        return Completable.complete()
    }

    fun getSongs(query: SongQuery? = null): Observable<List<Song>>

    fun incrementPlayCount(song: Song): Completable

    fun setPlaybackPosition(song: Song, playbackPosition: Int): Completable
}

sealed class SongQuery(val predicate: ((Song) -> Boolean), val sortOrder: Comparator<Song>? = null) : Serializable {

    class AlbumArtistIds(private val albumArtistIds: List<Long>) :
        SongQuery({ song -> albumArtistIds.contains(song.albumArtistId) })

    class AlbumIds(private val albumIds: List<Long>) :
        SongQuery({ song -> albumIds.contains(song.albumId) })

    class SongIds(private val songIds: List<Long>) :
        SongQuery({ song -> songIds.contains(song.id) })

    class LastPlayed(private val after: java.util.Date) :
        SongQuery({ song -> song.lastPlayed?.after(after) ?: false })

    class LastCompleted(private val after: java.util.Date) :
        SongQuery({ song -> song.lastCompleted?.after(after) ?: false })

    class Search(private val query: String) :
        SongQuery({ song -> song.name.contains(query, true) || song.albumName.contains(query, true) || song.albumArtistName.contains(query, true) })

    class PlayCount(private val count: Int, sortOrder: Comparator<Song>? = null) :
        SongQuery({ song -> song.playCount >= count }, sortOrder)
}