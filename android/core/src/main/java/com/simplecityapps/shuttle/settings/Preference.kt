package com.simplecityapps.shuttle.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * One [Setting] bound to the SharedPreferences it lives in: read it with [value], write it by assigning
 * [value], observe it with [flow] or [stateIn].
 */
class Preference<T>(
    private val sharedPreferences: SharedPreferences,
    val setting: Setting<T>
) {
    val key: String get() = setting.key

    val default: T get() = setting.default

    var value: T
        get() = setting.read(sharedPreferences)
        set(value) {
            sharedPreferences.edit { setting.write(this, value) }
        }

    /** Forgets the stored value, so the setting reads as its [default] again. */
    fun reset() {
        sharedPreferences.edit { remove(setting.key) }
    }

    /** Whether a value has been explicitly stored — false means [value] is reading as [default]. */
    fun isSet(): Boolean = sharedPreferences.contains(setting.key)

    /** The current value, then every change to it. */
    val flow: Flow<T> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            // A null key means the whole file was cleared (API 30+)
            if (changedKey == null || changedKey == setting.key) {
                trySend(value)
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(value)
        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
        .conflate()
        .distinctUntilChanged()

    fun stateIn(
        scope: CoroutineScope,
        started: SharingStarted = SharingStarted.WhileSubscribed(5_000)
    ): StateFlow<T> = flow.stateIn(scope, started, value)
}

fun <T> SharedPreferences.preference(setting: Setting<T>): Preference<T> = Preference(this, setting)
