package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistSongJoin
import com.simplecityapps.mediaprovider.model.Song
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlaylistSongJoinDao {

    @Insert
    abstract suspend fun insert(playlistSongJoin: PlaylistSongJoin)

    @Insert
    abstract suspend fun insert(playlistSongJoins: List<PlaylistSongJoin>)

    @Query(
        "SELECT songs.* " +
                "FROM songs " +
                "INNER JOIN playlist_song_join ON songs.id = playlist_song_join.songId " +
                "WHERE playlist_song_join.playlistId = :playlistId AND songs.blacklisted == 0;"
    )
    abstract fun getSongsForPlaylist(playlistId: Long): Flow<List<Song>>

    @Query("DELETE FROM playlist_song_join WHERE playlistId = :playlistId and songId IN (:songIds)")
    abstract suspend fun delete(playlistId: Long, songIds: Array<Long>)
}