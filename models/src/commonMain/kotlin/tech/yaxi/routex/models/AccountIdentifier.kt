package tech.yaxi.routex.models

import kotlin.io.encoding.Base64
import kotlin.jvm.JvmStatic
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Identifier of a creditor account in payment services.
 */
@Serializable(with = CreditorAccountIdentifierSerializer::class)
public sealed class CreditorAccountIdentifier {
    /** ISO 20022 IBAN2007Identifier. */
    public data class Iban(
        public val value: String,
    ) : CreditorAccountIdentifier()
}

/** @suppress */
public object CreditorAccountIdentifierSerializer : KSerializer<CreditorAccountIdentifier> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("tech.yaxi.routex.models.CreditorAccountIdentifier")

    override fun serialize(
        encoder: Encoder,
        value: CreditorAccountIdentifier,
    ) {
        val jsonEncoder =
            encoder as? JsonEncoder
                ?: throw SerializationException("CreditorAccountIdentifier only supports JSON")
        jsonEncoder.encodeJsonElement(encodeToJsonObject(value))
    }

    override fun deserialize(decoder: Decoder): CreditorAccountIdentifier {
        val jsonDecoder =
            decoder as? JsonDecoder
                ?: throw SerializationException("CreditorAccountIdentifier only supports JSON")
        val obj =
            jsonDecoder.decodeJsonElement() as? JsonObject
                ?: throw SerializationException("Expected JSON object for CreditorAccountIdentifier")
        return decodeFromJsonObject(obj)
    }

    internal fun encodeToJsonObject(value: CreditorAccountIdentifier): JsonObject =
        when (value) {
            is CreditorAccountIdentifier.Iban ->
                JsonObject(mapOf("iban" to JsonPrimitive(value.value)))
        }

    internal fun decodeFromJsonObject(obj: JsonObject): CreditorAccountIdentifier {
        val iban = obj["iban"]
        if (iban is JsonPrimitive && iban.isString) {
            return CreditorAccountIdentifier.Iban(iban.content)
        }
        throw SerializationException(
            "Unknown or malformed CreditorAccountIdentifier variant in object: ${obj.keys}",
        )
    }
}

/**
 * Identifier of an account referenced by the account-information services.
 */
@Serializable(with = AccountIdentifierSerializer::class)
public sealed class AccountIdentifier {
    /** ISO 20022 IBAN2007Identifier. */
    public data class Iban(
        public val value: String,
    ) : AccountIdentifier()

    /** Account number that is not an IBAN, e.g. ISO 20022 BBANIdentifier. */
    public data class Number(
        public val value: String,
    ) : AccountIdentifier()

    /** Masked payment card number. */
    public data class MaskedPan(
        public val value: String,
    ) : AccountIdentifier()
}

/** @suppress */
public object AccountIdentifierSerializer : KSerializer<AccountIdentifier> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("tech.yaxi.routex.models.AccountIdentifier")

    override fun serialize(
        encoder: Encoder,
        value: AccountIdentifier,
    ) {
        val jsonEncoder =
            encoder as? JsonEncoder
                ?: throw SerializationException("AccountIdentifier only supports JSON")
        jsonEncoder.encodeJsonElement(encodeToJsonObject(value))
    }

    override fun deserialize(decoder: Decoder): AccountIdentifier {
        val jsonDecoder =
            decoder as? JsonDecoder
                ?: throw SerializationException("AccountIdentifier only supports JSON")
        val obj =
            jsonDecoder.decodeJsonElement() as? JsonObject
                ?: throw SerializationException("Expected JSON object for AccountIdentifier")
        return decodeFromJsonObject(obj)
    }

    internal fun encodeToJsonObject(value: AccountIdentifier): JsonObject =
        when (value) {
            is AccountIdentifier.Iban ->
                JsonObject(mapOf("iban" to JsonPrimitive(value.value)))
            is AccountIdentifier.Number ->
                JsonObject(mapOf("number" to JsonPrimitive(value.value)))
            is AccountIdentifier.MaskedPan ->
                JsonObject(mapOf("maskedPan" to JsonPrimitive(value.value)))
        }

    internal fun decodeFromJsonObject(obj: JsonObject): AccountIdentifier {
        (obj["iban"] as? JsonPrimitive)?.takeIf { it.isString }?.let {
            return AccountIdentifier.Iban(it.content)
        }
        (obj["number"] as? JsonPrimitive)?.takeIf { it.isString }?.let {
            return AccountIdentifier.Number(it.content)
        }
        (obj["maskedPan"] as? JsonPrimitive)?.takeIf { it.isString }?.let {
            return AccountIdentifier.MaskedPan(it.content)
        }
        throw SerializationException(
            "Unknown or malformed AccountIdentifier variant in object: ${obj.keys}",
        )
    }
}

