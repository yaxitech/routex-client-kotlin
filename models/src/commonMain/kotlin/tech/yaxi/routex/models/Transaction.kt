package tech.yaxi.routex.models

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/** Status of a single [Transaction]. */
@Serializable
public enum class TransactionStatus {
    /** Expected / planned but not yet booked. */
    @SerialName("Pending")
    PENDING,

    /** Booked to the account; typically the final state for most accounts. */
    @SerialName("Booked")
    BOOKED,

    /** Card transaction booked and invoiced but not yet paid. */
    @SerialName("Invoiced")
    INVOICED,

    /** Card transaction paid; typically the final state for card accounts. */
    @SerialName("Paid")
    PAID,

    /** Transaction has been canceled. */
    @SerialName("Canceled")
    CANCELED,
}

/**
 * Fee attached to a transaction, transfer detail, or batch entry.
 *
 * @property amount Decimal fee amount.
 * @property kind ISO 20022 `ExternalChargeType1Code`, when known.
 * @property bic ISO 20022 `BICFIIdentifier` of the agent the charges are due to.
 */
@ConsistentCopyVisibility
@Serializable
public data class Fee internal constructor(
    public val amount: Amount,
    public val kind: String? = null,
    public val bic: String? = null,
)

/** Counterparty (creditor or debtor) referenced by a [Transaction]. */
@ConsistentCopyVisibility
@Serializable
public data class Party internal constructor(
    public val name: String? = null,
    public val iban: String? = null,
    public val bic: String? = null,
    public val ultimate: String? = null,
)

/** Currency conversion attached to a [Transaction]. */
@ConsistentCopyVisibility
@Serializable
public data class ExchangeRate internal constructor(
    public val sourceCurrency: String,
    public val targetCurrency: String? = null,
    public val unitCurrency: String? = null,
    @Serializable(with = DecimalSerializer::class)
    public val exchangeRate: BigDecimal,
)

/** Bank transaction code attached to a [Transaction]. */
@Serializable(with = BankTransactionCodeSerializer::class)
public sealed class BankTransactionCode {
    /** ISO 20022 Bank Transaction Code. */
    @ConsistentCopyVisibility
    @Serializable
    public data class Iso internal constructor(
        public val domain: String,
        public val family: String,
        public val subFamily: String,
    ) : BankTransactionCode()

    /** SWIFT transaction code. */
    @ConsistentCopyVisibility
    public data class Swift internal constructor(
        public val value: String,
    ) : BankTransactionCode()

    /** BAI2 transaction code. */
    @ConsistentCopyVisibility
    public data class Bai internal constructor(
        public val value: String,
    ) : BankTransactionCode()

    /** National transaction code, e.g. German GVC. */
    @ConsistentCopyVisibility
    @Serializable
    public data class National internal constructor(
        public val code: String,
        public val country: CountryCode,
    ) : BankTransactionCode()

    /** Unspecified transaction code, possibly with an issuer. */
    @ConsistentCopyVisibility
    @Serializable
    public data class Other internal constructor(
        public val code: String,
        public val issuer: String? = null,
    ) : BankTransactionCode()
}

/** @suppress */
public object BankTransactionCodeSerializer :
    ExternallyTaggedSerializer<BankTransactionCode>("BankTransactionCode") {
    override fun variantName(value: BankTransactionCode): String =
        when (value) {
            is BankTransactionCode.Iso -> "iso"
            is BankTransactionCode.Swift -> "swift"
            is BankTransactionCode.Bai -> "bai"
            is BankTransactionCode.National -> "national"
            is BankTransactionCode.Other -> "other"
        }

    override fun childSerializer(variant: String): KSerializer<out BankTransactionCode> =
        throw SerializationException("BankTransactionCode decoded via direct JSON; childSerializer not used")

    override fun encodePayload(value: BankTransactionCode): JsonElement =
        when (value) {
            is BankTransactionCode.Iso ->
                RoutexJson.encodeToJsonElement(BankTransactionCode.Iso.serializer(), value)
            is BankTransactionCode.Swift -> JsonPrimitive(value.value)
            is BankTransactionCode.Bai -> JsonPrimitive(value.value)
            is BankTransactionCode.National ->
                RoutexJson.encodeToJsonElement(BankTransactionCode.National.serializer(), value)
            is BankTransactionCode.Other ->
                RoutexJson.encodeToJsonElement(BankTransactionCode.Other.serializer(), value)
        }

    override fun deserialize(decoder: Decoder): BankTransactionCode {
        val jsonDecoder =
            decoder as? JsonDecoder
                ?: throw SerializationException("BankTransactionCode only supports JSON")
        val obj =
            jsonDecoder.decodeJsonElement() as? JsonObject
                ?: throw SerializationException("Expected JSON object for BankTransactionCode")
        if (obj.size != 1) {
            throw SerializationException("Expected one-key object for BankTransactionCode, got ${obj.keys}")
        }
        val (variant, payload) = obj.entries.first()
        val json = jsonDecoder.json
        return when (variant) {
            "iso" -> json.decodeFromJsonElement(BankTransactionCode.Iso.serializer(), payload)
            "swift" -> BankTransactionCode.Swift(payload.jsonPrimitive.content)
            "bai" -> BankTransactionCode.Bai(payload.jsonPrimitive.content)
            "national" -> json.decodeFromJsonElement(BankTransactionCode.National.serializer(), payload)
            "other" -> json.decodeFromJsonElement(BankTransactionCode.Other.serializer(), payload)
            else -> throw SerializationException("Unknown BankTransactionCode variant: $variant")
        }
    }
}

