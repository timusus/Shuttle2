package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.simplecityapps.localmediaprovider.local.data.room.entity.MediaStoreFileData
import com.simplecityapps.localmediaprovider.local.data.room.entity.MediaStoreScanStateData

/** The MediaStore listing the last stored local import read, for the next one to read only what changed since (#875). */
@Dao
abstract class MediaStoreFileDao {
    @Query("SELECT * FROM media_store_files")
    abstract suspend fun files(): List<MediaStoreFileData>

    @Query("SELECT version FROM media_store_scan_state WHERE id = 0")
    abstract suspend fun version(): String?

    /** The listing as [version] read it whole: what was stored before is dropped. */
    @Transaction
    open suspend fun replaceAll(
        version: String,
        files: List<MediaStoreFileData>
    ) {
        deleteAll()
        upsert(files)
        setState(MediaStoreScanStateData(version = version))
    }

    /** The listing [version] read again only in part: [deletes] are the ids gone from it, [upserts] the rows read again. */
    @Transaction
    open suspend fun applyChanges(
        version: String,
        deletes: List<Long>,
        upserts: List<MediaStoreFileData>
    ) {
        // Chunked: SQLite caps the variables one statement can bind
        deletes.chunked(500).forEach { ids -> delete(ids) }
        upsert(upserts)
        setState(MediaStoreScanStateData(version = version))
    }

    /** Forgets the listing, so the next import reads MediaStore whole. */
    @Transaction
    open suspend fun clear() {
        deleteAll()
        deleteState()
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsert(files: List<MediaStoreFileData>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun setState(state: MediaStoreScanStateData)

    @Query("DELETE FROM media_store_files WHERE id IN (:ids)")
    protected abstract suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM media_store_files")
    protected abstract suspend fun deleteAll()

    @Query("DELETE FROM media_store_scan_state")
    protected abstract suspend fun deleteState()
}
