package com.simplecityapps.playback.fakes

import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.PlaylistSong
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** The album artists [artists], read only. */
class FakeAlbumArtistRepository(private val artists: List<AlbumArtist> = emptyList()) : AlbumArtistRepository {
    override fun getAlbumArtists(query: AlbumArtistQuery): Flow<List<AlbumArtist>> = flowOf(artists.filter(query.predicate))
}

/** The albums [albums], read only. */
class FakeAlbumRepository(private val albums: List<Album> = emptyList()) : AlbumRepository {
    override fun getAlbums(query: AlbumQuery): Flow<List<Album>> = flowOf(albums.filter(query.predicate))
}

/** The playlists [playlists] and their songs, in order, read only. */
class FakePlaylistRepository(private val playlists: Map<Playlist, List<Song>> = emptyMap()) : PlaylistRepository {
    override fun getPlaylists(query: PlaylistQuery): Flow<List<Playlist>> = flowOf(playlists.keys.filter(query.predicate))

    override fun getSongsForPlaylist(playlist: Playlist): Flow<List<PlaylistSong>> = flowOf(
        playlists[playlist].orEmpty().mapIndexed { index, song -> PlaylistSong(id = index.toLong(), sortOrder = index.toLong(), song = song) }
    )

    override suspend fun getFavoritesPlaylist(): Playlist = error("not called")

    override suspend fun createPlaylist(
        name: String,
        mediaProviderType: MediaProviderType,
        songs: List<Song>?,
        externalId: String?
    ): Playlist = error("not called")

    override suspend fun addToPlaylist(
        playlist: Playlist,
        songs: List<Song>
    ) = error("not called")

    override suspend fun removeFromPlaylist(
        playlist: Playlist,
        playlistSongs: List<PlaylistSong>
    ) = error("not called")

    override suspend fun removeSongsFromPlaylist(
        playlist: Playlist,
        songs: List<Song>
    ) = error("not called")

    override suspend fun deletePlaylist(playlist: Playlist) = error("not called")

    override suspend fun deleteAll(mediaProviderType: MediaProviderType) = error("not called")

    override suspend fun clearPlaylist(playlist: Playlist) = error("not called")

    override suspend fun renamePlaylist(
        playlist: Playlist,
        name: String
    ) = error("not called")

    override suspend fun updatePlaylistSortOder(
        playlist: Playlist,
        sortOrder: PlaylistSongSortOrder,
        sortDescending: Boolean
    ) = error("not called")

    override suspend fun updatePlaylistSongsSortOder(
        playlist: Playlist,
        playlistSongs: List<PlaylistSong>
    ) = error("not called")
}
