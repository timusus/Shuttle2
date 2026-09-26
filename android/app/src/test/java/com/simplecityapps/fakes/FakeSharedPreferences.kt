package com.simplecityapps.fakes

import android.content.SharedPreferences

/**
 * In-memory [SharedPreferences], notifying listeners on [Editor.apply]/[Editor.commit] the way the real
 * (and Robolectric) implementations do — [com.simplecityapps.shuttle.settings.Preference.flow] depends on
 * that to observe changes made after its first read.
 */
class FakeSharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): Map<String, *> = values

    override fun getString(key: String, defValue: String?): String? = values[key] as String? ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = values[key] as Set<String>? ?: defValues

    override fun getInt(key: String, defValue: Int): Int = values[key] as Int? ?: defValue

    override fun getLong(key: String, defValue: Long): Long = values[key] as Long? ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = values[key] as Float? ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as Boolean? ?: defValue

    override fun contains(key: String): Boolean = key in values

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners += listener
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners -= listener
    }

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val toRemove = mutableSetOf<String>()
        private var cleared = false

        override fun putString(key: String, value: String?) = apply { pending[key] = value }

        override fun putStringSet(key: String, values: Set<String>?) = apply { pending[key] = values }

        override fun putInt(key: String, value: Int) = apply { pending[key] = value }

        override fun putLong(key: String, value: Long) = apply { pending[key] = value }

        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }

        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }

        override fun remove(key: String) = apply { toRemove += key }

        override fun clear() = apply { cleared = true }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (cleared) values.clear()
            toRemove.forEach { values.remove(it) }
            values.putAll(pending)

            val changedKeys = if (cleared) setOf<String?>(null) else (toRemove + pending.keys)
            changedKeys.forEach { key -> listeners.forEach { it.onSharedPreferenceChanged(this@FakeSharedPreferences, key) } }
        }
    }
}
