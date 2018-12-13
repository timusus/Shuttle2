package com.simplecityapps.localmediaprovider.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.REPLACE
import androidx.room.Query
import com.simplecityapps.localmediaprovider.data.room.entity.SongData
import com.simplecityapps.mediaprovider.model.Song
import io.reactivex.Flowable

@Dao
abstract class SongDataDao {

    fun getAllDistinct(): Flowable<List<Song>> {
        return getAll().distinctUntilChanged()
    }

    @Query("SELECT " +
            "songs.*, " +
            "album_artists.name as albumArtistName, " +
            "albums.name as albumName " +
            "FROM songs " +
            "INNER JOIN album_artists ON album_artists.id = songs.albumArtistId " +
            "INNER JOIN albums ON albums.id = songs.albumId " +
            "ORDER BY albumArtistName, albumName, track;")
    protected abstract fun getAll(): Flowable<List<Song>>

    @Insert(onConflict = REPLACE)
    abstract fun insert(songData: SongData): Long

    @Insert(onConflict = REPLACE)
    abstract fun insertAll(songData: List<SongData>): List<Long>

    @Query("DELETE from songs")
    abstract fun deleteAll()
}