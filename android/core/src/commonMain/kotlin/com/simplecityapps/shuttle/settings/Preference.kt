package com.simplecityapps.shuttle.settings

import com.simplecityapps.shuttle.persistence.KeyValueStore
import com.simplecityapps.shuttle.persistence.remove
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * One [Setting] bound to the [KeyValueStore] it lives in: read it with [value], write it by assigning
 * [value], observe it with [flow] or [stateIn].
 */
class Preference<T>(
    private val store: KeyValueStore,
    val setting: Setting<T>
) {
    val key: String get() = setting.key

    val default: T get() = setting.default

    var value: T
        get() = setting.read(store)
        set(value) {
            store.edit { setting.write(this, value) }
        }

    /** Forgets the stored value, so the setting reads as its [default] again. */
    fun reset() {
        store.remove(setting.key)
    }

    /** Whether a value has been explicitly stored — false means [value] is reading as [default]. */
    fun isSet(): Boolean = store.contains(setting.key)

    /** The current value, then every change to it. */
    val flow: Flow<T> = store.changes(setting.key)
        .map { value }
        .conflate()
        .distinctUntilChanged()

    fun stateIn(
        scope: CoroutineScope,
        started: SharingStarted = SharingStarted.WhileSubscribed(5_000)
    ): StateFlow<T> = flow.stateIn(scope, started, value)
}
