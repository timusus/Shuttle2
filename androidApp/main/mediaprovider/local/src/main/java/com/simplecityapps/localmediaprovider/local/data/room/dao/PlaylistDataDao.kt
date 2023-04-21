package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistData
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlaylistDataDao {

    @Insert
    abstract suspend fun insert(playlistData: PlaylistData): Long

    @Update
    abstract suspend fun update(playlistData: PlaylistData)

    @Query(
        """
            SELECT playlists.*, count(songs.id) as songCount, sum(songs.duration) as duration, playlists.sortOrder as sortOrder, playlists.mediaProvider, playlists.externalId
            FROM playlists 
            LEFT JOIN playlist_song_join ON playlists.id = playlist_song_join.playlistId 
            LEFT JOIN songs ON songs.id = playlist_song_join.songId AND songs.blacklisted == 0 
            GROUP BY playlists.id
            ORDER BY playlists.name;
            """
    )
    abstract fun getAll(): Flow<List<com.simplecityapps.shuttle.model.Playlist>>

    @Query(
        """
            SELECT playlists.*, count(songs.id) as songCount, sum(songs.duration) as duration, playlists.sortOrder as sortOrder, playlists.mediaProvider, playlists.externalId
            FROM playlists 
            LEFT JOIN playlist_song_join ON playlists.id = playlist_song_join.playlistId 
            LEFT JOIN songs ON songs.id = playlist_song_join.songId AND songs.blacklisted == 0 
            WHERE playlists.id = :playlistId 
            GROUP BY playlists.id 
            ORDER BY playlists.name
            """
    )
    abstract suspend fun getPlaylist(playlistId: Long): com.simplecityapps.shuttle.model.Playlist

    @Query("DELETE FROM playlist_song_join WHERE playlistId = :playlistId")
    abstract suspend fun clear(playlistId: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    abstract suspend fun delete(playlistId: Long)

    @Query("DELETE FROM playlists WHERE mediaProvider = :mediaProviderType")
    abstract suspend fun deleteAll(mediaProviderType: MediaProviderType)
}