/**
 * Per-transaction batch metadata: count of grouped transactions plus their individual
 * details where the provider supplied them.
 */
@ConsistentCopyVisibility
@Serializable
public data class BatchData internal constructor(
    public val numberOfTransactions: UInt? = null,
    public val transactions: List<BatchTransactionDetails>,
)

/**
 * Per-entry details for a transaction inside a batch. Same fields as [Transaction]
 * minus the booking dates and status (those are carried by the parent transaction).
 */
@ConsistentCopyVisibility
@Serializable
public data class BatchTransactionDetails internal constructor(
    public val accountServicerReference: String? = null,
    public val paymentId: String? = null,
    public val transactionId: String? = null,
    public val endToEndId: String? = null,
    public val mandateId: String? = null,
    public val creditorId: String? = null,
    public val amount: Amount? = null,
    public val reversal: Boolean = false,
    public val originalAmount: Amount? = null,
    public val exchanges: List<ExchangeRate> = emptyList(),
    public val fees: List<Fee> = emptyList(),
    public val creditor: Party? = null,
    public val debtor: Party? = null,
    public val remittanceInformation: List<String> = emptyList(),
    public val purposeCode: String? = null,
    public val bankTransactionCodes: List<BankTransactionCode> = emptyList(),
    public val additionalInformation: String? = null,
)

/**
 * Single transaction returned by the transactions service. Most fields are optional;
 * provider coverage varies and a typical [Transaction] populates a small subset. The
 * required fields are [status] and [amount].
 *
 * @property entryReference Identifier used for delta requests.
 * @property batch Sub-transaction details when the entry is a batch.
 * @property bookingDate Booking date in the ASPSP's books.
 * @property valueDate Value date (expected / requested for pending entries).
 * @property transactionDate Date of the actual transaction (e.g. card payment).
 * @property status Transaction status.
 * @property accountServicerReference Unique reference assigned by the account servicer.
 * @property paymentId Unique identifier assigned by the sending party.
 * @property transactionId Unique identifier assigned by the first instructing agent.
 * @property endToEndId Unique end-to-end identifier assigned by the initiating party.
 * @property mandateId Mandate identifier.
 * @property creditorId SEPA creditor identifier.
 * @property amount Transaction amount as billed to the account.
 * @property reversal Indicator for reversals.
 * @property originalAmount Original amount of the transaction.
 * @property exchanges Currency conversions.
 * @property fees Fees related to the transaction.
 * @property creditor Creditor data; refers to the initial transaction for reversals.
 * @property debtor Debtor data; refers to the initial transaction for reversals.
 * @property remittanceInformation Remittance / purpose strings.
 * @property purposeCode ISO 20022 ExternalPurpose1Code.
 * @property bankTransactionCodes ISO 20022 / SWIFT / BAI / national transaction codes.
 * @property additionalInformation Provider-supplied free-form annotation.
 */
@Suppress("LongParameterList")
@ConsistentCopyVisibility
@Serializable
public data class Transaction internal constructor(
    public val entryReference: String? = null,
    public val batch: BatchData? = null,
    public val bookingDate: LocalDate? = null,
    public val valueDate: LocalDate? = null,
    public val transactionDate: LocalDate? = null,
    public val status: TransactionStatus,
    public val accountServicerReference: String? = null,
    public val paymentId: String? = null,
    public val transactionId: String? = null,
    public val endToEndId: String? = null,
    public val mandateId: String? = null,
    public val creditorId: String? = null,
    public val amount: Amount,
    public val reversal: Boolean = false,
    public val originalAmount: Amount? = null,
    public val exchanges: List<ExchangeRate> = emptyList(),
    public val fees: List<Fee> = emptyList(),
    public val creditor: Party? = null,
    public val debtor: Party? = null,
    public val remittanceInformation: List<String> = emptyList(),
    public val purposeCode: String? = null,
    public val bankTransactionCodes: List<BankTransactionCode> = emptyList(),
    public val additionalInformation: String? = null,
)
