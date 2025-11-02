package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistData
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    abstract fun getAllPlaylistData(): Flow<List<PlaylistEntity>>

    fun getAll(): Flow<List<Playlist>> = getAllPlaylistData().map { list ->
        list.map { playlistData ->
            playlistData.toPlaylist()
        }
    }

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
    abstract suspend fun getPlaylistData(playlistId: Long): PlaylistEntity

    suspend fun getPlaylist(playlistId: Long): Playlist = getPlaylistData(playlistId).toPlaylist()

    @Query("DELETE FROM playlist_song_join WHERE playlistId = :playlistId")
    abstract suspend fun clear(playlistId: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    abstract suspend fun delete(playlistId: Long)

    @Query("DELETE FROM playlists WHERE mediaProvider = :mediaProviderType")
    abstract suspend fun deleteAll(mediaProviderType: MediaProviderType)
}

data class PlaylistEntity(
    val id: Long,
    val name: String,
    val songCount: Int,
    val duration: Int?,
    val sortOrder: PlaylistSongSortOrder,
    val mediaProvider: MediaProviderType,
    val externalId: String?
)

fun PlaylistEntity.toPlaylist(): Playlist = Playlist(
    id = id,
    name = name,
    songCount = songCount,
    duration = duration ?: 0,
    sortOrder = sortOrder,
    mediaProvider = mediaProvider,
    externalId = externalId
)
