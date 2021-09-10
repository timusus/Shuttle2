package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistSongData
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistSongJoin
import com.simplecityapps.shuttle.model.PlaylistSong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
abstract class PlaylistSongJoinDao {

    @Insert
    abstract suspend fun insert(playlistSongJoin: PlaylistSongJoin)

    @Insert
    abstract suspend fun insert(playlistSongJoins: List<PlaylistSongJoin>)

    @Query(
        """
            SELECT songs.*, playlist_song_join.id as playlistSongId, playlist_song_join.sortOrder as sortOrder 
            FROM songs 
            LEFT JOIN playlist_song_join ON songs.id = playlist_song_join.songId AND songs.blacklisted == 0
            WHERE playlist_song_join.playlistId = :playlistId AND songs.blacklisted == 0;
            """
    )
    abstract fun getSongDataForPlaylist(playlistId: Long): Flow<List<PlaylistSongData>>

    fun getSongsForPlaylist(playlistId: Long): Flow<List<PlaylistSong>> {
        return getSongDataForPlaylist(playlistId).map { list ->
            list.map { playlistSongData ->
                PlaylistSong(
                    playlistSongData.playlistSongId,
                    playlistSongData.sortOrder,
                    playlistSongData.songData.toSong()
                )
            }
        }
    }

    @Query("DELETE FROM playlist_song_join WHERE playlistId = :playlistId and id IN (:playlistSongIds)")
    abstract suspend fun delete(playlistId: Long, playlistSongIds: Array<Long>)

    @Query("DELETE FROM playlist_song_join WHERE playlistId = :playlistId and songId IN (:songIds)")
    abstract suspend fun deleteSongs(playlistId: Long, songIds: Array<Long>)

    @Update
    abstract suspend fun updateSortOrder(playlistSongJoins: List<PlaylistSongJoin>)
}