package com.simplecityapps.mediaprovider.repository

import com.simplecityapps.mediaprovider.SongProvider
import com.simplecityapps.mediaprovider.model.Song
import io.reactivex.Completable
import io.reactivex.Observable
import java.io.Serializable
import java.util.*
import kotlin.Comparator

interface SongRepository {

    fun populate(songProvider: SongProvider, callback: ((Float, String) -> Unit)? = null): Completable {
        return Completable.complete()
    }

    fun getSongs(query: SongQuery? = null, includeBlacklisted: Boolean = false): Observable<List<Song>>

    fun incrementPlayCount(song: Song): Completable

    fun setPlaybackPosition(song: Song, playbackPosition: Int): Completable

    fun setBlacklisted(songs: List<Song>, blacklisted: Boolean): Completable

    fun clearBlacklist(): Completable

    fun removeSong(song: Song) : Completable
}

sealed class SongQuery(
    val predicate: ((Song) -> Boolean),
    val sortOrder: SongSortOrder? = null
) : Serializable {

    class AlbumArtistIds(private val albumArtistIds: List<Long>) :
        SongQuery(
            { song -> albumArtistIds.contains(song.albumArtistId) },
            SongSortOrder.Track
        )

    class AlbumIds(private val albumIds: List<Long>) :
        SongQuery(
            { song -> albumIds.contains(song.albumId) },
            SongSortOrder.Track
        )

    class SongIds(private val songIds: List<Long>) :
        SongQuery({ song -> songIds.contains(song.id) })

    class LastPlayed(private val after: Date) :
        SongQuery({ song -> song.lastPlayed?.after(after) ?: false })

    class LastCompleted(private val after: Date) :
        SongQuery({ song -> song.lastCompleted?.after(after) ?: false })

    class Search(private val query: String) :
        SongQuery({ song -> song.name.contains(query, true) || song.albumName.contains(query, true) || song.albumArtistName.contains(query, true) })

    class PlayCount(private val count: Int, sortOrder: SongSortOrder) :
        SongQuery({ song -> song.playCount >= count }, sortOrder)

    // Todo: This isn't really 'recently added', any songs which have had their contents modified will show up here.
    //   Best to add a 'dateAdded' column.
    class RecentlyAdded :
        SongQuery({ song -> (Date().time - song.lastModified.time < (2 * 7 * 24 * 60 * 60 * 1000L)) }, SongSortOrder.RecentlyAdded) // 2 weeks
}

enum class SongSortOrder : Serializable {
    Track, PlayCount, RecentlyAdded, MostPlayed, RecentlyPlayed;

    fun getSortOrder(): Comparator<Song> {
        return when (this) {
            Track -> compareBy<Song> { song -> song.disc }.thenBy { song -> song.track }
            PlayCount -> Comparator { a, b -> a.playCount.compareTo(b.playCount) }
            RecentlyAdded -> compareByDescending<Song> { song -> song.lastModified.time / 1000 / 60 } // Round to the nearest minute
                .thenBy { song -> song.albumArtistName }
                .thenBy { song -> song.year }
                .thenBy { song -> song.track }
            MostPlayed -> Comparator { a, b -> b.playCount.compareTo(a.playCount) }
            RecentlyPlayed -> Comparator { a, b -> b.lastCompleted?.compareTo(a.lastCompleted) ?: 0 }
        }
    }
}