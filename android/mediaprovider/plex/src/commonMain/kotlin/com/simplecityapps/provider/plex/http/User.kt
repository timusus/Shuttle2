package com.simplecityapps.provider.plex.http

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive

/** A plex.tv account. */
@Serializable
data class User(
    // plex.tv sends the id as a number
    @Serializable(with = NumberAsStringSerializer::class) val id: String,
    val authToken: String
)

/** A string, or a number read as its text. */
internal object NumberAsStringSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("NumberAsString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String = (decoder as? JsonDecoder)?.decodeJsonElement()?.jsonPrimitive?.content ?: decoder.decodeString()

    override fun serialize(
        encoder: Encoder,
        value: String
    ) = encoder.encodeString(value)
}
