package com.simplecityapps.shuttle.persistence

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update

/**
 * A [KeyValueStore] and [SecureStore] held in memory, for tests and previews. Behaves as SharedPreferences does:
 * a read of a key stored with another type throws, and storing a null string removes the key.
 */
class InMemoryKeyValueStore(
    initialValues: Map<String, Any> = emptyMap()
) : KeyValueStore,
    SecureStore {
    private val state = MutableStateFlow(initialValues.toMap())

    // A key per write (null when cleared); dropping the oldest never loses a change, since each emission re-reads
    private val writes = MutableSharedFlow<String?>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Everything stored, for assertions. */
    val values: Map<String, Any> get() = state.value

    override fun contains(key: String): Boolean = key in state.value

    override fun getBoolean(
        key: String,
        default: Boolean
    ): Boolean = read(key) ?: default

    override fun getInt(
        key: String,
        default: Int
    ): Int = read(key) ?: default

    override fun getLong(
        key: String,
        default: Long
    ): Long = read(key) ?: default

    override fun getFloat(
        key: String,
        default: Float
    ): Float = read(key) ?: default

    override fun getString(
        key: String,
        default: String?
    ): String? = read(key) ?: default

    // A value of another type throws ClassCastException, as SharedPreferences' typed getters do
    private inline fun <reified T : Any> read(key: String): T? = state.value[key]?.let { it as T }

    override fun edit(block: KeyValueStore.Editor.() -> Unit) {
        val editor = MapEditor().apply(block)
        state.update { values -> values - editor.removals + editor.puts }
        (editor.removals + editor.puts.keys).forEach { key -> writes.tryEmit(key) }
    }

    /** Forgets every value, as `SharedPreferences.Editor.clear()` does. */
    fun clear() {
        state.value = emptyMap()
        writes.tryEmit(null)
    }

    override fun changes(key: String): Flow<Unit> = writes
        .onSubscription { emit(key) }
        .filter { changedKey -> changedKey == null || changedKey == key }
        .map { }

    override fun getString(key: String): String? = getString(key, null)

    override fun putString(
        key: String,
        value: String?
    ) = edit { putString(key, value) }

    override fun getBoolean(key: String): Boolean = getBoolean(key, false)

    override fun putBoolean(
        key: String,
        value: Boolean
    ) = edit { putBoolean(key, value) }

    private class MapEditor : KeyValueStore.Editor {
        val puts = mutableMapOf<String, Any>()
        val removals = mutableSetOf<String>()

        private fun put(
            key: String,
            value: Any
        ) {
            removals -= key
            puts[key] = value
        }

        override fun putBoolean(
            key: String,
            value: Boolean
        ) = put(key, value)

        override fun putInt(
            key: String,
            value: Int
        ) = put(key, value)

        override fun putLong(
            key: String,
            value: Long
        ) = put(key, value)

        override fun putFloat(
            key: String,
            value: Float
        ) = put(key, value)

        override fun putString(
            key: String,
            value: String?
        ) = if (value == null) remove(key) else put(key, value)

        override fun remove(key: String) {
            puts -= key
            removals += key
        }
    }
}
