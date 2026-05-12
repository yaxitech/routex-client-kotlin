package tech.yaxi.routex.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import tech.yaxi.routex.models.Credentials
import tech.yaxi.routex.models.Response

/**
 * Server returns [Response.Redirect] when the client sent a `yaxi-redirect-uri` header
 * (set via [RoutexClient.setRedirectUri]) and [Response.RedirectHandle] otherwise; the
 * latter lets the caller register the URI per-ticket via [RoutexClient.registerRedirectUri].
 * Both flows must resolve the bank-side redirect chain to the same registered URI.
 */
@Tag("online")
@OptIn(ExperimentalUuidApi::class)
class RedirectsOnlineTest {
    private val nonFollowingClient = HttpClient(CIO) { followRedirects = false }
    private val redirectUri = "myapp://redirect?context=signup"
    private val credentials =
        Credentials(
            connectionId = OnlineTestSupport.YAXI_DEMO_CONNECTION_ID,
            userId = "redirect",
        )

    @Test
    fun redirectHandleFlowResolvesToRegisteredUri() =
        runTest {
            val client = OnlineTestSupport.routexClient()
            val ticket = OnlineTestSupport.accountsTicket()

            val response =
                client.accounts(ticket = ticket, credentials = credentials, fields = emptyList())
            val handle = assertIs<Response.RedirectHandle<*>>(response).redirectHandle

            val url =
                client.registerRedirectUri(
                    ticket = ticket,
                    handle = handle.handle,
                    redirectUriValue = redirectUri,
                )
            assertEquals(redirectUri, followRedirectChain(url))

            val completion = client.confirmAccounts(ticket = ticket, context = handle.context)
            assertIs<Response.Result<*>>(completion)
        }

    @Test
    fun setRedirectUriFlowResolvesToRegisteredUri() =
        runTest {
            val client = OnlineTestSupport.routexClient().apply { setRedirectUri(redirectUri) }
            val ticket = OnlineTestSupport.accountsTicket()

            val response =
                client.accounts(ticket = ticket, credentials = credentials, fields = emptyList())
            val redirect = assertIs<Response.Redirect<*>>(response).redirect
            assertEquals(redirectUri, followRedirectChain(redirect.url))

            val completion = client.confirmAccounts(ticket = ticket, context = redirect.context)
            assertIs<Response.Result<*>>(completion)
        }

    /**
     * Walk the bank's redirect chain to the user-visible URI. `redirect.yaxi.tech`
     * holds the URI behind a query string the bank POSTs back; for any other host
     * the URI sits in the `Location` header of a single 303.
     */
    @Suppress("MagicNumber")
    private suspend fun followRedirectChain(url: String): String {
        val parsed = Url(url)
        return if (parsed.host == "redirect.yaxi.tech") {
            val response =
                nonFollowingClient.post("https://remux.yaxi.tech/redirect") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(parsed.encodedQuery)
                }
            val body = response.bodyAsText()
            check(response.status.value == 200 && body.isNotBlank()) {
                "expected 200 with a body from remux, got ${response.status.value}: $body"
            }
            body
        } else {
            val response = nonFollowingClient.request(parsed) { method = HttpMethod.Get }
            check(response.status.value == 303) { "expected 303 from $url, got ${response.status.value}" }
            assertNotNull(response.headers["location"], "redirect response missing Location header")
        }
    }
}
