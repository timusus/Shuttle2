package com.simplecityapps.mediaprovider

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * A set each change of which swaps in a new copy, so it can be changed from any thread while another iterates it: an
 * iteration, or [snapshot], reads the set as it was when it began and never sees a change made meanwhile.
 */
internal class CopyOnWriteSet<E> : AbstractMutableSet<E>() {
    private val elements = MutableStateFlow<Set<E>>(emptySet())

    /** The set as it is now, which no later change touches. */
    val snapshot: Set<E> get() = elements.value

    override val size: Int get() = snapshot.size

    override fun contains(element: E): Boolean = element in snapshot

    override fun add(element: E): Boolean = change { it + element }

    override fun remove(element: E): Boolean = change { it - element }

    override fun clear() {
        elements.value = emptySet()
    }

    override fun iterator(): MutableIterator<E> = object : MutableIterator<E> {
        private val iterator = snapshot.iterator()
        private var last: E? = null

        override fun hasNext(): Boolean = iterator.hasNext()

        override fun next(): E = iterator.next().also { last = it }

        @Suppress("UNCHECKED_CAST")
        override fun remove() {
            this@CopyOnWriteSet.remove(last as E)
        }
    }

    /** Swaps in [transform] of the current copy; whether that changed it. */
    private inline fun change(transform: (Set<E>) -> Set<E>): Boolean {
        var changed = false
        elements.update { current -> transform(current).also { changed = it.size != current.size } }
        return changed
    }
}
