package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.SyncOperationDao
import com.simplecityapps.localmediaprovider.local.data.room.entity.SyncOperation
import com.simplecityapps.localmediaprovider.local.data.room.entity.SyncStatus
import com.simplecityapps.mediaprovider.sync.SyncQueueRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import kotlinx.coroutines.flow.Flow

/**
 * Local implementation of SyncQueueRepository using Room database.
 */
class LocalSyncQueueRepository(
    private val syncOperationDao: SyncOperationDao
) : SyncQueueRepository {

    override suspend fun enqueue(operation: SyncOperation): Long = syncOperationDao.insert(operation)

    override suspend fun enqueueAll(operations: List<SyncOperation>) {
        syncOperationDao.insertAll(operations)
    }

    override suspend fun getPendingOperations(): List<SyncOperation> = syncOperationDao.getByStatus(SyncStatus.PENDING)

    override suspend fun getOperation(operationId: Long): SyncOperation? = syncOperationDao.get(operationId)

    override suspend fun updateOperation(operation: SyncOperation) {
        syncOperationDao.update(operation)
    }

    override suspend fun getOperationsByPlaylist(playlistId: Long): List<SyncOperation> = syncOperationDao.getByPlaylist(playlistId)

    override suspend fun getOperationsByStatus(status: SyncStatus): List<SyncOperation> = syncOperationDao.getByStatus(status)

    override fun observeOperationsByStatuses(statuses: List<SyncStatus>): Flow<List<SyncOperation>> = syncOperationDao.observeByStatuses(statuses)

    override suspend fun deleteOperation(operationId: Long) {
        syncOperationDao.delete(operationId)
    }

    override suspend fun deleteOperationsByPlaylist(playlistId: Long) {
        syncOperationDao.deleteByPlaylist(playlistId)
    }

    override suspend fun deleteOperationsByStatus(status: SyncStatus) {
        syncOperationDao.deleteByStatus(status)
    }

    override suspend fun countByStatus(status: SyncStatus): Int = syncOperationDao.countByStatus(status)

    override suspend fun hasPendingOperations(playlistId: Long): Boolean {
        val count = syncOperationDao.countByPlaylistAndStatuses(
            playlistId,
            listOf(SyncStatus.PENDING, SyncStatus.IN_PROGRESS)
        )
        return count > 0
    }

    override suspend fun getOperationsByProvider(
        mediaProviderType: MediaProviderType,
        status: SyncStatus
    ): List<SyncOperation> = syncOperationDao.getByProviderAndStatus(mediaProviderType, status)
}
