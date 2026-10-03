package com.simplecityapps.shuttle.settings

import com.simplecityapps.shuttle.persistence.KeyValueStore

/**
 * A typed setting: its [KeyValueStore] key, its default, and the format it is stored in.
 *
 * Keys and formats are the ones the app has always written (the androidx.preference screens wrote enum
 * choices as strings, for example), so saved values survive the move to Compose settings. A stored value
 * that can't be read back as [T] reads as [default].
 */
class Setting<T> private constructor(
    val key: String,
    val default: T,
    /** The primitive type [key] holds in the store, whatever [T] decodes it into. */
    val storage: Storage,
    private val reader: KeyValueStore.(key: String, default: T) -> T,
    private val writer: KeyValueStore.Editor.(key: String, value: T) -> Unit
) {
    fun read(store: KeyValueStore): T {
        if (!store.contains(key)) return default
        // A value stored under this key with another type throws ClassCastException
        return runCatching { store.reader(key, default) }.getOrDefault(default)
    }

    fun write(
        editor: KeyValueStore.Editor,
        value: T
    ) {
        editor.writer(key, value)
    }

    enum class Storage { Boolean, Int, Float, String }

    override fun toString(): String = "Setting($key, default=$default)"

    companion object {
        fun boolean(
            key: String,
            default: Boolean
        ): Setting<Boolean> = Setting(key, default, Storage.Boolean, { k, d -> getBoolean(k, d) }, { k, v -> putBoolean(k, v) })

        fun int(
            key: String,
            default: Int
        ): Setting<Int> = Setting(key, default, Storage.Int, { k, d -> getInt(k, d) }, { k, v -> putInt(k, v) })

        fun float(
            key: String,
            default: Float
        ): Setting<Float> = Setting(key, default, Storage.Float, { k, d -> getFloat(k, d) }, { k, v -> putFloat(k, v) })

        /** A value stored as a string, decoded with [decode]; a string [decode] rejects (returns null for) reads as [default]. */
        fun <T> string(
            key: String,
            default: T,
            decode: (String) -> T?,
            encode: (T) -> String
        ): Setting<T> = Setting(
            key = key,
            default = default,
            storage = Storage.String,
            reader = { k, d -> getString(k, null)?.let(decode) ?: d },
            writer = { k, v -> putString(k, encode(v)) }
        )

        /** An enum stored as its ordinal in a string, the format the ListPreference-era screens wrote. */
        fun <E : Enum<E>> enumOrdinalString(
            key: String,
            default: E,
            entries: List<E>
        ): Setting<E> = string(
            key = key,
            default = default,
            decode = { value -> value.toIntOrNull()?.let(entries::getOrNull) },
            encode = { value -> value.ordinal.toString() }
        )

        /** An enum stored as its ordinal in an int. */
        fun <E : Enum<E>> enumOrdinalInt(
            key: String,
            default: E,
            entries: List<E>
        ): Setting<E> = Setting(
            key = key,
            default = default,
            storage = Storage.Int,
            reader = { k, d -> entries.getOrNull(getInt(k, d.ordinal)) ?: d },
            writer = { k, v -> putInt(k, v.ordinal) }
        )
    }
}
