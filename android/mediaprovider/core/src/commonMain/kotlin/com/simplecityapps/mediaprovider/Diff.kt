package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * Matches [newData] against [existingData]: what's only new is inserted, what's in both is updated, and, with
 * [deleteMissing], what's only existing is deleted. Without it [newData] is a partial listing (an incremental sync's
 * changes), so nothing is deleted.
 */
abstract class Diff<T>(
    private val existingData: List<T>,
    private val newData: List<T>,
    private val deleteMissing: Boolean = true
) {
    private val logger = Logger.tagged("Diff")

    class Result<T>(
        val inserts: List<T>,
        val updates: List<T>,
        val deletes: List<T>
    ) {
        override fun toString(): String = "${inserts.count()} inserts, ${updates.count()} updates, ${deletes.count()} deletes"
    }

    /**
     * What identifies an entry: two entries with equal keys represent the same data
     */
    abstract fun key(item: T): Any

    /**
     * Whether [updated], as [update] built it from [oldData], differs from what's stored in a way that needs a write
     */
    open fun isChanged(
        oldData: T,
        updated: T
    ): Boolean = true

    /**
     * When two items are considered equal, we have an opportunity to copy data from the old entry to the new
     */
    abstract fun update(
        oldData: T,
        newData: T
    ): T

    suspend fun apply(): Result<T> {
        if (existingData.isEmpty()) {
            return Result(inserts = dedupe(), updates = emptyList(), deletes = emptyList())
        }

        if (newData.isEmpty()) {
            return Result(inserts = emptyList(), updates = emptyList(), deletes = if (deleteMissing) existingData else emptyList())
        }

        return withContext(Dispatchers.IO) {
            val existingByKey = existingData.associateBy { key(it) }
            val inserts = mutableListOf<T>()
            val updates = mutableListOf<T>()
            val seen = HashSet<Any>(newData.size)
            for (item in dedupe()) {
                val key = key(item)
                seen.add(key)
                val oldData = existingByKey[key]
                if (oldData == null) {
                    // Data which exist in the new dataset, but not in the old
                    inserts.add(item)
                } else {
                    // Data which exist in both, with their previous IDs restored; unchanged ones need no write
                    val updated = update(oldData, item)
                    if (isChanged(oldData, updated)) updates.add(updated)
                }
            }

            // Data which exist in the old dataset, but not in the new
            val deletes = if (deleteMissing) existingData.filter { key(it) !in seen } else emptyList()

            Result(inserts, updates, deletes)
        }
    }

    /** [newData] with one entry per key: the last one listed wins, in the position of the first. */
    private fun dedupe(): List<T> {
        val byKey = LinkedHashMap<Any, T>(newData.size)
        var duplicates = 0
        for (item in newData) {
            if (byKey.put(key(item), item) != null) duplicates++
        }
        if (duplicates > 0) logger.warn { "Ignoring $duplicates entries with a duplicate key" }
        return if (duplicates == 0) newData else byKey.values.toList()
    }
}
