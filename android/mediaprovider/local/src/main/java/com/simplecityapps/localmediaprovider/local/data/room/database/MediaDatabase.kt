package com.simplecityapps.localmediaprovider.local.data.room.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.simplecityapps.localmediaprovider.local.data.room.Converters
import com.simplecityapps.localmediaprovider.local.data.room.dao.PinnedCollectionDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.PlaylistDataDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.PlaylistSongJoinDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.SmartPlaylistDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao
import com.simplecityapps.localmediaprovider.local.data.room.entity.PinnedCollectionData
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistData
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistSongJoin
import com.simplecityapps.localmediaprovider.local.data.room.entity.SmartPlaylistData
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData

@Database(
    entities = [
        SongData::class,
        PlaylistData::class,
        PlaylistSongJoin::class,
        PinnedCollectionData::class,
        SmartPlaylistData::class
    ],
    version = 46,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MediaDatabase : RoomDatabase() {
    abstract fun songDataDao(): SongDataDao

    abstract fun playlistSongJoinDataDao(): PlaylistSongJoinDao

    abstract fun playlistDataDao(): PlaylistDataDao

    abstract fun pinnedCollectionDao(): PinnedCollectionDao

    abstract fun smartPlaylistDao(): SmartPlaylistDao
}
