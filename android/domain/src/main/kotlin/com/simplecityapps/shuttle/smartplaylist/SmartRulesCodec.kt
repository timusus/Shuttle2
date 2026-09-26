package com.simplecityapps.shuttle.smartplaylist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * [SmartRules] to and from the JSON a smart playlist is stored as. The JSON carries a version so a later change to its
 * shape can migrate what's stored; fields added later decode from older JSON by their defaults, and fields this
 * version doesn't know are ignored.
 */
object SmartRulesCodec {
    const val VERSION = 1

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun encode(rules: SmartRules): String = json.encodeToString(Stored.serializer(), Stored(VERSION, rules))

    /**
     * @throws SerializationException (an [IllegalArgumentException]) if [text] isn't smart playlist JSON, or holds a rule
     *   this version doesn't know.
     */
    fun decode(text: String): SmartRules = json.decodeFromString(Stored.serializer(), text).rules

    @Serializable
    private data class Stored(
        @SerialName("version") val version: Int,
        @SerialName("rules") val rules: SmartRules,
    )
}
