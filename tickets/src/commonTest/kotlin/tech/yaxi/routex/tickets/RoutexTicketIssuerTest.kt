package tech.yaxi.routex.tickets

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tech.yaxi.routex.models.AccountReference
import tech.yaxi.routex.models.Amount
import tech.yaxi.routex.models.Base64UrlNoPad
import tech.yaxi.routex.models.CreditorAccountIdentifier
import tech.yaxi.routex.models.RoutexJson
import tech.yaxi.routex.tickets.internal.hmacSha256

@OptIn(ExperimentalUuidApi::class)
class RoutexTicketIssuerTest {
    @Test
    fun unitTypedServicesProduceCorrectHeaderAndNullData() {
        val issuer = newIssuer()
        val cases =
            listOf(
                "Accounts" to issuer.accounts().raw,
                "Balances" to issuer.balances().raw,
                "Transfer" to issuer.transfer().raw,
            )
        cases.forEach { (expectedService, raw) ->
            val (header, payload) = decodeTicket(raw)

            assertEquals("HS256", header["alg"]?.jsonPrimitive?.content)
            assertEquals("JWT", header["typ"]?.jsonPrimitive?.content)
            assertEquals(API_KEY_ID, header["kid"]?.jsonPrimitive?.content)

            val data = payload["data"]?.jsonObject ?: error("missing data claim")
            assertEquals(expectedService, data["service"]?.jsonPrimitive?.content)
            Uuid.parse(data["id"]?.jsonPrimitive?.content ?: error("missing id"))
            assertEquals(JsonNull, data["data"], "unit-typed services emit data:null")
        }
    }

    @Test
    fun transactionsTicketShapesPeriodRangeAndNullWebhook() {
        val raw =
            newIssuer()
                .transactions(
                    account = AccountReference.iban("DE02120300000000202051", "EUR"),
                    range = Range.Period(LocalDate(2026, 1, 1)),
                ).raw
        val ticketData = decodeTicket(raw).second.ticketData

        assertEquals("DE02120300000000202051", ticketData.iban("account"))
        assertEquals(JsonNull, ticketData["webhook"], "webhook is emitted as null, not omitted")

        val range = ticketData["range"]?.jsonObject ?: error("missing range")
        assertEquals("2026-01-01", range["from"]?.jsonPrimitive?.content)
        assertEquals(JsonNull, range["to"], "Period.to is emitted as null, not omitted")
    }

