package com.simplecityapps.shuttle.scrobbling.queue

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.IGNORE
import androidx.room.Query

@Dao
abstract class ScrobbleDao {
    /** Idempotent: a row already queued for the same [QueuedScrobbleEntity.service]/startedAt/track is kept as is. */
    @Insert(onConflict = IGNORE)
    abstract suspend fun enqueue(entity: QueuedScrobbleEntity)

    @Query("SELECT * FROM queued_scrobbles WHERE service = :service ORDER BY startedAtEpochSec ASC LIMIT :limit")
    abstract suspend fun oldestBatch(
        service: String,
        limit: Int
    ): List<QueuedScrobbleEntity>

    @Query("SELECT COUNT(*) FROM queued_scrobbles WHERE service = :service")
    abstract suspend fun count(service: String): Int

    @Delete
    abstract suspend fun delete(entities: List<QueuedScrobbleEntity>)

    @Query("DELETE FROM queued_scrobbles WHERE id IN (:ids)")
    abstract suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE queued_scrobbles SET attempts = attempts + 1 WHERE id IN (:ids)")
    abstract suspend fun incrementAttempts(ids: List<Long>)

    /** Last.fm rejects scrobbles this old ([com.simplecityapps.shuttle.scrobbling.queue.ScrobbleQueue.MAX_AGE]). */
    @Query("DELETE FROM queued_scrobbles WHERE startedAtEpochSec < :cutoffEpochSec")
    abstract suspend fun deleteOlderThan(cutoffEpochSec: Long): Int

    /** Keeps only the [keep] newest rows for [service] ([com.simplecityapps.shuttle.scrobbling.queue.ScrobbleQueue.MAX_QUEUE_SIZE]). */
    @Query(
        "DELETE FROM queued_scrobbles WHERE service = :service AND id NOT IN " +
            "(SELECT id FROM queued_scrobbles WHERE service = :service ORDER BY startedAtEpochSec DESC LIMIT :keep)"
    )
    abstract suspend fun trimToNewest(
        service: String,
        keep: Int
    )
}
