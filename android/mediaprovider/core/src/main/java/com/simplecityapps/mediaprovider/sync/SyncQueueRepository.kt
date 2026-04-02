package com.simplecityapps.mediaprovider.sync

import com.simplecityapps.localmediaprovider.local.data.room.entity.SyncOperation
import com.simplecityapps.localmediaprovider.local.data.room.entity.SyncStatus
import com.simplecityapps.shuttle.model.MediaProviderType
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing the playlist sync operation queue.
 */
interface SyncQueueRepository {
    /**
     * Enqueue a new sync operation.
     *
     * @param operation The sync operation to enqueue
     * @return The ID of the enqueued operation
     */
    suspend fun enqueue(operation: SyncOperation): Long

    /**
     * Enqueue multiple sync operations.
     */
    suspend fun enqueueAll(operations: List<SyncOperation>)

    /**
     * Get pending operations ordered by priority and creation time.
     */
    suspend fun getPendingOperations(): List<SyncOperation>

    /**
     * Get operation by ID.
     */
    suspend fun getOperation(operationId: Long): SyncOperation?

    /**
     * Update an existing operation.
     */
    suspend fun updateOperation(operation: SyncOperation)

    /**
     * Get operations for a specific playlist.
     */
    suspend fun getOperationsByPlaylist(playlistId: Long): List<SyncOperation>

    /**
     * Get operations by status.
     */
    suspend fun getOperationsByStatus(status: SyncStatus): List<SyncOperation>

    /**
     * Observe operations with specific statuses.
     */
    fun observeOperationsByStatuses(statuses: List<SyncStatus>): Flow<List<SyncOperation>>

    /**
     * Delete an operation.
     */
    suspend fun deleteOperation(operationId: Long)

    /**
     * Delete all operations for a playlist.
     */
    suspend fun deleteOperationsByPlaylist(playlistId: Long)

    /**
     * Delete all operations with a specific status.
     */
    suspend fun deleteOperationsByStatus(status: SyncStatus)

    /**
     * Count operations by status.
     */
    suspend fun countByStatus(status: SyncStatus): Int

    /**
     * Check if there are pending operations for a playlist.
     */
    suspend fun hasPendingOperations(playlistId: Long): Boolean

    /**
     * Get operations for a specific media provider.
     */
    suspend fun getOperationsByProvider(
        mediaProviderType: MediaProviderType,
        status: SyncStatus
    ): List<SyncOperation>
}
