package com.simplecityapps.mediaprovider.repository

import android.os.Parcelable
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.model.PlaylistSong
import com.simplecityapps.mediaprovider.model.SmartPlaylist
import com.simplecityapps.mediaprovider.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.parcelize.Parcelize
import java.io.Serializable

interface PlaylistRepository {
    fun getPlaylists(query: PlaylistQuery): Flow<List<Playlist>>
    suspend fun getFavoritesPlaylist(): Playlist
    suspend fun createPlaylist(name: String, mediaProviderType: MediaProvider.Type, songs: List<Song>?, externalId: String?): Playlist
    suspend fun addToPlaylist(playlist: Playlist, songs: List<Song>)
    suspend fun removeFromPlaylist(playlist: Playlist, playlistSongs: List<PlaylistSong>)
    suspend fun removeSongsFromPlaylist(playlist: Playlist, songs: List<Song>)
    fun getSongsForPlaylist(playlist: Playlist): Flow<List<PlaylistSong>>
    suspend fun deletePlaylist(playlist: Playlist)
    suspend fun clearPlaylist(playlist: Playlist)
    suspend fun renamePlaylist(playlist: Playlist, name: String)
    fun getSmartPlaylists(): Flow<List<SmartPlaylist>>
    suspend fun updatePlaylistSortOder(playlist: Playlist, sortOrder: PlaylistSongSortOrder)
    suspend fun updatePlaylistSongsSortOder(playlist: Playlist, playlistSongs: List<PlaylistSong>)
}

sealed class PlaylistQuery(
    val predicate: ((Playlist) -> Boolean),
    val sortOrder: PlaylistSortOrder = PlaylistSortOrder.Default
) {
    class All(mediaProviderType: MediaProvider.Type?, sortOrder: PlaylistSortOrder = PlaylistSortOrder.Default) : PlaylistQuery(
        predicate = { mediaProviderType == null || it.mediaProvider == mediaProviderType },
        sortOrder = sortOrder
    )

    class PlaylistId(val playlistId: Long) : PlaylistQuery(
        predicate = { playlist -> playlist.id == playlistId },
        sortOrder = PlaylistSortOrder.Default
    )
}

enum class PlaylistSortOrder : Serializable {
    Default;

    val comparator: Comparator<Playlist>
        get() {
            return when (this) {
                Default -> defaultComparator
            }
        }

    companion object {
        val defaultComparator: Comparator<Playlist> by lazy { compareBy { playlist -> playlist.id } }
    }
}

@Parcelize
enum class PlaylistSongSortOrder : Parcelable {
    Position,
    SongName,
    ArtistGroupKey,
    AlbumGroupKey,
    Year,
    Duration,
    Track,
    PlayCount,
    LastModified,
    LastCompleted;

    val comparator: Comparator<PlaylistSong>
        get() {
            return when (this) {
                Position -> positionComparator
                SongName -> DelegatingComparator(SongSortOrder.songNameComparator)
                ArtistGroupKey -> DelegatingComparator(SongSortOrder.artistGroupKeyComparator)
                AlbumGroupKey -> DelegatingComparator(SongSortOrder.albumGroupKeyComparator)
                Year -> DelegatingComparator(SongSortOrder.yearComparator)
                Duration -> DelegatingComparator(SongSortOrder.durationComparator)
                Track -> DelegatingComparator(SongSortOrder.trackComparator)
                PlayCount -> DelegatingComparator(SongSortOrder.playCountComparator)
                LastModified -> DelegatingComparator(SongSortOrder.lastModifiedComparator)
                LastCompleted -> DelegatingComparator(SongSortOrder.lastCompletedComparator)
            }
        }

    companion object {
        private val positionComparator: Comparator<PlaylistSong> by lazy {
            compareBy { it.sortOrder }
        }
    }
}

class DelegatingComparator(private val songComparator: Comparator<Song>) : Comparator<PlaylistSong> {
    override fun compare(o1: PlaylistSong, o2: PlaylistSong): Int {
        return songComparator.compare(o1.song, o2.song)
    }
}