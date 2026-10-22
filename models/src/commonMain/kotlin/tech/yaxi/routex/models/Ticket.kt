package tech.yaxi.routex.models

import kotlin.jvm.JvmStatic
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Thrown when a raw JWT string is not a valid Routex ticket (bad structure, missing
 * claims, or a service claim that does not match the typed [Ticket] subclass).
 */
public class InvalidTicketError(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * A Routex service ticket. The raw string is a JWT whose `data.service` claim names the
 * specific service it authenticates; the subclasses pin that service at the type level
 * so the Kotlin compiler rejects passing an `Accounts` ticket to `balances()` and vice
 * versa.
 *
 * Ticket instances are cheap to create: construction parses the JWT payload (no
 * signature verification) and caches the extracted `data.id` claim.
 */
@OptIn(ExperimentalUuidApi::class)
public sealed class Ticket {
    /** The underlying JWT string. */
    public abstract val raw: String

    /** The `data.id` UUIDv4 claim extracted from the JWT payload. */
    public abstract val id: Uuid

    /** Ticket that authenticates a call to the accounts service. */
    public data class Accounts
        @Throws(InvalidTicketError::class)
        constructor(
            override val raw: String,
        ) : Ticket() {
            override val id: Uuid = validateAndExtractId(raw, "Accounts")
        }

    /** Ticket that authenticates a call to the balances service. */
    public data class Balances
        @Throws(InvalidTicketError::class)
        constructor(
            override val raw: String,
        ) : Ticket() {
            override val id: Uuid = validateAndExtractId(raw, "Balances")
        }

    /** Ticket that authenticates a call to the collect-payment service. */
    public data class CollectPayment
        @Throws(InvalidTicketError::class)
        constructor(
            override val raw: String,
        ) : Ticket() {
            override val id: Uuid = validateAndExtractId(raw, "CollectPayment")
        }

    /** Ticket that authenticates a call to the standing-orders service. */
    public data class StandingOrders
        @Throws(InvalidTicketError::class)
        constructor(
            override val raw: String,
        ) : Ticket() {
            override val id: Uuid = validateAndExtractId(raw, "StandingOrders")
        }

    /** Ticket that authenticates a call to the transactions service. */
    public data class Transactions
        @Throws(InvalidTicketError::class)
        constructor(
            override val raw: String,
        ) : Ticket() {
            override val id: Uuid = validateAndExtractId(raw, "Transactions")
        }

    /** Ticket that authenticates a call to the transfer service. */
    public data class Transfer
        @Throws(InvalidTicketError::class)
        constructor(
            override val raw: String,
        ) : Ticket() {
            override val id: Uuid = validateAndExtractId(raw, "Transfer")
        }

    public companion object {
        /**
         * Parse [raw] and return the [Ticket] subclass whose service name matches
         * the JWT's `data.service` claim. Throws [InvalidTicketError] for unknown
         * services or malformed payloads.
         *
         * Reach for this only when the calling code does not know the service
         * ahead of time (generic dispatch, diagnostics). When the service is
         * known, prefer the typed constructor: [Accounts], [Balances],
         * [CollectPayment], [StandingOrders], [Transactions], or [Transfer].
         * Each typed constructor performs the same parsing and additionally
         * enforces the expected service at the type level.
         */
        @JvmStatic
        @Throws(InvalidTicketError::class)
        public fun parse(raw: String): Ticket {
            val service = extractService(raw)
            return when (service) {
                "Accounts" -> Accounts(raw)
                "Balances" -> Balances(raw)
                "CollectPayment" -> CollectPayment(raw)
                "StandingOrders" -> StandingOrders(raw)
                "Transactions" -> Transactions(raw)
                "Transfer" -> Transfer(raw)
                else -> throw InvalidTicketError("Unknown ticket service: '$service'")
            }
        }
    }
}

private data class TicketClaims(
    val service: String,
    val id: String,
)

private val ticketJson: Json = Json { ignoreUnknownKeys = true }

private fun parseTicketClaims(raw: String): TicketClaims {
    val segments = raw.split(".")
    if (segments.size < 2) {
        throw InvalidTicketError("JWT must contain at least two segments")
    }
    val payloadText =
        try {
            Base64UrlNoPad.decode(segments[1]).decodeToString()
        } catch (e: IllegalArgumentException) {
            throw InvalidTicketError("JWT payload is not valid base64url: ${e.message}", e)
        }
    val obj: JsonObject =
        try {
            ticketJson.parseToJsonElement(payloadText).jsonObject
        } catch (e: SerializationException) {
            throw InvalidTicketError("JWT payload is not a JSON object: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw InvalidTicketError("JWT payload is not a JSON object: ${e.message}", e)
        }
    val data =
        obj["data"] as? JsonObject
            ?: throw InvalidTicketError("JWT payload missing `data` claim")
    val id =
        data["id"]?.jsonPrimitive?.content
            ?: throw InvalidTicketError("JWT payload missing `data.id` claim")
    val service =
        data["service"]?.jsonPrimitive?.content
            ?: throw InvalidTicketError("JWT payload missing `data.service` claim")
    return TicketClaims(service = service, id = id)
}

private fun extractService(raw: String): String = parseTicketClaims(raw).service

@OptIn(ExperimentalUuidApi::class)
private fun validateAndExtractId(
    raw: String,
    expectedService: String,
): Uuid {
    val claims = parseTicketClaims(raw)
    if (claims.service != expectedService) {
        throw InvalidTicketError(
            "Expected ticket for service '$expectedService' but JWT claims service '${claims.service}'",
        )
    }
    return try {
        Uuid.parse(claims.id)
    } catch (e: IllegalArgumentException) {
        throw InvalidTicketError("JWT payload `data.id` claim is not a valid UUID: '${claims.id}'", e)
    }
}
