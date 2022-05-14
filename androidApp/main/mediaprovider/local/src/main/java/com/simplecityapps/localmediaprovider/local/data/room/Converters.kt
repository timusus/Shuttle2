package com.simplecityapps.localmediaprovider.local.data.room

import androidx.room.TypeConverter
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.sorting.SongSortOrder
import java.util.*

class Converters {

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromString(string: String): List<String> {
        return string.split(";").filter { it.isNotEmpty() }.map { it }
    }

    @TypeConverter
    fun toString(stringList: List<String>): String {
        return stringList.joinToString(separator = ";")
    }

    @TypeConverter
    fun fromMediaProvider(mediaProviderType: MediaProviderType): String {
        return mediaProviderType.name
    }

    @TypeConverter
    fun toMediaProvider(string: String): MediaProviderType {
        return MediaProviderType.valueOf(string)
    }

    @TypeConverter
    fun fromSortOrder(songSortOrder: SongSortOrder): String {
        return songSortOrder.name
    }

    @TypeConverter
    fun toSortOrder(string: String): SongSortOrder {
        return try {
            SongSortOrder.valueOf(string)
        } catch (e: IllegalArgumentException) {
            SongSortOrder.Default
        }
    }
}
