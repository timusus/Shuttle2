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

    fun getSongsForPlaylist(playlistId: Long): Flow<List<PlaylistSong>> = getSongDataForPlaylist(playlistId).map { list -> list.map { it.toPlaylistSong() } }

    /**
     * One row per distinct album (case-insensitively, by [SongData.album]/[SongData.albumArtist]), the row with the
     * lowest [PlaylistSongJoin.sortOrder] in each group — SQLite's `MIN()` bare-column rule guarantees the other bare
     * columns (`playlist_song_join.id`, `songs.*`) come from that same row, not an arbitrary one in the group.
     *
     * This grouping approximates [com.simplecityapps.shuttle.model.Song.albumGroupKey]: no article-stripping, no
     * fallback to the artist alone. Deliberate — this is a cosmetic mosaic, not album identity.
     *
     * Not limited or ordered for display here: the caller re-sorts by the playlist's own
     * [com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder] before taking a cover count, so this raw join
     * order is only ever a placeholder pre-sort.
     */
    @Query(
        """
            SELECT MIN(playlist_song_join.sortOrder) as sortOrder, playlist_song_join.id as playlistSongId, songs.*
            FROM playlist_song_join
            JOIN songs ON songs.id = playlist_song_join.songId AND songs.blacklisted == 0
            WHERE playlist_song_join.playlistId = :playlistId
            GROUP BY LOWER(songs.album), LOWER(songs.albumArtist)
            """
    )
    abstract fun getCoverSongData(playlistId: Long): Flow<List<PlaylistSongData>>

    fun getCoverSongsForPlaylist(playlistId: Long): Flow<List<PlaylistSong>> = getCoverSongData(playlistId).map { list -> list.map { it.toPlaylistSong() } }

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

private fun PlaylistSongData.toPlaylistSong(): PlaylistSong = PlaylistSong(playlistSongId, sortOrder, songData.toSong())
