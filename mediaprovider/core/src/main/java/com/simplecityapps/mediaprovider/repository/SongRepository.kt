package com.simplecityapps.mediaprovider.repository

import com.simplecityapps.mediaprovider.SongProvider
import com.simplecityapps.mediaprovider.model.Song
import io.reactivex.Completable
import io.reactivex.Observable
import java.util.*

interface SongRepository {

    fun populate(songProvider: SongProvider, callback: ((Float, String) -> Unit)? = null): Completable {
        return Completable.complete()
    }

    fun getSongs(): Observable<List<Song>>

    fun getSongs(query: SongQuery): Observable<List<Song>>

    fun incrementPlayCount(song: Song): Completable

    fun setPlaybackPosition(song: Song, playbackPosition: Int): Completable
}

sealed class SongQuery {
    class AlbumArtistId(val albumArtistId: Long) : SongQuery()
    class AlbumArtistIds(val albumArtistIds: List<Long>) : SongQuery()
    class AlbumId(val albumId: Long) : SongQuery()
    class AlbumIds(val albumIds: List<Long>) : SongQuery()
    class SongIds(val songIds: List<Long>) : SongQuery()
    class LastPlayed(val after: Date) : SongQuery()
    class LastCompleted(val after: Date) : SongQuery()
    class PlaylistId(val playlistId: Long) : SongQuery()
    class Search(val query: String) : SongQuery()
}

fun SongQuery.predicate(): (Song) -> Boolean {
    return when (this) {
        is SongQuery.AlbumArtistId -> { song -> song.albumArtistId == albumArtistId }
        is SongQuery.AlbumArtistIds -> { song -> albumArtistIds.contains(song.albumArtistId) }
        is SongQuery.AlbumId -> { song -> song.albumId == albumId }
        is SongQuery.AlbumIds -> { song -> albumIds.contains(song.albumId) }
        is SongQuery.SongIds -> { song -> songIds.contains(song.id) }
        is SongQuery.LastPlayed -> { song -> song.lastPlayed?.after(after) ?: false }
        is SongQuery.LastCompleted -> { song -> song.lastCompleted?.after(after) ?: false }
        is SongQuery.PlaylistId -> throw NotImplementedError("Use PlaylistRepository.getSongsForPlaylist() instead")
        is SongQuery.Search -> { song ->
            song.name.contains(query, true)
                    || song.albumName.contains(query, true)
                    || song.albumArtistName.contains(query, true)
        }
    }
}