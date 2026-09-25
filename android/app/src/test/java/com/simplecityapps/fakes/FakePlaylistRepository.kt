package com.simplecityapps.fakes

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.playlists.comparator
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.PlaylistSong
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakePlaylistRepository : PlaylistRepository {
    private val playlists = MutableStateFlow<List<Playlist>>(emptyList())
    private val playlistSongs = MutableStateFlow<Map<Long, List<Song>>>(emptyMap())

    /** What [getFavoritesPlaylist] returns; unset, it throws as the real one can. */
    var favorites: Playlist? = null

    /** Every [addToPlaylist] call, in order. */
    val addedToPlaylist = mutableListOf<Pair<Playlist, List<Song>>>()

    /** Every [createPlaylist] call's name and songs, in order. */
    val created = mutableListOf<Pair<String, List<Song>?>>()

    /** Thrown by [addToPlaylist] and [createPlaylist] when set. */
    var failure: Exception? = null

    fun setSongsForPlaylist(playlist: Playlist, songs: List<Song>) {
        playlistSongs.value += playlist.id to songs
    }

    fun setPlaylists(value: List<Playlist>) {
        playlists.value = value
    }

    override fun getPlaylists(query: PlaylistQuery): Flow<List<Playlist>> = playlists

    override suspend fun getFavoritesPlaylist(): Playlist = favorites ?: error("No favorites playlist")

    override suspend fun createPlaylist(name: String, mediaProviderType: MediaProviderType, songs: List<Song>?, externalId: String?): Playlist {
        failure?.let { throw it }
        created += name to songs
        return com.simplecityapps.createPlaylist(id = 1000L + created.size, name = name)
    }

    override suspend fun addToPlaylist(playlist: Playlist, songs: List<Song>) {
        failure?.let { throw it }
        addedToPlaylist += playlist to songs
        playlistSongs.value += playlist.id to playlistSongs.value[playlist.id].orEmpty() + songs
    }

    /** Every [removeFromPlaylist] call, in order. */
    val removedFromPlaylist = mutableListOf<Pair<Playlist, List<PlaylistSong>>>()

    /** The entries of the last [updatePlaylistSongsSortOder] call. */
    var reorderedSongs: List<PlaylistSong>? = null

    override suspend fun removeFromPlaylist(playlist: Playlist, playlistSongs: List<PlaylistSong>) {
        removedFromPlaylist += playlist to playlistSongs
    }

    override suspend fun removeSongsFromPlaylist(playlist: Playlist, songs: List<Song>) {
        playlistSongs.value += playlist.id to playlistSongs.value[playlist.id].orEmpty() - songs.toSet()
    }

    override fun getSongsForPlaylist(playlist: Playlist): Flow<List<PlaylistSong>> {
        val comparator = playlist.sortOrder.comparator
        return playlistSongs.map { byPlaylist ->
            val songs = byPlaylist[playlist.id].orEmpty().mapIndexed { index, song ->
                PlaylistSong(id = index.toLong(), sortOrder = index.toLong(), song = song)
            }
            songs.sortedWith(if (playlist.sortDescending) comparator.reversed() else comparator)
        }
    }

    override suspend fun deletePlaylist(playlist: Playlist) {}

    override suspend fun deleteAll(mediaProviderType: MediaProviderType) {}

    override suspend fun clearPlaylist(playlist: Playlist) {}

    override suspend fun renamePlaylist(playlist: Playlist, name: String) {}

    override suspend fun updatePlaylistSortOder(playlist: Playlist, sortOrder: PlaylistSongSortOrder, sortDescending: Boolean) {
        playlists.value = playlists.value.map { existing ->
            if (existing.id == playlist.id) existing.copy(sortOrder = sortOrder, sortDescending = sortDescending) else existing
        }
    }

    override suspend fun updatePlaylistSongsSortOder(playlist: Playlist, playlistSongs: List<PlaylistSong>) {
        reorderedSongs = playlistSongs
    }

    override suspend fun updatePlaylistMediaProviderType(playlist: Playlist, mediaProviderType: MediaProviderType) {}

    override suspend fun updatePlaylistExternalId(playlist: Playlist, externalId: String?) {}
}
