package tech.yaxi.routex.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import tech.yaxi.routex.models.ConnectionId
import tech.yaxi.routex.models.TicketError
import tech.yaxi.routex.models.TicketErrorCode

@Tag("online")
@OptIn(ExperimentalUuidApi::class)
class ErrorsOnlineTest {
    @Test
    fun expiredTicketOnInfoThrowsTicketErrorWithExpiredCode() {
        val client = OnlineTestSupport.routexClient()
        val expiredTicket = OnlineTestSupport.accountsTicket(ttlSeconds = -NEGATIVE_TTL_SECONDS)
        // Warm settlement with a valid ticket so the expired ticket fails on `info`
        // rather than on the settlement round-trip.
        val validTicket = OnlineTestSupport.accountsTicket()
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

    private companion object {
        const val NEGATIVE_TTL_SECONDS: Long = 300
    }
}
