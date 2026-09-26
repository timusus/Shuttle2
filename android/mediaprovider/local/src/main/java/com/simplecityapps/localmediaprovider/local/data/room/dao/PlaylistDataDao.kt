package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistData
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistSongJoin
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
abstract class PlaylistDataDao {
    @Insert
    abstract suspend fun insert(playlistData: PlaylistData): Long

    @Insert
    abstract suspend fun insertSongJoins(playlistSongJoins: List<PlaylistSongJoin>)

    /**
     * Inserts [playlistData] holding the songs with [songIds], in that order, as one transaction: if a song
     * can't be added, no playlist is left behind either.
     */
    @Transaction
    open suspend fun insert(
        playlistData: PlaylistData,
        songIds: List<Long>
    ): Long {
        val playlistId = insert(playlistData)
        insertSongJoins(songJoins(playlistId, songIds, firstSortOrder = 0))
        return playlistId
    }

    @Update
    abstract suspend fun update(playlistData: PlaylistData)

    @Query("SELECT * FROM playlists WHERE mediaProvider = :mediaProviderType AND externalId = :externalId ORDER BY id LIMIT 1")
    abstract suspend fun getImportedPlaylistData(
        mediaProviderType: MediaProviderType,
        externalId: String
    ): PlaylistData?

    @Query("SELECT songId FROM playlist_song_join WHERE playlistId = :playlistId ORDER BY sortOrder")
    abstract suspend fun getSongIds(playlistId: Long): List<Long>

    /**
     * Stores the playlist [playlistData]'s provider imported from its [PlaylistData.externalId] source, holding the songs with
     * [songIds], as one transaction: inserts it the first time that source is found, and afterwards renames the playlist
     * imported from it, then gives it exactly [songIds] if [replaceSongs], or else adds those of them it doesn't hold yet.
     */
    @Transaction
    open suspend fun storeImported(
        playlistData: PlaylistData,
        songIds: List<Long>,
        replaceSongs: Boolean
    ) {
        val existing = getImportedPlaylistData(playlistData.mediaProviderType, checkNotNull(playlistData.externalId))
        if (existing == null) {
            insert(playlistData, songIds)
            return
        }
        if (existing.name != playlistData.name) {
            update(existing.copy(name = playlistData.name))
        }
        val heldSongIds = getSongIds(existing.id)
        if (replaceSongs) {
            if (heldSongIds != songIds) {
                clear(existing.id)
                insertSongJoins(songJoins(existing.id, songIds, firstSortOrder = 0))
            }
        } else {
            insertSongJoins(songJoins(existing.id, songIds.filterNot { songId -> songId in heldSongIds }, firstSortOrder = heldSongIds.size))
        }
    }

    private fun songJoins(
        playlistId: Long,
        songIds: List<Long>,
        firstSortOrder: Int
    ): List<PlaylistSongJoin> = songIds.mapIndexed { i, songId ->
        PlaylistSongJoin(
            playlistId = playlistId,
            songId = songId,
            sortOrder = (firstSortOrder + i).toLong()
        )
    }

    @Query(
        """
            SELECT playlists.*, count(songs.id) as songCount, sum(songs.duration) as duration, playlists.sortOrder as sortOrder, playlists.sortDescending as sortDescending, playlists.mediaProvider, playlists.externalId
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
            SELECT playlists.*, count(songs.id) as songCount, sum(songs.duration) as duration, playlists.sortOrder as sortOrder, playlists.sortDescending as sortDescending, playlists.mediaProvider, playlists.externalId
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
    val sortDescending: Boolean,
    val mediaProvider: MediaProviderType,
    val externalId: String?
)

fun PlaylistEntity.toPlaylist(): Playlist = Playlist(
    id = id,
    name = name,
    songCount = songCount,
    duration = duration ?: 0,
    sortOrder = sortOrder,
    sortDescending = sortDescending,
    mediaProvider = mediaProvider,
    externalId = externalId
)
