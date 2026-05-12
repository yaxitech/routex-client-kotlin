package tech.yaxi.routex.models

import kotlin.io.encoding.Base64
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * URL-safe base64 codec for JWT-style segments: encodes without trailing `=`
 * and decodes with-or-without padding.
 */
public val Base64UrlNoPad: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

/** @suppress */
public object Base64ByteArraySerializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("tech.yaxi.routex.models.Base64ByteArray", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: ByteArray,
    ) {
        encoder.encodeString(Base64.Default.encode(value))
    }

    override fun deserialize(decoder: Decoder): ByteArray = Base64.Default.decode(decoder.decodeString())
}
