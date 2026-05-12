package tech.yaxi.routex.models

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Monetary amount paired with an ISO 4217 currency code.
 *
 * @property amount Exact decimal amount; serialized as a JSON string to preserve scale.
 * @property currency ISO 4217 Alpha 3 currency code.
 */
@Serializable
public data class Amount(
    @Serializable(with = DecimalSerializer::class)
    public val amount: BigDecimal,
    public val currency: String,
) {
    /**
     * Convenience constructor that parses [amount] as a decimal, preserving the
     * original number of fractional digits (e.g. `Amount("100.00", "EUR")` keeps the
     * two trailing zeros). Accepts whatever
     * [BigDecimal.parseString][com.ionspin.kotlin.bignum.decimal.BigDecimal.parseString]
     * accepts; non-canonical inputs are normalized on the wire. Callers needing
     * byte-exact control should construct a [BigDecimal] themselves and use the
     * primary constructor.
     *
     * @throws IllegalArgumentException if [amount] is not parseable as a decimal.
     */
    public constructor(amount: String, currency: String) : this(parseScaledDecimal(amount), currency)
}

/**
 * Serializes a [BigDecimal] as a plain JSON string (`"-12.34"`, `"1200.00"`; never
 * scientific notation), preserving the original number of fractional digits.
 */
public object DecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("tech.yaxi.routex.models.Decimal", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: BigDecimal,
    ) {
        encoder.encodeString(value.toPlainString())
    }

    override fun deserialize(decoder: Decoder): BigDecimal = parseScaledDecimal(decoder.decodeString())
}

/**
 * Parses a decimal string into a [BigDecimal], setting the scale to the number of
 * digits after the decimal point so the value round-trips through [DecimalSerializer]
 * with the same fractional-digit count.
 */
private fun parseScaledDecimal(text: String): BigDecimal {
    val parsed = BigDecimal.parseString(text)
    val dot = text.indexOf('.')
    return if (dot >= 0) parsed.scale((text.length - dot - 1).toLong()) else parsed
}
