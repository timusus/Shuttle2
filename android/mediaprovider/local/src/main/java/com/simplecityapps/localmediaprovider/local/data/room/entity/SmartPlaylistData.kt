package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/** A user smart playlist; [rulesJson] is its rules as `SmartRulesCodec` writes them. */
@Entity(tableName = "smart_playlists")
data class SmartPlaylistData(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "rulesJson") val rulesJson: String,
    @ColumnInfo(name = "createdAt") val createdAt: Date
)