/**
 * Reference to an account by IBAN, account number or masked payment card number,
 * with an optional ISO 4217 currency code for multi-currency accounts.
 *
 * Construct with [iban], [number] or [maskedPan]; the chosen identifier is held in [id].
 */
@Serializable(with = AccountReferenceSerializer::class)
public data class AccountReference(
    public val id: AccountIdentifier,
    public val currency: String? = null,
) {
    public companion object {
        /** Reference an account by its ISO 20022 IBAN2007Identifier. */
        @JvmStatic
        public fun iban(
            iban: String,
            currency: String? = null,
        ): AccountReference = AccountReference(AccountIdentifier.Iban(iban), currency)

        /** Reference an account by a non-IBAN account number, e.g. an ISO 20022 BBANIdentifier. */
        @JvmStatic
        public fun number(
            number: String,
            currency: String? = null,
        ): AccountReference = AccountReference(AccountIdentifier.Number(number), currency)

        /** Reference a card account by its masked payment card number. */
        @JvmStatic
        public fun maskedPan(
            maskedPan: String,
            currency: String? = null,
        ): AccountReference = AccountReference(AccountIdentifier.MaskedPan(maskedPan), currency)
    }
}

/** @suppress */
public object AccountReferenceSerializer : KSerializer<AccountReference> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("tech.yaxi.routex.models.AccountReference")

    override fun serialize(
        encoder: Encoder,
        value: AccountReference,
    ) {
        val jsonEncoder =
            encoder as? JsonEncoder
                ?: throw SerializationException("AccountReference only supports JSON")
        jsonEncoder.encodeJsonElement(encodeToJsonObject(value))
    }

    override fun deserialize(decoder: Decoder): AccountReference {
        val jsonDecoder =
            decoder as? JsonDecoder
                ?: throw SerializationException("AccountReference only supports JSON")
        val obj =
            jsonDecoder.decodeJsonElement() as? JsonObject
                ?: throw SerializationException("Expected JSON object for AccountReference")
        return decodeFromJsonObject(obj)
    }

    internal fun encodeToJsonObject(value: AccountReference): JsonObject =
        JsonObject(
            buildMap<String, JsonElement> {
                putAll(AccountIdentifierSerializer.encodeToJsonObject(value.id))
                value.currency?.let { put("currency", JsonPrimitive(it)) }
            },
        )

    internal fun decodeFromJsonObject(obj: JsonObject): AccountReference {
        val id = AccountIdentifierSerializer.decodeFromJsonObject(obj)
        val currency =
            (obj["currency"] as? JsonPrimitive)?.let {
                if (it.isString) it.content else null
            }
        val unknown = obj.keys - setOf("iban", "number", "maskedPan", "currency")
        if (unknown.isNotEmpty()) {
            throw SerializationException("Unknown keys in AccountReference: $unknown")
        }
        return AccountReference(id, currency)
    }
}

/**
 * Reference to a debtor account for the collect-payment and transfer services.
 *
 * Pass an [IbanReference] for a plain IBAN, or — for the collect-payment service — an
 * [EncryptedIbanReference] ciphertext your backend produced with its API secret following
 * the [Opaque Data](https://docs.yaxi.tech/opaque-data.html) protocol so the frontend
 * never sees the underlying IBAN.
 *
 * ```
 * client.collectPayment(ticket, credentials, account = IbanReference("NL58YAXI1234567890"))
 * ```
 *
 * For an encrypted handoff use [EncryptedIbanReference]:
 *
 * ```
 * client.collectPayment(
 *     ticket,
 *     credentials,
 *     account = EncryptedIbanReference(base64FromBackend),
 * )
 * ```
 */
