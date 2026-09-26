package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistCoverSongData
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistSongData
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistSongJoin
import com.simplecityapps.shuttle.model.PlaylistSong
import com.simplecityapps.shuttle.model.Song
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

    fun getSongsForPlaylist(playlistId: Long): Flow<List<PlaylistSong>> = getSongDataForPlaylist(playlistId).map { list ->
        list.map { playlistSongData ->
            PlaylistSong(
                playlistSongData.playlistSongId,
                playlistSongData.sortOrder,
                playlistSongData.songData.toSong()
            )
        }
    }

    /**
     * One row per distinct album (case-insensitively, by [SongData.album]/[SongData.albumArtist]), the row with the
     * lowest [PlaylistSongJoin.sortOrder] in each group — SQLite's `MIN()` bare-column rule guarantees `songs.*` comes
     * from that row, not an arbitrary one in the group.
     */
    @Query(
        """
            SELECT MIN(playlist_song_join.sortOrder) as sortOrder, songs.*
            FROM playlist_song_join
            JOIN songs ON songs.id = playlist_song_join.songId AND songs.blacklisted == 0
            WHERE playlist_song_join.playlistId = :playlistId
            GROUP BY LOWER(songs.album), LOWER(songs.albumArtist)
            ORDER BY sortOrder
            LIMIT :limit;
            """
    )
    abstract fun getCoverSongData(
        playlistId: Long,
        limit: Int
    ): Flow<List<PlaylistCoverSongData>>

    fun getCoverSongsForPlaylist(playlistId: Long, limit: Int): Flow<List<Song>> = getCoverSongData(playlistId, limit).map { list ->
        list.map { it.songData.toSong() }
    }

    @Query("DELETE FROM playlist_song_join WHERE playlistId = :playlistId and id IN (:playlistSongIds)")
    abstract suspend fun delete(
        playlistId: Long,
        playlistSongIds: Array<Long>
    )

    @Query("DELETE FROM playlist_song_join WHERE playlistId = :playlistId and songId IN (:songIds)")
    abstract suspend fun deleteSongs(
        playlistId: Long,
        songIds: Array<Long>
    )

    @Update
    abstract suspend fun updateSortOrder(playlistSongJoins: List<PlaylistSongJoin>)
}
