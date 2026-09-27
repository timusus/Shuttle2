package com.simplecityapps.shuttle.persistence

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import platform.Foundation.NSNumber
import platform.Foundation.NSUserDefaults

/**
 * A [KeyValueStore] over NSUserDefaults: the standard defaults, or the suite named [suiteName] (the iOS twin of
 * a separate SharedPreferences file).
 *
 * [changes] reports the writes made through this store, the only writer the app has; NSUserDefaults' own change
 * notification doesn't say which key changed.
 */
class UserDefaultsKeyValueStore(
    suiteName: String? = null
) : KeyValueStore {
    private val defaults: NSUserDefaults = suiteName?.let { NSUserDefaults(suiteName = it) } ?: NSUserDefaults.standardUserDefaults

    private val writes = MutableSharedFlow<String>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override fun contains(key: String): Boolean = defaults.objectForKey(key) != null

    override fun getBoolean(
        key: String,
        default: Boolean
    ): Boolean = number(key)?.boolValue ?: default

    override fun getInt(
        key: String,
        default: Int
    ): Int = number(key)?.intValue ?: default

    override fun getLong(
        key: String,
        default: Long
    ): Long = number(key)?.longLongValue ?: default

    override fun getFloat(
        key: String,
        default: Float
    ): Float = number(key)?.floatValue ?: default

    override fun getString(
        key: String,
        default: String?
    ): String? = when (val value = defaults.objectForKey(key)) {
        null -> default
        else -> value as String
    }

    // A value of another type throws ClassCastException, as SharedPreferences' typed getters do
    private fun number(key: String): NSNumber? = defaults.objectForKey(key)?.let { it as NSNumber }

    override fun edit(block: KeyValueStore.Editor.() -> Unit) {
        val changed = mutableListOf<String>()
        object : KeyValueStore.Editor {
            override fun putBoolean(
                key: String,
                value: Boolean
            ) {
                defaults.setBool(value, key)
                changed += key
            }

            override fun putInt(
                key: String,
                value: Int
            ) {
                defaults.setInteger(value.toLong(), key)
                changed += key
            }

            override fun putLong(
                key: String,
                value: Long
            ) {
                defaults.setInteger(value, key)
                changed += key
            }

            override fun putFloat(
                key: String,
                value: Float
            ) {
                defaults.setFloat(value, key)
                changed += key
            }

            override fun putString(
                key: String,
                value: String?
            ) {
                if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, key)
                changed += key
            }

            override fun remove(key: String) {
                defaults.removeObjectForKey(key)
                changed += key
            }
        }.block()
        changed.forEach { key -> writes.tryEmit(key) }
    }

    override fun changes(key: String): Flow<Unit> = writes
        .onSubscription { emit(key) }
        .filter { changedKey -> changedKey == key }
        .map { }
}
