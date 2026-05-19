package tech.yaxi.routex.client

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import tech.yaxi.routex.models.ConnectionId
import tech.yaxi.routex.models.TicketError
import tech.yaxi.routex.models.TicketErrorCode
import tech.yaxi.routex.testing.internal.TestCredentials
import tech.yaxi.routex.tickets.RoutexTicketIssuer

@Tag("online")
@OptIn(ExperimentalUuidApi::class)
class ErrorsOnlineTest {
    @Test
    fun expiredTicketOnInfoThrowsTicketErrorWithExpiredCode() {
        val client = OnlineTestSupport.routexClient()
        // Sign the expired ticket with a clock fixed one hour in the past so `exp` is
        // already behind the server's wall clock by the time it sees the request.
        val past = Clock.System.now() - 1.hours
        val expiredIssuer =
            RoutexTicketIssuer(
                apiKeyId = TestCredentials.API_KEY_ID,
                apiKeySecret = Base64.Default.decode(TestCredentials.API_KEY),
                ttl = 10.minutes,
                clock =
                    object : Clock {
                        override fun now(): Instant = past
                    },
            )
        val expiredTicket = expiredIssuer.accounts()
        // Warm settlement with a valid ticket so the expired ticket fails on `info`
        // rather than on the settlement round-trip.
        val validTicket = OnlineTestSupport.ticketIssuer.accounts()
        runTest { client.info(validTicket, OnlineTestSupport.YAXI_DEMO_CONNECTION_ID) }

        val error =
            assertFailsWith<TicketError> {
                runTest {
                    client.info(expiredTicket, ConnectionId("connection-00000000-0000-0000-0000-000000000000"))
                }
            }
        assertEquals("Ticket is expired", error.message)
        assertEquals(TicketErrorCode.EXPIRED, error.code)
    }
}
