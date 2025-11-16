package com.simplecityapps.localmediaprovider.local.data.room.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.simplecityapps.localmediaprovider.local.data.room.Converters
import com.simplecityapps.localmediaprovider.local.data.room.dao.PlaylistDataDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.PlaylistSongJoinDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.PlaylistSyncStateDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.SyncOperationDao
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistData
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistSongJoin
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistSyncState
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.localmediaprovider.local.data.room.entity.SyncOperation

@Database(
    entities = [
        SongData::class,
        PlaylistData::class,
        PlaylistSongJoin::class,
        SyncOperation::class,
        PlaylistSyncState::class
    ],
    version = 41,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MediaDatabase : RoomDatabase() {
    abstract fun songDataDao(): SongDataDao

    abstract fun playlistSongJoinDataDao(): PlaylistSongJoinDao

    abstract fun playlistDataDao(): PlaylistDataDao

    abstract fun syncOperationDao(): SyncOperationDao

    abstract fun playlistSyncStateDao(): PlaylistSyncStateDao
}
