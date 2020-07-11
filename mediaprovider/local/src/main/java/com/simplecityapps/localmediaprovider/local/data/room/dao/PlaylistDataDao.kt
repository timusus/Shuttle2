package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.*
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistData
import com.simplecityapps.mediaprovider.model.Playlist
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlaylistDataDao {

    @Insert
    abstract suspend fun insert(playlistData: PlaylistData): Long

    @Delete
    abstract suspend fun delete(playlistData: PlaylistData)

    @Update
    abstract suspend fun update(playlistData: PlaylistData)

    @Query(
        "SELECT playlists.*, " +
                "count(songs.id) as songCount, " +
                "sum(songs.duration) as duration, " +
                "media_store_id as mediaStoreId " +
                "FROM playlists " +
                "LEFT JOIN playlist_song_join ON playlists.id = playlist_song_join.playlistId " +
                "LEFT JOIN songs ON songs.id = playlist_song_join.songId " +
                "WHERE songs.blacklisted == 0 " +
                "GROUP BY playlists.id " +
                "ORDER BY playlists.name;"
    )
    abstract fun getAll(): Flow<List<Playlist>>

    @Query(
        "SELECT playlists.*, " +
                "count(songs.id) as songCount, " +
                "sum(songs.duration) as duration, " +
                "media_store_id as mediaStoreId " +
                "FROM playlists " +
                "LEFT JOIN playlist_song_join ON playlists.id = playlist_song_join.playlistId " +
                "LEFT JOIN songs ON songs.id = playlist_song_join.songId " +
                "WHERE playlists.id = :playlistId AND songs.blacklisted == 0 " +
                "GROUP BY playlists.id " +
                "ORDER BY playlists.name;"
    )
    abstract suspend fun getPlaylist(playlistId: Long): Playlist

    @Query("DELETE FROM playlist_song_join WHERE playlistId = :playlistId")
    abstract suspend fun delete(playlistId: Long)
}