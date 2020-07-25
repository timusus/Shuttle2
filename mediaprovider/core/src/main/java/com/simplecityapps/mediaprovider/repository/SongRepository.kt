package com.simplecityapps.mediaprovider.repository

import com.simplecityapps.mediaprovider.model.Song
import kotlinx.coroutines.flow.Flow
import java.io.Serializable
import java.util.*
import kotlin.Comparator

interface SongRepository {
    suspend fun insert(songs: List<Song>)
    fun getSongs(query: SongQuery): Flow<List<Song>>
    suspend fun incrementPlayCount(song: Song)
    suspend fun setPlaybackPosition(song: Song, playbackPosition: Int)
    suspend fun setExcluded(songs: List<Song>, excluded: Boolean)
    suspend fun clearExcludeList()
    suspend fun removeSong(song: Song)
}

sealed class SongQuery(
    val predicate: ((Song) -> Boolean),
    val sortOrder: SongSortOrder? = null,
    val includeExcluded: Boolean = false
) : Serializable {

    class All(includeExcluded: Boolean = false) : SongQuery(predicate = { true }, includeExcluded = includeExcluded)

    class AlbumArtist(val name: String) : SongQuery({ song -> song.albumArtist.equals(name, ignoreCase = true) })

    class AlbumArtists(val albumArtists: List<AlbumArtist>) :
        SongQuery(
            predicate = { song -> albumArtists.any { albumArtist -> AlbumArtist(name = albumArtist.name).predicate(song) } },
            sortOrder = SongSortOrder.Track
        )

    class Album(val name: String, val albumArtistName: String) :
        SongQuery(
            predicate = { song -> song.album.equals(name, ignoreCase = true) && song.albumArtist.equals(albumArtistName, ignoreCase = true) }
        )

    class Albums(val albums: List<Album>) :
        SongQuery(
            predicate = { song -> albums.any { album -> Album(name = album.name, albumArtistName = album.albumArtistName).predicate(song) } },
            sortOrder = SongSortOrder.Track
        )

    class SongIds(val songIds: List<Long>) :
        SongQuery(
            predicate = { song -> songIds.contains(song.id) }
        )

    class LastPlayed(val after: Date) :
        SongQuery(
            predicate = { song -> song.lastPlayed?.after(after) ?: false }
        )

    class LastCompleted(val after: Date) :
        SongQuery(
            predicate = { song -> song.lastCompleted?.after(after) ?: false }
        )

    class Search(val query: String) :
        SongQuery(
            predicate = { song -> song.name.contains(query, true) || song.album.contains(query, true) || song.albumArtist.contains(query, true) }
        )

    class PlayCount(val count: Int, sortOrder: SongSortOrder) :
        SongQuery(
            predicate = { song -> song.playCount >= count },
            sortOrder = sortOrder
        )

    // Todo: This isn't really 'recently added', any songs which have had their contents modified will show up here.
    //   Best to add a 'dateAdded' column.
    class RecentlyAdded :
        SongQuery(
            predicate = { song -> (Date().time - song.lastModified.time < (2 * 7 * 24 * 60 * 60 * 1000L)) },
            sortOrder = SongSortOrder.RecentlyAdded
        ) // 2 weeks
}

enum class SongSortOrder : Serializable {
    Track, PlayCount, RecentlyAdded, RecentlyPlayed;

    val comparator: Comparator<Song>
        get() {
            return when (this) {
                Track -> compareBy<Song> { song -> song.disc }.thenBy { song -> song.track }
                PlayCount -> Comparator { a, b -> a.playCount.compareTo(b.playCount) }
                RecentlyAdded -> compareByDescending<Song> { song -> song.lastModified.time / 1000 / 60 } // Round to the nearest minute
                    .thenBy { song -> song.albumArtist }
                    .thenBy { song -> song.year }
                    .thenBy { song -> song.track }
                RecentlyPlayed -> Comparator { a, b -> b.lastCompleted?.compareTo(a.lastCompleted) ?: 0 }
            }
        }
}