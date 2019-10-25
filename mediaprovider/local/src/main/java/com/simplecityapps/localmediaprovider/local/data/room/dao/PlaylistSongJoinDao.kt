package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomWarnings
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistSongJoin
import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.model.Song
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single

@Dao
abstract class PlaylistSongJoinDao {

    @Insert
    abstract fun insert(playlistSongJoin: PlaylistSongJoin): Completable

    @Insert
    abstract fun insert(playlistSongJoins: List<PlaylistSongJoin>): Completable

    @Query(
        "SELECT playlists.*, " +
                "count(songs.id) as songCount, " +
                "sum(songs.duration) as duration " +
                "FROM playlists " +
                "LEFT JOIN playlist_song_join ON playlists.id = playlist_song_join.playlistId " +
                "LEFT JOIN songs ON songs.id = playlist_song_join.songId " +
                "GROUP BY playlists.id " +
                "ORDER BY playlists.name;"
    )
    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
    abstract fun getAll(): Flowable<List<Playlist>>

    @Query(
        "SELECT playlists.*, " +
                "count(songs.id) as songCount, " +
                "sum(songs.duration) as duration " +
                "FROM playlists " +
                "LEFT JOIN playlist_song_join ON playlists.id = playlist_song_join.playlistId " +
                "LEFT JOIN songs ON songs.id = playlist_song_join.songId " +
                "WHERE playlists.id = :playlistId " +
                "GROUP BY playlists.id " +
                "ORDER BY playlists.name;"
    )
    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
    abstract fun getPlaylist(playlistId: Long): Single<Playlist>

    @Query(
        "SELECT songs.*, " +
                "album_artists.name as albumArtistName, " +
                "albums.name as albumName " +
                "FROM songs " +
                "INNER JOIN album_artists ON album_artists.id = songs.albumArtistId " +
                "INNER JOIN albums ON albums.id = songs.albumId " +
                "INNER JOIN playlist_song_join ON songs.id = playlist_song_join.songId " +
                "WHERE playlist_song_join.playlistId = :playlistId;"
    )
    abstract fun getSongsForPlaylist(playlistId: Long): Flowable<List<Song>>
}