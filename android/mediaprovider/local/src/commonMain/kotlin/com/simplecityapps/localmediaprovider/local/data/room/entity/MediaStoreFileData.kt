package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One audio row of MediaStore's listing as the last stored local import read it (#875), every folder's: the folder rules
 * apply after. [id] is MediaStore's `_ID` and [generation] its `GENERATION_MODIFIED` (API 30), which MediaStore raises on
 * every change to the row, so the next import reads again only the rows whose generation moved and keeps the rest.
 */
@Entity(tableName = "media_store_files")
data class MediaStoreFileData(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long,
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
 * The MediaStore the [MediaStoreFileData] rows were read from: one row, whose [version] is `MediaStore.getVersion`. A new
 * version is a rebuilt index, whose ids and generations can't be compared with the stored ones.
 */
@Entity(tableName = "media_store_scan_state")
data class MediaStoreScanStateData(
    @PrimaryKey @ColumnInfo(name = "id") val id: Int = 0,
    @ColumnInfo(name = "version") val version: String
)
