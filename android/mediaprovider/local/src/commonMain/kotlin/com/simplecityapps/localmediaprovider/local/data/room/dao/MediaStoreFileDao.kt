package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.simplecityapps.localmediaprovider.local.data.room.entity.MediaStoreFileData
import com.simplecityapps.localmediaprovider.local.data.room.entity.MediaStoreScanStateData
import com.simplecityapps.shuttle.model.MediaProviderType

/** The MediaStore listing each local provider's last stored import read, for its next one to read only what changed since (#875). */
@Dao
abstract class MediaStoreFileDao {
    @Query("SELECT * FROM media_store_files WHERE provider = :provider")
    abstract suspend fun files(provider: MediaProviderType): List<MediaStoreFileData>

    @Query("SELECT version FROM media_store_scan_state WHERE provider = :provider")
    abstract suspend fun version(provider: MediaProviderType): String?

    /** [provider]'s listing as [version] read it whole: what it stored before is dropped. */
    @Transaction
    open suspend fun replaceAll(
        provider: MediaProviderType,
        version: String,
        files: List<MediaStoreFileData>
    ) {
        deleteAll(provider)
        upsert(files)
        setState(MediaStoreScanStateData(provider, version))
    }

    /** [provider]'s listing [version] read again only in part: [deletes] are the ids gone from it, [upserts] the rows read again. */
    @Transaction
    open suspend fun applyChanges(
        provider: MediaProviderType,
        version: String,
        deletes: List<Long>,
        upserts: List<MediaStoreFileData>
    ) {
        // Chunked: SQLite caps the variables one statement can bind
        deletes.chunked(500).forEach { ids -> delete(provider, ids) }
        upsert(upserts)
        setState(MediaStoreScanStateData(provider, version))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(files: List<MediaStoreFileData>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun setState(state: MediaStoreScanStateData)

    @Query("DELETE FROM media_store_files WHERE provider = :provider AND id IN (:ids)")
    abstract suspend fun delete(
        provider: MediaProviderType,
        ids: List<Long>
    )

    @Query("DELETE FROM media_store_files WHERE provider = :provider")
    abstract suspend fun deleteAll(provider: MediaProviderType)
}
