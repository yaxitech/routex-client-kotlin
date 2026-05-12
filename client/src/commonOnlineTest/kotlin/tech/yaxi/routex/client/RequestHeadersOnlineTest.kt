package tech.yaxi.routex.client

import io.ktor.http.Headers
import kotlin.concurrent.Volatile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import tech.yaxi.routex.models.SearchFilter
import tech.yaxi.routex.transport.HttpRequest
import tech.yaxi.routex.transport.HttpResponse
import tech.yaxi.routex.transport.HttpTransport
import tech.yaxi.routex.transport.defaultHttpTransport

@Tag("online")
@OptIn(ExperimentalUuidApi::class)
class RequestHeadersOnlineTest {
    @Test
    fun yaxiTicketIdHeaderIsSetOnServiceCall() =
        runTest {
            val capturing = CapturingHttpTransport()
            val client = OnlineTestSupport.routexClient(httpTransport = capturing)
            val ticket = OnlineTestSupport.accountsTicket()

            client.search(
                ticket = ticket,
                filters = listOf(SearchFilter.Term("sparkasse"), SearchFilter.Term("stadt")),
                limit = 20u,
            )

            val lastHeaders = assertNotNull(capturing.lastRequestHeaders, "no request was captured")
            assertEquals(ticket.id.toString(), lastHeaders["yaxi-ticket-id"])
        }

    private class CapturingHttpTransport : HttpTransport {
        @Volatile
        var lastRequestHeaders: Headers? = null
            private set
        private val delegate = defaultHttpTransport()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            lastRequestHeaders = request.headers
            return delegate.execute(request)
        }
    }
}
