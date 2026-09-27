package com.simplecityapps.localmediaprovider.local.data.room

import androidx.room.TypeConverter
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.sorting.SongSortOrder
import kotlin.time.Instant

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? = value?.let(Instant::fromEpochMilliseconds)

    @TypeConverter
    fun instantToTimestamp(instant: Instant?): Long? = instant?.toEpochMilliseconds()

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
}
