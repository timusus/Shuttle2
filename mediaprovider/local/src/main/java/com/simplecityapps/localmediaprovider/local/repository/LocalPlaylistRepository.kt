package com.simplecityapps.localmediaprovider.local.repository

import com.jakewharton.rxrelay2.BehaviorRelay
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistData
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistSongJoin
import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.predicate
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.Consumer
import timber.log.Timber

class LocalPlaylistRepository(
    private val database: MediaDatabase
) : PlaylistRepository {

    private val playlistsRelay: BehaviorRelay<List<Playlist>> by lazy {
        val relay = BehaviorRelay.create<List<Playlist>>()
        database.playlistDataDao()
            .getAll()
            .toObservable()
            .subscribe(
                relay,
                Consumer { throwable -> Timber.e(throwable, "Failed to subscribe to playlists relay") })
        relay
    }

    override fun getPlaylists(query: PlaylistQuery): Observable<List<Playlist>> {
        return playlistsRelay.map { playlists ->
            playlists.filter(query.predicate())
        }
    }

    override fun getPlaylists(): Observable<List<Playlist>> {
        return playlistsRelay
    }

    override fun createPlaylist(name: String, mediaStoreId: Long?, songs: List<Song>?): Single<Playlist> {
        return Single.fromCallable {
                database.playlistDataDao().insert(PlaylistData(name = name, mediaStoreId = mediaStoreId))
            }
            .flatMap { playlistId ->
                database.playlistSongJoinDataDao().insert(songs.orEmpty().map { song -> PlaylistSongJoin(playlistId, song.id) })
                    .andThen(Single.just(playlistId))
            }
            .flatMap { playlistId ->
                database.playlistDataDao().getPlaylist(playlistId)
            }
    }

    override fun addToPlaylist(playlist: Playlist, songs: List<Song>): Completable {
        return database.playlistSongJoinDataDao().insert(songs.map { song -> PlaylistSongJoin(playlist.id, song.id) })
    }

    override fun removeFromPlaylist(playlist: Playlist, songs: List<Song>): Completable {
        return database.playlistSongJoinDataDao().delete(playlist.id, songs.map { song -> song.id }.toTypedArray())
    }

    override fun getSongsForPlaylist(playlistId: Long): Observable<List<Song>> {
        return database.playlistSongJoinDataDao().getSongsForPlaylist(playlistId).toObservable()
    }

    override fun deletePlaylist(playlist: Playlist): Completable {
        return database.playlistDataDao().delete(
            PlaylistData(playlist.id, playlist.name)
        )
    }

    override fun updatePlaylistMediaStoreId(playlist: Playlist, mediaStoreId: Long?): Completable {
        return database.playlistDataDao().update(PlaylistData(playlist.id, playlist.name, mediaStoreId))
    }

    override fun clearPlaylist(playlist: Playlist): Completable {
        return database.playlistDataDao().delete(playlist.id)
    }
}