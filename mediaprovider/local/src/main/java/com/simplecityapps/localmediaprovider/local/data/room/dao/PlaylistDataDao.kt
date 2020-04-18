package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.*
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistData
import com.simplecityapps.mediaprovider.model.Playlist
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single

@Dao
abstract class PlaylistDataDao {

    @Insert
    abstract fun insert(playlistData: PlaylistData): Long

    @Delete
    abstract fun delete(playlistData: PlaylistData): Completable

    @Update
    abstract fun update(playlistData: PlaylistData): Completable

    @Query(
        "SELECT playlists.*, " +
                "count(songs.id) as songCount, " +
                "sum(songs.duration) as duration, " +
                "media_store_id as mediaStoreId " +
                "FROM playlists " +
                "LEFT JOIN playlist_song_join ON playlists.id = playlist_song_join.playlistId " +
                "LEFT JOIN songs ON songs.id = playlist_song_join.songId " +
                "AND songs.blacklisted == 0 " +
                "GROUP BY playlists.id " +
                "ORDER BY playlists.name;"
    )
    abstract fun getAll(): Flowable<List<Playlist>>

    @Query(
        "SELECT playlists.*, " +
                "count(songs.id) as songCount, " +
                "sum(songs.duration) as duration, " +
                "media_store_id as mediaStoreId " +
                "FROM playlists " +
                "LEFT JOIN playlist_song_join ON playlists.id = playlist_song_join.playlistId " +
                "LEFT JOIN songs ON songs.id = playlist_song_join.songId " +
                "AND playlists.id = :playlistId AND songs.blacklisted == 0 " +
                "GROUP BY playlists.id " +
                "ORDER BY playlists.name;"
    )
    abstract fun getPlaylist(playlistId: Long): Single<Playlist>

    @Query("DELETE FROM playlist_song_join WHERE playlistId = :playlistId")
    abstract fun delete(playlistId: Long): Completable
}