package tech.yaxi.routex.settlement

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

internal sealed class SystemVersionDecodeError(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    class UnknownKeyId(
        val keyId: String,
    ) : SystemVersionDecodeError("unknown system-version signing key id: $keyId")

    class InvalidSignature(
        message: String = "system-version signature is invalid",
        cause: Throwable? = null,
    ) : SystemVersionDecodeError(message, cause)

    class MalformedInput(
        message: String,
        cause: Throwable? = null,
    ) : SystemVersionDecodeError(message, cause)
}

@Serializable(with = LaunchMeasurementSerializer::class)
public sealed class LaunchMeasurement {
    public class V1(
        public val bytes: ByteArray,
    ) : LaunchMeasurement() {
        override fun equals(other: Any?): Boolean = other is V1 && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int = bytes.contentHashCode()
    }
}

public object LaunchMeasurementSerializer : KSerializer<LaunchMeasurement> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("tech.yaxi.routex.settlement.LaunchMeasurement")

    @OptIn(ExperimentalEncodingApi::class)
    override fun serialize(
        encoder: Encoder,
        value: LaunchMeasurement,
    ) {
        val jsonEncoder =
            encoder as? JsonEncoder
                ?: throw SerializationException("LaunchMeasurement only supports JSON")
        when (value) {
            is LaunchMeasurement.V1 ->
                jsonEncoder.encodeJsonElement(JsonPrimitive(Base64.encode(value.bytes)))
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun deserialize(decoder: Decoder): LaunchMeasurement {
        val jsonDecoder =
            decoder as? JsonDecoder
                ?: throw SerializationException("LaunchMeasurement only supports JSON")
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonPrimitive && element.isString) {
            return LaunchMeasurement.V1(Base64.decode(element.jsonPrimitive.content))
        }
        throw SerializationException(
            "unsupported LaunchMeasurement wire shape: ${element::class.simpleName}",
        )
    }
}

@Serializable
public data class SystemVersionSignature(
    public val keyId: String,
    public val value: String,
)

@ConsistentCopyVisibility
@Serializable
public data class SystemVersionEntry internal constructor(
    public val kind: String,
    public val generation: Long,
    /** Raw wire string; kept verbatim for signature byte-stability. Use [createdAt] elsewhere. */
    @SerialName("createdAt")
    internal val rawCreatedAt: String,
    public val ref: String,
    public val launchMeasurement: LaunchMeasurement,
    public val signature: SystemVersionSignature,
) {
    /** `createdAt` parsed as a [kotlin.time.Instant]; throws on first access if malformed. */
    public val createdAt: Instant get() = Instant.parse(rawCreatedAt)

    /** Canonical JSON serialization of this entry. */
    public fun toJson(): String =
        tech.yaxi.routex.models.RoutexJson
            .encodeToString(serializer(), this)
}
