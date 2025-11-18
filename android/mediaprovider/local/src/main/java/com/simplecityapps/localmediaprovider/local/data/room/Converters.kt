package com.simplecityapps.localmediaprovider.local.data.room

import androidx.room.TypeConverter
import com.simplecityapps.localmediaprovider.local.data.room.entity.OperationType
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistSyncStatus
import com.simplecityapps.localmediaprovider.local.data.room.entity.SyncStatus
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.sorting.SongSortOrder
import java.util.Date
import kotlinx.datetime.Instant

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time

    @TypeConverter
    fun fromString(string: String): List<String> = string.split(";").filter { it.isNotEmpty() }.map { it }

    @TypeConverter
    fun toString(stringList: List<String>): String = stringList.joinToString(separator = ";")

    @TypeConverter
    fun fromMediaProvider(mediaProviderType: MediaProviderType): String = mediaProviderType.name

    @TypeConverter
    fun toMediaProvider(string: String): MediaProviderType = MediaProviderType.valueOf(string)

    @TypeConverter
    fun fromSortOrder(songSortOrder: SongSortOrder): String = songSortOrder.name

    @TypeConverter
    fun toSortOrder(string: String): SongSortOrder = try {
        SongSortOrder.valueOf(string)
    } catch (e: IllegalArgumentException) {
        SongSortOrder.Default
    }

    // Sync-related type converters

    @TypeConverter
    fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilliseconds()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.fromEpochMilliseconds(it) }

    @TypeConverter
    fun fromOperationType(type: OperationType): String = type.name

    @TypeConverter
    fun toOperationType(value: String): OperationType = OperationType.valueOf(value)

    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

    @TypeConverter
    fun fromPlaylistSyncStatus(status: PlaylistSyncStatus): String = status.name

    @TypeConverter
    fun toPlaylistSyncStatus(value: String): PlaylistSyncStatus = PlaylistSyncStatus.valueOf(value)
}
