package tech.yaxi.routex.refresh

import io.ktor.http.Url
import kotlin.io.encoding.Base64
import kotlin.uuid.ExperimentalUuidApi
import tech.yaxi.routex.client.RoutexClient
import tech.yaxi.routex.models.ConnectionId
import tech.yaxi.routex.settlement.YaxiSystemVersionKeys
import tech.yaxi.routex.testing.internal.TestCredentials
import tech.yaxi.routex.tickets.RoutexTicketIssuer
import tech.yaxi.routex.transport.HttpTransport
import tech.yaxi.routex.transport.defaultHttpTransport

@OptIn(ExperimentalUuidApi::class)
internal object OnlineTestSupport {
    val DEMO_CONNECTION_ID: ConnectionId = ConnectionId("connection-96386142-60e5-4ca9-abcf-944efce5bc1e")
    const val DEMO_IBAN: String = "DE02120300000000202051"

    fun apiUrl(): Url = Url(TestCredentials.URL)

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

    /** Shared issuer keyed with the live `TestCredentials`. Random UUIDs and the default TTL. */
    val ticketIssuer: RoutexTicketIssuer by lazy {
        RoutexTicketIssuer(apiKeyId = TestCredentials.API_KEY_ID, base64Secret = TestCredentials.API_KEY)
    }

    private const val CI_TEST_SIGNING_KEY_ID: String = "dYa685dhHap8RSUtB4DDy1l4UcycsGhklBnV5a/4HSg="
    private const val CI_TEST_SIGNING_KEY_BASE64: String = "qzLgDnRegbiQzY416i9/MClrmMp24jcHzaWCWSWSutA="
    private val CI_TEST_SIGNING_KEYS: Map<String, ByteArray> =
        mapOf(CI_TEST_SIGNING_KEY_ID to Base64.Default.decode(CI_TEST_SIGNING_KEY_BASE64))
}
