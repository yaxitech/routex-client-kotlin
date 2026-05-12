package tech.yaxi.routex.client

import io.ktor.http.Url
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import tech.yaxi.routex.models.ConnectionId
import tech.yaxi.routex.models.RoutexJson
import tech.yaxi.routex.models.Ticket
import tech.yaxi.routex.settlement.YaxiSystemVersionKeys
import tech.yaxi.routex.testing.internal.TestCredentials
import tech.yaxi.routex.transport.HttpTransport
import tech.yaxi.routex.transport.defaultHttpTransport

@OptIn(ExperimentalUuidApi::class)
internal object OnlineTestSupport {
    val YAXI_DEMO_CONNECTION_ID: ConnectionId = ConnectionId(OnlineTestConstants.YAXI_DEMO_CONNECTION_ID)

    fun apiUrl(): Url = Url(TestCredentials.URL)

    fun apiKeyId(): String = TestCredentials.API_KEY_ID

    fun apiKeySecret(): ByteArray = Base64.Default.decode(TestCredentials.API_KEY)

    fun systemVersionSigningKeys(): Map<String, ByteArray> =
        if (TestCredentials.TEST_SIGNING_KEYS) CI_TEST_SIGNING_KEYS else YaxiSystemVersionKeys.DEFAULT

    fun routexClient(httpTransport: HttpTransport = defaultHttpTransport()): RoutexClient =
        RoutexClient(
            baseUrl = apiUrl(),
            httpTransport = httpTransport,
            signingKeys = systemVersionSigningKeys(),
        )

    fun rawTicket(
        service: String,
        ticketId: Uuid = Uuid.random(),
        ticketData: JsonElement = JsonNull,
        ttlSeconds: Long = TICKET_TTL_SECONDS,
    ): String {
        val now = Clock.System.now().toEpochMilliseconds()
        val claims =
            buildJsonObject {
                put(
                    "data",
                    buildJsonObject {
                        put("service", service)
                        put("id", ticketId.toString())
                        put("data", ticketData)
                    },
                )
                put("exp", (now / MILLIS_PER_SECOND) + ttlSeconds)
            }
        val claimsJson = RoutexJson.encodeToString(JsonObject.serializer(), claims)
        return HS256.sign(apiKeyId(), apiKeySecret(), claimsJson)
    }

    fun accountsTicket(
        ticketId: Uuid = Uuid.random(),
        ticketData: JsonElement = JsonNull,
        ttlSeconds: Long = TICKET_TTL_SECONDS,
    ): Ticket.Accounts = Ticket.Accounts(rawTicket("Accounts", ticketId, ticketData, ttlSeconds))

    fun balancesTicket(
        ticketId: Uuid = Uuid.random(),
        ticketData: JsonElement = JsonNull,
        ttlSeconds: Long = TICKET_TTL_SECONDS,
    ): Ticket.Balances = Ticket.Balances(rawTicket("Balances", ticketId, ticketData, ttlSeconds))

    fun collectPaymentTicket(
        ticketId: Uuid = Uuid.random(),
        ticketData: JsonElement = JsonNull,
        ttlSeconds: Long = TICKET_TTL_SECONDS,
    ): Ticket.CollectPayment = Ticket.CollectPayment(rawTicket("CollectPayment", ticketId, ticketData, ttlSeconds))

    fun transactionsTicket(
        ticketId: Uuid = Uuid.random(),
        ticketData: JsonElement = JsonNull,
        ttlSeconds: Long = TICKET_TTL_SECONDS,
    ): Ticket.Transactions = Ticket.Transactions(rawTicket("Transactions", ticketId, ticketData, ttlSeconds))

    fun transferTicket(
        ticketId: Uuid = Uuid.random(),
        ticketData: JsonElement = JsonNull,
        ttlSeconds: Long = TICKET_TTL_SECONDS,
    ): Ticket.Transfer = Ticket.Transfer(rawTicket("Transfer", ticketId, ticketData, ttlSeconds))

    /** Pinned €1.00 collect-payment ticket data; the server echoes back debtor IBAN and name. */
    fun collectPaymentTicketData(): JsonElement =
        buildJsonObject {
            put(
                "amount",
                buildJsonObject {
                    put("currency", "EUR")
                    put("amount", "100")
                },
            )
            put("creditorAccount", buildJsonObject { put("iban", "DE79430609671288143100") })
            put("creditorName", "YAXI GmbH")
            put("remittance", "Sign-up fee routex 123456789")
            put(
                "fields",
                buildJsonArray {
                    add("debtorIban")
                    add("debtorName")
                },
            )
        }

    private const val TICKET_TTL_SECONDS: Long = 60
    private const val MILLIS_PER_SECOND: Long = 1_000

    private const val CI_TEST_SIGNING_KEY_ID: String = "dYa685dhHap8RSUtB4DDy1l4UcycsGhklBnV5a/4HSg="
    private const val CI_TEST_SIGNING_KEY_BASE64: String = "qzLgDnRegbiQzY416i9/MClrmMp24jcHzaWCWSWSutA="
    private val CI_TEST_SIGNING_KEYS: Map<String, ByteArray> =
        mapOf(CI_TEST_SIGNING_KEY_ID to Base64.Default.decode(CI_TEST_SIGNING_KEY_BASE64))
}
