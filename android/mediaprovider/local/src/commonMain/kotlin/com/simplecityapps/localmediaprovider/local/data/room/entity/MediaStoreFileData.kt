package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.simplecityapps.shuttle.model.MediaProviderType

/** One MediaStore row as the last stored import read it; the next import re-reads only rows whose generation moved. */
@Entity(tableName = "media_store_files", primaryKeys = ["provider", "id"])
data class MediaStoreFileData(
    @ColumnInfo(name = "provider") val provider: MediaProviderType,
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "generation") val generation: Long,
    @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "displayName") val displayName: String,
    @ColumnInfo(name = "size") val size: Long,
    // Epoch milliseconds
    @ColumnInfo(name = "lastModified") val lastModified: Long,
    @ColumnInfo(name = "mimeType") val mimeType: String?,
    // Milliseconds; null if MediaStore couldn't read it
    @ColumnInfo(name = "duration") val duration: Long?
)

/**
 * The MediaStore [provider]'s [MediaStoreFileData] rows were read from: [version] is `MediaStore.getVersion`. A new
 * version is a rebuilt index, whose ids and generations can't be compared with the stored ones.
 */
@Entity(tableName = "media_store_scan_state", primaryKeys = ["provider"])
data class MediaStoreScanStateData(
    @ColumnInfo(name = "provider") val provider: MediaProviderType,
    @ColumnInfo(name = "version") val version: String
)
