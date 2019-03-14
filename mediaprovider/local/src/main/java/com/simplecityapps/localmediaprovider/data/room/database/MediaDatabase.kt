package com.simplecityapps.localmediaprovider.data.room.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.simplecityapps.localmediaprovider.data.room.Converters
import com.simplecityapps.localmediaprovider.data.room.dao.AlbumArtistDataDao
import com.simplecityapps.localmediaprovider.data.room.dao.AlbumDataDao
import com.simplecityapps.localmediaprovider.data.room.dao.SongDataDao
import com.simplecityapps.localmediaprovider.data.room.entity.AlbumArtistData
import com.simplecityapps.localmediaprovider.data.room.entity.AlbumData
import com.simplecityapps.localmediaprovider.data.room.entity.SongData

@Database(
    entities = [
        SongData::class,
        AlbumArtistData::class,
        AlbumData::class
    ],
    version = 22,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MediaDatabase : RoomDatabase() {

    abstract fun songDataDao(): SongDataDao

    abstract fun albumDataDao(): AlbumDataDao

    abstract fun albumArtistDataDao(): AlbumArtistDataDao
}