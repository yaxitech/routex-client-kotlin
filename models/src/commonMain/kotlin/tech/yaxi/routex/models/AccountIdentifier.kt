package tech.yaxi.routex.models

import kotlin.io.encoding.Base64
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
 * Identifier of an account, used when referencing creditor or debtor accounts in
 * payment-related requests.
 */
@Serializable(with = AccountIdentifierSerializer::class)
public sealed class AccountIdentifier {
    /** ISO 20022 IBAN2007Identifier. */
    public data class Iban(
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
        }

    internal fun decodeFromJsonObject(obj: JsonObject): AccountIdentifier {
        val iban = obj["iban"]
        if (iban is JsonPrimitive && iban.isString) {
            return AccountIdentifier.Iban(iban.content)
        }
        throw SerializationException(
            "Unknown or malformed AccountIdentifier variant in object: ${obj.keys}",
        )
    }

    internal val identifierKeys: Set<String> = setOf("iban")
}

/**
 * Reference to an account by IBAN, with an optional ISO 4217 currency code for
 * multi-currency accounts.
 *
 * Implements [DebtorAccountReference] so the same value can be passed to the
 * collect-payment service without conversion; encrypted-IBAN debtors are
 * represented by [EncryptedIbanReference].
 */
@Serializable(with = AccountReferenceSerializer::class)
public data class AccountReference(
    public val iban: String,
    public override val currency: String? = null,
) : DebtorAccountReference

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
                put("iban", JsonPrimitive(value.iban))
                value.currency?.let { put("currency", JsonPrimitive(it)) }
            },
        )

    internal fun decodeFromJsonObject(obj: JsonObject): AccountReference {
        val ibanElement = obj["iban"]
        val iban =
            (ibanElement as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: throw SerializationException("AccountReference requires a string `iban` field")
        val currency =
            (obj["currency"] as? JsonPrimitive)?.let {
                if (it.isString) it.content else null
            }
        val unknown = obj.keys - setOf("iban", "currency")
        if (unknown.isNotEmpty()) {
            throw SerializationException("Unknown keys in AccountReference: $unknown")
        }
        return AccountReference(iban, currency)
    }
}

/**
 * Reference to a debtor account for the collect-payment service.
 *
 * The collect-payment service accepts either a plain [AccountReference] or an
 * [EncryptedIbanReference] ciphertext your backend produced with its API secret
 * following the [Opaque Data](https://docs.yaxi.tech/opaque-data.html) protocol so the
 * frontend never sees the underlying IBAN.
 *
 * Pass an [AccountReference] returned by the accounts service straight through:
 *
 * ```
 * val account: AccountReference = result.data.first().toAccountReference()
 * client.collectPayment(ticket, credentials, account = account)
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
                is AccountReference -> AccountReferenceSerializer.encodeToJsonObject(value)
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
        return AccountReferenceSerializer.decodeFromJsonObject(obj)
    }
}