    @Test
    fun transactionsTicketShapesReferenceRangeAndExplicitWebhook() {
        val raw =
            newIssuer()
                .transactions(
                    account = AccountReference.iban("DE02120300000000202051"),
                    range = Range.Reference("abc"),
                    webhook = "https://example.invalid/hook",
                ).raw
        val ticketData = decodeTicket(raw).second.ticketData

        assertEquals("https://example.invalid/hook", ticketData["webhook"]?.jsonPrimitive?.content)
        assertEquals(
            "abc",
            ticketData["range"]
                ?.jsonObject
                ?.get("reference")
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun collectPaymentTicketOmitsOptionalsByDefault() {
        val raw =
            newIssuer()
                .collectPayment(
                    amount = Amount("100.00", "EUR"),
                    creditorAccount = CreditorAccountIdentifier.Iban("DE79430609671288143100"),
                    creditorName = "YAXI GmbH",
                    remittance = "Sign-up fee routex 123456789",
                ).raw
        val ticketData = decodeTicket(raw).second.ticketData

        val amount = ticketData["amount"]?.jsonObject ?: error("missing amount")
        assertEquals("100.00", amount["amount"]?.jsonPrimitive?.content)
        assertEquals("EUR", amount["currency"]?.jsonPrimitive?.content)
        assertEquals("DE79430609671288143100", ticketData.iban("creditorAccount"))
        assertEquals("YAXI GmbH", ticketData["creditorName"]?.jsonPrimitive?.content)
        assertEquals("Sign-up fee routex 123456789", ticketData["remittance"]?.jsonPrimitive?.content)
        assertNull(ticketData["instant"], "instant is omitted when null")
        assertNull(ticketData["fields"], "fields is omitted when null")
    }

    @Test
    fun collectPaymentTicketEmitsOptionalsWhenProvided() {
        val raw =
            newIssuer()
                .collectPayment(
                    amount = Amount("1.00", "EUR"),
                    creditorAccount = CreditorAccountIdentifier.Iban("DE79430609671288143100"),
                    creditorName = "YAXI GmbH",
                    remittance = "fee",
                    instant = false,
                    fields = listOf(CollectPaymentField.DebtorIban, CollectPaymentField.DebtorName),
                ).raw
        val ticketData = decodeTicket(raw).second.ticketData

        assertEquals(false, ticketData["instant"]?.jsonPrimitive?.content?.toBooleanStrict())
        assertEquals(
            "[\"debtorIban\",\"debtorName\"]",
            ticketData["fields"]?.toString(),
            "field enum values must serialize as camelCase strings",
        )
    }

    @Test
    fun expClaimReflectsClockAndTtlIncludingPerCallOverride() {
        val now = Instant.fromEpochSeconds(1_800_000_000)
        val issuer =
            RoutexTicketIssuer(
                apiKeyId = API_KEY_ID,
                apiKeySecret = API_KEY_SECRET,
                ttl = 7.minutes,
                clock = fixedClock(now),
            )

        val defaultExp = decodeTicket(issuer.accounts().raw).second.exp()
        assertEquals(now.epochSeconds + 7 * 60, defaultExp)

        val overrideExp = decodeTicket(issuer.accounts(ttl = 30.seconds).raw).second.exp()
        assertEquals(now.epochSeconds + 30, overrideExp)
    }

    @Test
    fun ticketIdIsAFreshUuidv4PerCall() {
        val issuer = newIssuer()
        val first = issuer.accounts().id
        val second = issuer.accounts().id
        require(first != second) { "expected two distinct UUIDs across calls" }
        // Each id must round-trip as a parseable Uuid; also covers the wire format.
        Uuid.parse(first.toString())
    }

    @Test
    fun callerSuppliedTicketIdAppearsOnTheTicket() {
        val explicit = Uuid.random()
        assertEquals(explicit, newIssuer().accounts(ticketId = explicit).id)
    }

    @Test
    fun signatureMatchesHmacSha256OverHeaderAndPayload() {
        // Sign once, recompute the signature ourselves, expect equality. Catches accidental
        // changes to header serialization (which would shift the bytes the HMAC keys over).
        val (header, payload, signature) = splitJwt(newIssuer().accounts().raw)
        val expected =
            Base64UrlNoPad.encode(hmacSha256(API_KEY_SECRET, "$header.$payload".encodeToByteArray()))
        assertEquals(expected, signature)
    }

    @Test
    fun constructorRejectsInvalidInputs() {
        assertFailsWith<IllegalArgumentException> {
            RoutexTicketIssuer(apiKeyId = " ", apiKeySecret = API_KEY_SECRET)
        }
        assertFailsWith<IllegalArgumentException> {
            RoutexTicketIssuer(apiKeyId = API_KEY_ID, apiKeySecret = ByteArray(0))
        }
        assertFailsWith<IllegalArgumentException> {
            RoutexTicketIssuer(apiKeyId = API_KEY_ID, apiKeySecret = API_KEY_SECRET, ttl = 0.seconds)
        }
    }

    @Test
    fun blankServiceInputsAreRejected() {
        val issuer = newIssuer()
        val creditor = CreditorAccountIdentifier.Iban("DE79430609671288143100")
        assertFailsWith<IllegalArgumentException> {
            issuer.collectPayment(Amount("1.00", "EUR"), creditor, creditorName = " ", remittance = "fee")
        }
        assertFailsWith<IllegalArgumentException> {
            issuer.collectPayment(Amount("1.00", "EUR"), creditor, creditorName = "YAXI", remittance = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            issuer.transactions(
                account = AccountReference.iban("DE02120300000000202051"),
                range = Range.Reference("abc"),
                webhook = " ",
            )
        }
    }

    @Test
    fun base64SecretConstructorDecodesToTheSameSigningKey() {
        // A signature produced via the base64 constructor verifies against the raw key bytes.
        // This catches a decoder bug regardless of clock or UUID, since each issuer call sees
        // its own fresh random UUID.
        val rawBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val base64 = Base64.Default.encode(rawBytes)
        val raw = RoutexTicketIssuer(API_KEY_ID, base64).accounts().raw
        val (header, payload, signature) = splitJwt(raw)
        val expected = Base64UrlNoPad.encode(hmacSha256(rawBytes, "$header.$payload".encodeToByteArray()))
        assertEquals(expected, signature)
    }

    private fun newIssuer(): RoutexTicketIssuer = RoutexTicketIssuer(API_KEY_ID, API_KEY_SECRET)

    private fun fixedClock(instant: Instant): Clock =
        object : Clock {
            override fun now(): Instant = instant
        }

    private fun decodeTicket(raw: String): Pair<JsonObject, JsonObject> {
        val (h, p, _) = splitJwt(raw)
        val header = RoutexJson.parseToJsonElement(Base64UrlNoPad.decode(h).decodeToString()).jsonObject
        val payload = RoutexJson.parseToJsonElement(Base64UrlNoPad.decode(p).decodeToString()).jsonObject
        return header to payload
    }

    private fun splitJwt(raw: String): Triple<String, String, String> {
        val parts = raw.split(".")
        assertEquals(3, parts.size, "JWT must have three dot-separated segments, got $raw")
        return Triple(parts[0], parts[1], parts[2])
    }

    /** Convenience accessor: the `data.data` claim, i.e. the inner ticket-data object. */
    private val JsonObject.ticketData: JsonObject
        get() = this["data"]?.jsonObject?.get("data")?.jsonObject ?: error("missing data.data")

    private fun JsonObject.iban(key: String): String? =
        this[key]
            ?.jsonObject
            ?.get("iban")
            ?.jsonPrimitive
            ?.content

    private fun JsonObject.exp(): Long? = this["exp"]?.jsonPrimitive?.content?.toLong()

    private companion object {
        const val API_KEY_ID: String = "test-key-id"
        val API_KEY_SECRET: ByteArray = "test-secret-bytes-with-enough-entropy".encodeToByteArray()
    }
}
