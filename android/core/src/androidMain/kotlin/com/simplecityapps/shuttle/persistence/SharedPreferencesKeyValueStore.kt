package com.simplecityapps.shuttle.persistence

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** A [KeyValueStore] over a SharedPreferences file, reading and writing it exactly as the app always has. */
class SharedPreferencesKeyValueStore(
    private val sharedPreferences: SharedPreferences
) : KeyValueStore {
    override fun contains(key: String): Boolean = sharedPreferences.contains(key)

    override fun getBoolean(
        key: String,
        default: Boolean
    ): Boolean = sharedPreferences.getBoolean(key, default)

    override fun getInt(
        key: String,
        default: Int
    ): Int = sharedPreferences.getInt(key, default)

    override fun getLong(
        key: String,
        default: Long
    ): Long = sharedPreferences.getLong(key, default)

    override fun getFloat(
        key: String,
        default: Float
    ): Float = sharedPreferences.getFloat(key, default)

    override fun getString(
        key: String,
        default: String?
    ): String? = sharedPreferences.getString(key, default)

    override fun edit(block: KeyValueStore.Editor.() -> Unit) {
        val editor = sharedPreferences.edit()
        EditorAdapter(editor).block()
        editor.apply()
    }

    override fun changes(key: String): Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            // A null key means the whole file was cleared (API 30+)
            if (changedKey == null || changedKey == key) {
                trySend(Unit)
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private class EditorAdapter(
        private val editor: SharedPreferences.Editor
    ) : KeyValueStore.Editor {
        override fun putBoolean(
            key: String,
            value: Boolean
        ) {
            editor.putBoolean(key, value)
        }

        override fun putInt(
            key: String,
            value: Int
        ) {
            editor.putInt(key, value)
        }

        override fun putLong(
            key: String,
            value: Long
        ) {
            editor.putLong(key, value)
        }

        override fun putFloat(
            key: String,
            value: Float
        ) {
            editor.putFloat(key, value)
        }

        override fun putString(
            key: String,
            value: String?
        ) {
            editor.putString(key, value)
        }

        override fun remove(key: String) {
            editor.remove(key)
        }
    }
}
