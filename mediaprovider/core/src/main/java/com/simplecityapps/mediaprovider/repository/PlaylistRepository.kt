package com.simplecityapps.mediaprovider.repository

import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.model.Song
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single

interface PlaylistRepository {
    fun getPlaylists(): Observable<List<Playlist>>
    fun getPlaylists(query: PlaylistQuery): Observable<List<Playlist>>
    fun createPlaylist(name: String, mediaStoreId: Long?, songs: List<Song>?): Single<Playlist>
    fun addToPlaylist(playlist: Playlist, songs: List<Song>): Completable
    fun removeFromPlaylist(playlist: Playlist, songs: List<Song>): Completable
    fun getSongsForPlaylist(playlistId: Long): Observable<List<Song>>
    fun deletePlaylist(playlist: Playlist): Completable
    fun updatePlaylistMediaStoreId(playlist: Playlist, mediaStoreId: Long?) : Completable
    fun clearPlaylist(playlist: Playlist): Completable
}

sealed class PlaylistQuery {
    class PlaylistId(val playlistId: Long) : PlaylistQuery()
    class PlaylistName(val name: String) : PlaylistQuery()
}

fun PlaylistQuery.predicate(): (Playlist) -> Boolean {
    return when (this) {
        is PlaylistQuery.PlaylistId -> { playlist -> playlist.id == playlistId }
        is PlaylistQuery.PlaylistName -> { playlist -> playlist.name == name }
    }
}