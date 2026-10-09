package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.simplecityapps.shuttle.model.MediaProviderType

/**
 * One audio row of MediaStore's listing as [provider]'s last stored import read it (#875), every folder's: the folder
 * rules apply after. [id] is MediaStore's `_ID` and [generation] its `GENERATION_MODIFIED` (API 30), which MediaStore
 * raises on every change to the row, so the next import reads again only the rows whose generation moved and keeps the
 * rest. Each provider keeps its own: one's import moving past a change mustn't hide it from the other's.
 */
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
