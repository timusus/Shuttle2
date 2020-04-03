package com.simplecityapps.localmediaprovider.local.data.room.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.simplecityapps.localmediaprovider.local.data.room.Converters
import com.simplecityapps.localmediaprovider.local.data.room.dao.*
import com.simplecityapps.localmediaprovider.local.data.room.entity.*

@Database(
    entities = [
        SongData::class,
        AlbumArtistData::class,
        AlbumData::class,
        PlaylistData::class,
        PlaylistSongJoin::class
    ],
    version = 28,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MediaDatabase : RoomDatabase() {

    abstract fun songDataDao(): SongDataDao

    abstract fun albumDataDao(): AlbumDataDao

    abstract fun albumArtistDataDao(): AlbumArtistDataDao

    abstract fun playlistSongJoinDataDao(): PlaylistSongJoinDao

    abstract fun playlistDataDao(): PlaylistDataDao
}