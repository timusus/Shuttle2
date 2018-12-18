package com.simplecityapps.localmediaprovider.data.room.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.simplecityapps.localmediaprovider.data.room.dao.AlbumDataDao
import com.simplecityapps.localmediaprovider.data.room.dao.SongDataDao
import com.simplecityapps.localmediaprovider.data.room.entity.AlbumArtistData
import com.simplecityapps.localmediaprovider.data.room.entity.AlbumData
import com.simplecityapps.localmediaprovider.data.room.entity.SongData
import com.simplecityapps.mediaprovider.data.room.dao.AlbumArtistDataDao

@Database(
    entities = [
        SongData::class,
        AlbumArtistData::class,
        AlbumData::class
    ],
    version = 12,
    exportSchema = false
)
abstract class MediaDatabase : RoomDatabase() {

    abstract fun songDataDao(): SongDataDao

    abstract fun albumDataDao(): AlbumDataDao

    abstract fun albumArtistDataDao(): AlbumArtistDataDao

}