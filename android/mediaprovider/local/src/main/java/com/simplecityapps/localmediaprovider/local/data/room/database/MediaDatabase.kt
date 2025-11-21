package com.simplecityapps.localmediaprovider.local.data.room.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.simplecityapps.localmediaprovider.local.data.room.Converters
import com.simplecityapps.localmediaprovider.local.data.room.dao.DownloadDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.PlaylistDataDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.PlaylistSongJoinDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao
import com.simplecityapps.localmediaprovider.local.data.room.entity.DownloadData
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistData
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistSongJoin
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData

@Database(
    entities = [
        SongData::class,
        PlaylistData::class,
        PlaylistSongJoin::class,
        DownloadData::class
    ],
    version = 41,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MediaDatabase : RoomDatabase() {
    abstract fun songDataDao(): SongDataDao

    abstract fun playlistSongJoinDataDao(): PlaylistSongJoinDao

    abstract fun playlistDataDao(): PlaylistDataDao

    abstract fun downloadDao(): DownloadDao
}
