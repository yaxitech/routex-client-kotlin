package tech.yaxi.routex.models

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.number
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Rule to follow if the execution of a [StandingOrder] falls on a weekend or bank holiday. */
@Serializable
public enum class ExecutionRule {
    /** Following working day. */
    @SerialName("Following")
    FOLLOWING,

    /** Following working day unless it would fall into the next month. */
    @SerialName("FollowingWithinAMonth")
    FOLLOWING_WITHIN_A_MONTH,

    /** Preceding working day. */
    @SerialName("Preceding")
    PRECEDING,

    /** Preceding working day unless it would fall into the previous month. */
    @SerialName("PrecedingWithinAMonth")
    PRECEDING_WITHIN_A_MONTH,
}

/** Execution frequency of a [StandingOrder]. */
@Serializable(with = FrequencySerializer::class)
public sealed class Frequency {
    /** Frequency in days. */
    @ConsistentCopyVisibility
    public data class Days internal constructor(
        public val value: Int,
    ) : Frequency()

    /** Frequency in months. */
    @ConsistentCopyVisibility
    public data class Months internal constructor(
        public val value: Int,
    ) : Frequency()

    /** Specific months that the standing order is scheduled for. */
    @ConsistentCopyVisibility
    public data class VariableMonths internal constructor(
        public val months: List<Month>,
    ) : Frequency()
}

/** @suppress */
public object FrequencySerializer :
    ExternallyTaggedSerializer<Frequency>("Frequency") {
    override fun variantName(value: Frequency): String =
        when (value) {
            is Frequency.Days -> "days"
            is Frequency.Months -> "months"
            is Frequency.VariableMonths -> "variableMonths"
        }

    override fun childSerializer(variant: String): KSerializer<out Frequency> =
        throw SerializationException("Frequency decoded via direct JSON; childSerializer not used")

    override fun encodePayload(value: Frequency): JsonElement =
        when (value) {
            is Frequency.Days -> JsonPrimitive(value.value)
            is Frequency.Months -> JsonPrimitive(value.value)
            is Frequency.VariableMonths -> JsonArray(value.months.map { JsonPrimitive(it.number) })
        }

    override fun deserialize(decoder: Decoder): Frequency {
        val jsonDecoder =
            decoder as? JsonDecoder
                ?: throw SerializationException("Frequency only supports JSON")
        val obj =
            jsonDecoder.decodeJsonElement() as? JsonObject
                ?: throw SerializationException("Expected JSON object for Frequency")
        if (obj.size != 1) {
            throw SerializationException("Expected one-key object for Frequency, got ${obj.keys}")
        }
        val (variant, payload) = obj.entries.first()
        val json = jsonDecoder.json
        return when (variant) {
            "days" -> Frequency.Days(json.decodeFromJsonElement(Int.serializer(), payload))
            "months" -> Frequency.Months(json.decodeFromJsonElement(Int.serializer(), payload))
            "variableMonths" ->
                Frequency.VariableMonths(
                    json.decodeFromJsonElement(ListSerializer(Int.serializer()), payload).map(::month),
                )
            else -> throw SerializationException("Unknown Frequency variant: $variant")
        }
    }

    private fun month(number: Int): Month {
        if (number !in 1..12) {
            throw SerializationException("Month must be between 1 and 12, got $number")
        }
        return Month(number)
    }
}

/**
 * Single standing order returned by the standing-orders service. The required fields
 * are [startDate] and [amount].
 *
 * @property startDate Date of the first execution.
 * @property endDate Date of the last execution.
 * @property executionRule Rule to follow if the execution falls on a weekend or bank
 *   holiday.
 * @property frequency Execution frequency.
 * @property day Day of month on which the order shall get executed.
 * @property skimmingLimit Amount limit for fund skimming, i.e. funds exceeding this
 *   value get transferred instead of a fixed amount.
 * @property endToEndId Unique end-to-end identifier assigned by the initiating party.
 * @property amount Amount of each execution.
 * @property creditor Creditor data.
 * @property debtor Debtor data.
 * @property remittanceInformation Remittance (purpose).
 * @property purposeCode ISO 20022 ExternalPurpose1Code.
 * @property additionalInformation Additional information attached to the standing
 *   order.
 */
@Suppress("LongParameterList")
@ConsistentCopyVisibility
@Serializable
public data class StandingOrder internal constructor(
    public val startDate: LocalDate,
    public val endDate: LocalDate? = null,
    public val executionRule: ExecutionRule? = null,
    public val frequency: Frequency? = null,
    public val day: Int? = null,
    public val skimmingLimit: Amount? = null,
    public val endToEndId: String? = null,
    public val amount: Amount,
    public val creditor: Party? = null,
    public val debtor: Party? = null,
    public val remittanceInformation: List<String> = emptyList(),
    public val purposeCode: String? = null,
    public val additionalInformation: String? = null,
)
