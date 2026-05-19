package tech.yaxi.routex.client

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag

@Tag("online")
@OptIn(ExperimentalUuidApi::class)
class SystemVersionOnlineTest {
    @Test
    fun systemVersionIsPopulatedAfterSettling() {
        val client = OnlineTestSupport.routexClient()
        val ticket = OnlineTestSupport.ticketIssuer.accounts()
        runTest { client.info(ticket, OnlineTestSupport.YAXI_DEMO_CONNECTION_ID) }
        val systemVersion =
            assertNotNull(client.systemVersion(ticket), "systemVersion was null after service call")
        assertTrue(systemVersion.kind.isNotEmpty(), "kind must be populated")
        assertTrue(systemVersion.generation > 0, "generation must be positive, got ${systemVersion.generation}")
        assertTrue(systemVersion.ref.isNotEmpty(), "ref must be populated")
        // Force-parse the timestamp so we cover the lazy [createdAt] accessor too.
        assertNotNull(systemVersion.createdAt, "createdAt must parse")
    }
}
