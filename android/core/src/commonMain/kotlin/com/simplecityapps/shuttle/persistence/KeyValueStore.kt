package com.simplecityapps.shuttle.persistence

import kotlinx.coroutines.flow.Flow

/**
 * A file of typed values under string keys: SharedPreferences on Android ([SharedPreferencesKeyValueStore]),
 * NSUserDefaults on iOS, [InMemoryKeyValueStore] in tests.
 *
 * Reads return [default] for a key with nothing stored. Reading a key stored with another type throws (as
 * SharedPreferences does), which [com.simplecityapps.shuttle.settings.Setting] turns back into its default.
 */
interface KeyValueStore {
    fun contains(key: String): Boolean

    fun getBoolean(
        key: String,
        default: Boolean
    ): Boolean

    fun getInt(
        key: String,
        default: Int
    ): Int

    fun getLong(
        key: String,
        default: Long
    ): Long

    fun getFloat(
        key: String,
        default: Float
    ): Float

    fun getString(
        key: String,
        default: String?
    ): String?

    /** Writes the changes [block] makes together. */
    fun edit(block: Editor.() -> Unit)

    /**
     * Emits once when collected, then each time [key] is written or removed, or the whole store is cleared: the
     * listener is registered before the first emission, so no change between the two is missed.
     */
    fun changes(key: String): Flow<Unit>

    interface Editor {
        fun putBoolean(
            key: String,
            value: Boolean
        )

        fun putInt(
            key: String,
            value: Int
        )

        fun putLong(
            key: String,
            value: Long
        )

        fun putFloat(
            key: String,
            value: Float
        )

        /** Stores [value] under [key]; null removes it, as SharedPreferences does. */
        fun putString(
            key: String,
            value: String?
        )

        fun remove(key: String)
    }
}

fun KeyValueStore.putBoolean(
    key: String,
    value: Boolean
) = edit { putBoolean(key, value) }

fun KeyValueStore.putInt(
    key: String,
    value: Int
) = edit { putInt(key, value) }

fun KeyValueStore.putLong(
    key: String,
    value: Long
) = edit { putLong(key, value) }

fun KeyValueStore.putFloat(
    key: String,
    value: Float
) = edit { putFloat(key, value) }

/** Stores [value] under [key]; null removes it. */
fun KeyValueStore.putString(
    key: String,
    value: String?
) = edit { putString(key, value) }

fun KeyValueStore.remove(key: String) = edit { remove(key) }
