package com.simplecityapps.localmediaprovider

/**
 * @param updates a [List] of [Pair]s of items requiring update. [Pair.first] represents the old item, [Pair.second] represents the new.
 * @param insertions a [List] of new items to be inserted
 * @param deletions a [List] of old items to be deleted
 * @param unchanged a [List] of [Pair]s of items which are considered identical. [Pair.first] represents the old item, [Pair.second] represents the new.
 */
class Diff<T>(val updates: List<Pair<T, T>>, val insertions: List<T>, val deletions: List<T>, val unchanged: List<Pair<T, T>>) {

    companion object {
        /**
         * Compares two lists of data, returning a [Diff] of insertions, removals and updates.
         *
         * Items are compared for equality via the [equalityPredicate].
         *
         * Items which are considered equal are then passed to the [contentsEqualityPredicate]. If the contents are not equal, the items are considered out-of-date and added to [Diff.updates].
         *
         * Items in [newData] which are not present in [oldData] are added to [Diff.insertions]
         *
         * Items in [oldData] which are not present in [newData] are added to [Diff.deletions]
         *
         *
         * @return a [Diff], containing items to be inserted, removed and updated.
         */
        fun <T> diff(
            oldData: List<T>,
            newData: List<T>,
            equalityPredicate: (a: T, b: T) -> Boolean,
            contentsEqualityPredicate: ((a: T, b: T) -> Boolean) = { _, _ -> true }
        ): Diff<T> {

            // Items which are found in both the old and new data set, according to the equality predicate
            val matches = newData.mapNotNull { newItem ->
                oldData.firstOrNull { oldItem -> equalityPredicate(oldItem, newItem) }?.let { oldItem ->
                    Pair(oldItem, newItem)
                }
            }

            // Items to be inserted are those in the new data set, minus those which already exist
            val inserts = newData.minus(matches.map { match -> match.second })

            // Items to be deleted are those in the old data set, minus any matching items
            val deletes = oldData.minus(matches.map { match -> match.first })

            // Items which are equal, but whose contents don't match, need to be updated
            val updates = matches.filterNot { match -> contentsEqualityPredicate(match.first, match.second) }

            // Items which are equal, and whose contents match
            val unchanged = matches.minus(updates)

            return Diff(updates, inserts, deletes, unchanged)
        }
    }
}

