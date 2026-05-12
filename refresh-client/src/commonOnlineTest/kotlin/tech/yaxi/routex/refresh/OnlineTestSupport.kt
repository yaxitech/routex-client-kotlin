package tech.yaxi.routex.refresh

import io.ktor.http.Url
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.kotlincrypto.macs.hmac.sha2.HmacSHA256
import tech.yaxi.routex.client.RoutexClient
import tech.yaxi.routex.models.Base64UrlNoPad
import tech.yaxi.routex.models.ConnectionId
import tech.yaxi.routex.models.RoutexJson
import tech.yaxi.routex.models.Ticket
import tech.yaxi.routex.settlement.YaxiSystemVersionKeys
import tech.yaxi.routex.testing.internal.TestCredentials
import tech.yaxi.routex.transport.HttpTransport
import tech.yaxi.routex.transport.defaultHttpTransport

@OptIn(ExperimentalUuidApi::class)
internal object OnlineTestSupport {
    private const val TICKET_TTL_SECONDS: Long = 60
    private const val MILLIS_PER_SECOND: Long = 1_000

    private const val CI_TEST_SIGNING_KEY_ID: String = "dYa685dhHap8RSUtB4DDy1l4UcycsGhklBnV5a/4HSg="
    private const val CI_TEST_SIGNING_KEY_BASE64: String = "qzLgDnRegbiQzY416i9/MClrmMp24jcHzaWCWSWSutA="
    private val CI_TEST_SIGNING_KEYS: Map<String, ByteArray> =
        mapOf(CI_TEST_SIGNING_KEY_ID to Base64.Default.decode(CI_TEST_SIGNING_KEY_BASE64))

    val DEMO_CONNECTION_ID: ConnectionId = ConnectionId("connection-96386142-60e5-4ca9-abcf-944efce5bc1e")
    const val DEMO_IBAN: String = "DE02120300000000202051"

    fun apiUrl(): Url = Url(TestCredentials.URL)

    fun apiKeyId(): String = TestCredentials.API_KEY_ID

    fun apiKeySecret(): ByteArray = Base64.Default.decode(TestCredentials.API_KEY)

    fun systemVersionSigningKeys(): Map<String, ByteArray> =
        if (TestCredentials.TEST_SIGNING_KEYS) CI_TEST_SIGNING_KEYS else YaxiSystemVersionKeys.DEFAULT

    fun refreshClient(httpTransport: HttpTransport = defaultHttpTransport()): RoutexRefreshClient =
        RoutexRefreshClient(
            baseUrl = apiUrl(),
            httpTransport = httpTransport,
            signingKeys = systemVersionSigningKeys(),
        )

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
        val header =
            Base64UrlNoPad.encode(
                """{"alg":"HS256","typ":"JWT","kid":"${apiKeyId()}"}""".encodeToByteArray(),
            )
        val payload = Base64UrlNoPad.encode(claimsJson.encodeToByteArray())
        val signingInput = "$header.$payload"
        val mac = HmacSHA256(apiKeySecret())
        mac.update(signingInput.encodeToByteArray())
        val signature = Base64UrlNoPad.encode(mac.doFinal())
        return "$signingInput.$signature"
    }

    fun accountsTicket(ticketId: Uuid = Uuid.random()): Ticket.Accounts =
        Ticket.Accounts(rawTicket("Accounts", ticketId))

    fun balancesTicket(
        ticketId: Uuid = Uuid.random(),
        ticketData: JsonElement = JsonNull,
    ): Ticket.Balances = Ticket.Balances(rawTicket("Balances", ticketId, ticketData))

    fun transactionsTicket(
        ticketId: Uuid = Uuid.random(),
        ticketData: JsonElement = JsonNull,
    ): Ticket.Transactions = Ticket.Transactions(rawTicket("Transactions", ticketId, ticketData))
}
