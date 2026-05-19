package tech.yaxi.routex.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import tech.yaxi.routex.models.NotFound
import tech.yaxi.routex.models.TraceId

@Tag("online")
@OptIn(ExperimentalUuidApi::class)
class TracesOnlineTest {
    @Test
    fun traceIdChangesAfterServiceCallAndTraceIsRetrievable() =
        runTest {
            val client = OnlineTestSupport.routexClient()
            val ticket = OnlineTestSupport.ticketIssuer.accounts()

            val initialTraceId = client.traceId

            val info = client.info(ticket, OnlineTestSupport.YAXI_DEMO_CONNECTION_ID)
            assertEquals(OnlineTestSupport.YAXI_DEMO_CONNECTION_ID, info.id)

            val traceIdAfterCall = client.traceId
            assertNotNull(traceIdAfterCall, "expected a trace id after the service call")
            if (initialTraceId != null) {
                assertFalse(
                    initialTraceId == traceIdAfterCall,
                    "trace id should have changed after a service call",
                )
            }

            val trace = client.trace(ticket, traceIdAfterCall)
            val looksAgeEncrypted = trace.startsWith("-----BEGIN AGE ENCRYPTED FILE-----")
            if (looksAgeEncrypted) {
                assertTrue(
                    trace.endsWith("-----END AGE ENCRYPTED FILE-----\n"),
                    "age-encrypted trace missing footer",
                )
            } else {
                assertTrue(trace.isNotBlank(), "expected non-blank trace payload")
            }
        }

    @Test
    fun traceCallSettlesKeyEvenWhenTraceIsNotFound() {
        val client = OnlineTestSupport.routexClient()
        val ticket = OnlineTestSupport.ticketIssuer.accounts()

        assertFailsWith<NotFound> {
            runTest {
                client.trace(ticket, TraceId("wurzeltrace".encodeToByteArray()))
            }
        }
        assertNotNull(client.systemVersion(ticket), "systemVersion was null; key did not settle")
    }
}
