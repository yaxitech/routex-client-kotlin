package tech.yaxi.routex.refresh

import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertNotNull
import tech.yaxi.routex.transport.HttpMethod
import tech.yaxi.routex.transport.HttpRequest
import tech.yaxi.routex.transport.HttpResponse
import tech.yaxi.routex.transport.HttpTransport

private class UnusedTransport : HttpTransport {
    override suspend fun execute(request: HttpRequest): HttpResponse =
        error("transport should not be invoked in this test")
}

class RefreshClientConstructorTest {
    @Test
    fun primaryConstructorWithBaseUrlAndTransportProducesConfiguredInstance() {
        val client = RoutexRefreshClient(baseUrl = Url("https://example.invalid"), httpTransport = UnusedTransport())
        assertNotNull(client)
        // Spot-check that :transport's `HttpMethod` flows transitively through :settlement.
        val method = HttpMethod.GET
        assertNotNull(method)
    }
}