@Serializable(with = DebtorAccountReferenceSerializer::class)
public sealed interface DebtorAccountReference {
    /** ISO 4217 Alpha 3 currency code for multi-currency accounts. */
    public val currency: String?
}

/**
 * Reference to a debtor account by IBAN, with an optional ISO 4217 currency code, for the
 * collect-payment and transfer services.
 */
public data class IbanReference(
    public val iban: String,
    public override val currency: String? = null,
) : DebtorAccountReference

/**
 * Reference to a debtor account by encrypted-IBAN ciphertext, for the collect-payment
 * service. Opaque on the client; the ciphertext is produced by the caller's backend
 * with its API secret following the
 * [Opaque Data](https://docs.yaxi.tech/opaque-data.html) protocol so the frontend never
 * sees the underlying IBAN.
 */
public class EncryptedIbanReference(
    public val bytes: ByteArray,
    public override val currency: String? = null,
) : DebtorAccountReference {
    /**
     * Convenience constructor for the typical backend-to-frontend handoff: the
     * ciphertext is decoded from its Base64 transport form.
     *
     * @throws IllegalArgumentException if [base64] is not valid Base64.
     */
    public constructor(base64: String, currency: String? = null) : this(
        Base64.Default.decode(base64),
        currency,
    )

    override fun equals(other: Any?): Boolean =
        other is EncryptedIbanReference && bytes.contentEquals(other.bytes) && currency == other.currency

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + (currency?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "EncryptedIbanReference(bytes=<${bytes.size} bytes>, currency=$currency)"
}

/** @suppress */
public object DebtorAccountReferenceSerializer : KSerializer<DebtorAccountReference> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("tech.yaxi.routex.models.DebtorAccountReference")

    override fun serialize(
        encoder: Encoder,
        value: DebtorAccountReference,
    ) {
        val jsonEncoder =
            encoder as? JsonEncoder
                ?: throw SerializationException("DebtorAccountReference only supports JSON")
        val obj =
            when (value) {
                is IbanReference ->
                    JsonObject(
                        buildMap<String, JsonElement> {
                            put("iban", JsonPrimitive(value.iban))
                            value.currency?.let { put("currency", JsonPrimitive(it)) }
                        },
                    )
                is EncryptedIbanReference ->
                    JsonObject(
                        buildMap<String, JsonElement> {
                            put("encryptedIban", JsonPrimitive(Base64.Default.encode(value.bytes)))
                            value.currency?.let { put("currency", JsonPrimitive(it)) }
                        },
                    )
            }
        jsonEncoder.encodeJsonElement(obj)
    }

    override fun deserialize(decoder: Decoder): DebtorAccountReference {
        val jsonDecoder =
            decoder as? JsonDecoder
                ?: throw SerializationException("DebtorAccountReference only supports JSON")
        val obj =
            jsonDecoder.decodeJsonElement() as? JsonObject
                ?: throw SerializationException("Expected JSON object for DebtorAccountReference")
        val encrypted = obj["encryptedIban"]
        if (encrypted is JsonPrimitive && encrypted.isString) {
            val currency =
                (obj["currency"] as? JsonPrimitive)?.let {
                    if (it.isString) it.content else null
                }
            val unknown = obj.keys - setOf("encryptedIban", "currency")
            if (unknown.isNotEmpty()) {
                throw SerializationException("Unknown keys in DebtorAccountReference: $unknown")
            }
            return EncryptedIbanReference(Base64.Default.decode(encrypted.content), currency)
        }
        val iban =
            (obj["iban"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: throw SerializationException("DebtorAccountReference requires a string `iban` field")
        val currency =
            (obj["currency"] as? JsonPrimitive)?.let {
                if (it.isString) it.content else null
            }
        val unknown = obj.keys - setOf("iban", "currency")
        if (unknown.isNotEmpty()) {
            throw SerializationException("Unknown keys in DebtorAccountReference: $unknown")
        }
        return IbanReference(iban, currency)
    }
}
