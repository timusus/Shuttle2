package com.simplecityapps.mediaprovider

import android.content.SharedPreferences

class FakeSharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

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

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}

    private inner class Editor : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = apply { values[key] = value }

        override fun putStringSet(key: String, values: Set<String>?) = apply { this@FakeSharedPreferences.values[key] = values }

        override fun putInt(key: String, value: Int) = apply { values[key] = value }

        override fun putLong(key: String, value: Long) = apply { values[key] = value }

        override fun putFloat(key: String, value: Float) = apply { values[key] = value }

        override fun putBoolean(key: String, value: Boolean) = apply { values[key] = value }

        override fun remove(key: String) = apply { values.remove(key) }

        override fun clear() = apply { values.clear() }

        override fun commit(): Boolean = true

        override fun apply() {}
    }
}
