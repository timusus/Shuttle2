package com.simplecityapps.localmediaprovider.local.data.room.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.simplecityapps.localmediaprovider.local.data.room.Converters
import com.simplecityapps.localmediaprovider.local.data.room.dao.PinnedCollectionDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.PlayEventDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.PlaylistDataDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.PlaylistSongJoinDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.SmartPlaylistDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.SuggestionsDao
import com.simplecityapps.localmediaprovider.local.data.room.entity.PendingFavouriteData
import com.simplecityapps.localmediaprovider.local.data.room.entity.PinnedCollectionData
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlayEventData
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
        SmartPlaylistData::class,
        PendingFavouriteData::class,
        PlayEventData::class
    ],
    version = 49,
    exportSchema = true
)
@TypeConverters(Converters::class)
@ConstructedBy(MediaDatabaseConstructor::class)
abstract class MediaDatabase : RoomDatabase() {
    abstract fun songDataDao(): SongDataDao

    abstract fun playlistSongJoinDataDao(): PlaylistSongJoinDao

    abstract fun playlistDataDao(): PlaylistDataDao

    abstract fun pinnedCollectionDao(): PinnedCollectionDao

    abstract fun smartPlaylistDao(): SmartPlaylistDao

    abstract fun playEventDao(): PlayEventDao

    abstract fun suggestionsDao(): SuggestionsDao
}

// Room generates the actual for each target.
@Suppress("KotlinNoActualForExpect")
expect object MediaDatabaseConstructor : RoomDatabaseConstructor<MediaDatabase> {
    override fun initialize(): MediaDatabase
}
