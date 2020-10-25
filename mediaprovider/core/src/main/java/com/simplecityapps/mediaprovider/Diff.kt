package com.simplecityapps.mediaprovider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

abstract class Diff<T>(private val existingData: List<T>, private val newData: List<T>) {

    class Result<T>(
        val inserts: List<T>,
        val updates: List<T>,
        val deletes: List<T>
    ) {
        override fun toString(): String {
            return "${inserts.count()} inserts, ${updates.count()} updates, ${deletes.count()} deletes"
        }
    }

    /**
     * Determine whether two entries represent the same data
     */
    abstract fun isEqual(a: T, b: T): Boolean

    /**
     * When two items are considered equal, we have an opportunity to copy data from the old entry to the new
     */
    abstract fun update(oldData: T, newData: T): T

    suspend fun apply(): Result<T> {
        if (existingData.isEmpty()) {
            return Result(inserts = newData, updates = emptyList(), deletes = emptyList())
        }

        if (newData.isEmpty()) {
            return Result(inserts = emptyList(), updates = emptyList(), deletes = existingData)
        }

        return withContext(Dispatchers.IO) {
            // Data which exist in the new dataset, but not in the old
            val inserts = newData.filterNot { newData -> existingData.any { oldData -> isEqual(oldData, newData) } }

            // Data which exist in the new dataset, as well as the old
            var updates = (newData - inserts)
            // Updates need their previous ID's restored
            if (updates.isNotEmpty()) {
                updates = updates.map { newData -> update(existingData.first { oldData -> isEqual(oldData, newData) }, newData) }
            }

            // Data which exist in the old dataset, but not in the new
            val deletes = existingData.filter { oldData -> newData.none { newData -> isEqual(oldData, newData) } }

            Result(inserts, updates, deletes)
        }
    }
}