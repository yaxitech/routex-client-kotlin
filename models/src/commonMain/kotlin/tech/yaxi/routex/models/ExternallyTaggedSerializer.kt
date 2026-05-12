package tech.yaxi.routex.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject

/**
 * Base class for sealed-class serializers that emit a single-key JSON object
 * `{"VariantName": <payload>}`.
 *
 * @suppress
 */
public abstract class ExternallyTaggedSerializer<T : Any>(
    serialName: String,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(serialName)

    protected abstract fun variantName(value: T): String

    protected abstract fun childSerializer(variant: String): KSerializer<out T>

    protected abstract fun encodePayload(value: T): JsonElement

    @Suppress("UNCHECKED_CAST")
    override fun serialize(
        encoder: Encoder,
        value: T,
    ) {
        val jsonEncoder =
            encoder as? JsonEncoder
                ?: throw SerializationException("ExternallyTaggedSerializer only supports JSON")
        val payload = encodePayload(value)
        jsonEncoder.encodeJsonElement(JsonObject(mapOf(variantName(value) to payload)))
    }

    override fun deserialize(decoder: Decoder): T {
        val jsonDecoder =
            decoder as? JsonDecoder
                ?: throw SerializationException("ExternallyTaggedSerializer only supports JSON")
        val element =
            jsonDecoder.decodeJsonElement() as? JsonObject
                ?: throw SerializationException("Expected JSON object for externally tagged enum")
        if (element.size != 1) {
            throw SerializationException(
                "Expected exactly one key for externally tagged enum, got ${element.keys}",
            )
        }
        val (variant, payload) = element.entries.first()
        val child = childSerializer(variant)
        @Suppress("UNCHECKED_CAST")
        return jsonDecoder.json.decodeFromJsonElement(child as KSerializer<T>, payload)
    }
}
