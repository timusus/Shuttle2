package com.simplecityapps.shuttle.scrobbling.queue

/**
 * [ScrobbleDao] in a list, for commonTest (Room needs a Context on Android and a native driver on iOS). Keeps the
 * queries' semantics: the unique (service, startedAt, track) key ignores a repeat, ids auto-increment, batches come
 * oldest first. ScrobbleDaoTest checks the real queries against Room.
 */
class FakeScrobbleDao : ScrobbleDao() {
    private val rows = mutableListOf<QueuedScrobbleEntity>()
    private var nextId = 1L

    override suspend fun enqueue(entity: QueuedScrobbleEntity) {
        if (rows.any { it.service == entity.service && it.startedAtEpochSec == entity.startedAtEpochSec && it.track == entity.track }) return
        rows += entity.copy(id = nextId++)
    }

    override suspend fun oldestBatch(
        service: String,
        limit: Int
    ): List<QueuedScrobbleEntity> = rows.filter { it.service == service }.sortedBy { it.startedAtEpochSec }.take(limit)

    override suspend fun count(service: String): Int = rows.count { it.service == service }

    override suspend fun deleteByIds(ids: List<Long>) {
        rows.removeAll { it.id in ids }
    }

    override suspend fun deleteAll(service: String) {
        rows.removeAll { it.service == service }
    }

    override suspend fun deleteOlderThan(cutoffEpochSec: Long): Int {
        val stale = rows.filter { it.startedAtEpochSec < cutoffEpochSec }
        rows.removeAll(stale)
        return stale.size
    }

    override suspend fun trimToNewest(
        service: String,
        keep: Int
    ) {
        val kept = rows.filter { it.service == service }.sortedByDescending { it.startedAtEpochSec }.take(keep).map { it.id }.toSet()
        rows.removeAll { it.service == service && it.id !in kept }
    }
}

class FakeScrobbleFlushScheduler : ScrobbleFlushScheduler {
    var scheduled = 0
        private set

    override fun scheduleFlush() {
        scheduled++
    }
}
