package tech.yaxi.routex.tickets

import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import tech.yaxi.routex.models.AccountReference
import tech.yaxi.routex.models.Ticket

/**
 * Time-range selector for the transactions service ticket. Pass an instance as
 * the `range` argument to [RoutexTicketIssuer.transactions]. Either a delta
 * reference returned by an earlier call ([Reference]) or an absolute date range
 * ([Period]).
 */
@Serializable(with = RangeSerializer::class)
public sealed class Range {
    /**
     * Delta cursor that resumes the transaction stream after a prior call.
     *
     * The [reference] value is the
     * [entryReference][tech.yaxi.routex.models.Transaction.entryReference] of
     * a transaction returned by an earlier transactions call; the service
     * then yields all newer transactions.
     */
    public data class Reference(
        public val reference: String,
    ) : Range()

    /**
     * Absolute date range. [from] is inclusive; [to] is inclusive when present
     * and may be `null` to leave the upper bound open.
     */
    public data class Period(
        public val from: LocalDate,
        public val to: LocalDate? = null,
    ) : Range()
}

/**
 * Hand-rolled rather than
 * [ExternallyTaggedSerializer][tech.yaxi.routex.models.ExternallyTaggedSerializer]:
 * [Range.Period] is untagged (`{`[from][Range.Period.from]`,`[to][Range.Period.to]`}`),
 * so the variants don't share one wire shape.
 *
 * @suppress
 */
public object RangeSerializer : KSerializer<Range> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("tech.yaxi.routex.tickets.Range")

    override fun serialize(
        encoder: Encoder,
        value: Range,
    ) {
        val jsonEncoder =
            encoder as? JsonEncoder
                ?: throw SerializationException("Range only supports JSON")
        val obj =
            when (value) {
                is Range.Reference ->
                    buildJsonObject {
                        put("reference", JsonPrimitive(value.reference))
                    }
                is Range.Period ->
                    buildJsonObject {
                        put("from", JsonPrimitive(value.from.toString()))
                        put("to", value.to?.let { JsonPrimitive(it.toString()) } ?: JsonNull)
                    }
            }
        jsonEncoder.encodeJsonElement(obj)
    }

    override fun deserialize(decoder: Decoder): Range {
        val jsonDecoder =
            decoder as? JsonDecoder
                ?: throw SerializationException("Range only supports JSON")
        val obj =
            jsonDecoder.decodeJsonElement() as? JsonObject
                ?: throw SerializationException("Expected JSON object for Range")
        val reference = obj["reference"]
        if (reference is JsonPrimitive && reference.isString) {
            return Range.Reference(reference.content)
        }
        val fromElement =
            obj["from"] as? JsonPrimitive
                ?: throw SerializationException("Range requires either `reference` or `from`")
        val from =
            try {
                LocalDate.parse(fromElement.content)
            } catch (e: IllegalArgumentException) {
                throw SerializationException("Range.from is not a valid ISO date: '${fromElement.content}'", e)
            }
        val to =
            when (val toElement = obj["to"]) {
                null, JsonNull -> null
                is JsonPrimitive -> {
                    try {
                        LocalDate.parse(toElement.content)
                    } catch (e: IllegalArgumentException) {
                        throw SerializationException("Range.to is not a valid ISO date: '${toElement.content}'", e)
                    }
                }
                else -> throw SerializationException("Range.to must be a string or null")
            }
        return Range.Period(from, to)
    }
}

@Serializable
internal data class TransactionsTicketData(
    val account: AccountReference,
    val range: Range,
    val webhook: String?,
)

internal class TransactionsService(
    data: TransactionsTicketData,
) : Service<Ticket.Transactions>(
        "Transactions",
        Json.encodeToJsonElement(TransactionsTicketData.serializer(), data),
        Ticket::Transactions,
    )
