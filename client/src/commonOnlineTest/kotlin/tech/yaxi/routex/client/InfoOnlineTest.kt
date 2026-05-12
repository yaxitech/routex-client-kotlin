package tech.yaxi.routex.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import tech.yaxi.routex.models.ConnectionId
import tech.yaxi.routex.models.NotFound

@Tag("online")
@OptIn(ExperimentalUuidApi::class)
class InfoOnlineTest {
    @Test
    fun infoForUnknownConnectionReturnsNotFound() {
        val client =
            OnlineTestSupport.routexClient()
        val ticket = OnlineTestSupport.accountsTicket()
        val unknownConnectionId = ConnectionId(Uuid.random())

        assertFailsWith<NotFound> {
            runTest {
                client.info(ticket, unknownConnectionId)
            }
        }
    }

    @Test
    fun infoForDemoConnectionReturnsExpectedShape() =
        runTest {
            val client =
                OnlineTestSupport.routexClient()
            val ticket = OnlineTestSupport.accountsTicket()

            val info = client.info(ticket, OnlineTestSupport.YAXI_DEMO_CONNECTION_ID)
            assertEquals(OnlineTestSupport.YAXI_DEMO_CONNECTION_ID, info.id)
            assertTrue(info.countries.isEmpty(), "expected no countries, got ${info.countries}")
            assertEquals("YAXI Demo (user)", info.displayName)
            assertEquals(false, info.credentials.full)
            assertEquals(true, info.credentials.userId)
            assertEquals(false, info.credentials.none)
            assertEquals("User ID", info.userId)
            assertEquals("Password", info.password)
            assertEquals("yaxi", info.logoId)
            val bics = assertNotNull(info.bics)
            assertTrue(bics.isEmpty(), "expected no bics, got $bics")
        }
}
