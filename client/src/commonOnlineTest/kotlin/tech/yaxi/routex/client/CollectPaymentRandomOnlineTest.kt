package tech.yaxi.routex.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.request
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import tech.yaxi.routex.models.AccountIdentifier
import tech.yaxi.routex.models.Amount
import tech.yaxi.routex.models.Credentials
import tech.yaxi.routex.models.DialogInput
import tech.yaxi.routex.models.Response
import tech.yaxi.routex.models.Ticket
import tech.yaxi.routex.tickets.CollectPaymentField

@Tag("online")
@OptIn(ExperimentalUuidApi::class)
class CollectPaymentRandomOnlineTest {
    @Test
    fun randomUserDrivesInterruptsBeforeResult() =
        runTest {
            val client = OnlineTestSupport.routexClient()
            val nonFollowingClient = HttpClient(CIO) { followRedirects = false }
            val ticket =
                OnlineTestSupport.ticketIssuer.collectPayment(
                    amount = Amount("100", "EUR"),
                    creditorAccount = AccountIdentifier.Iban("DE79430609671288143100"),
                    creditorName = "YAXI GmbH",
                    remittance = "Sign-up fee routex 123456789",
                    fields = listOf(CollectPaymentField.DebtorIban, CollectPaymentField.DebtorName),
                )
            val credentials =
                Credentials(
                    connectionId = OnlineTestSupport.YAXI_DEMO_CONNECTION_ID,
                    userId = "random",
                )

            var response: Response<*> =
                client.collectPayment(ticket = ticket, credentials = credentials, account = null)
            var steps = 0
            while (response !is Response.Result) {
                check(steps++ < MAX_STEPS) {
                    "too many interrupt steps; last response was ${response::class.simpleName}"
                }
                response = handleInterrupt(client, nonFollowingClient, ticket, response)
            }
            assertTrue(steps > 1, "expected multiple interrupt steps for 'random'")
        }

    private suspend fun handleInterrupt(
        client: RoutexClient,
        httpClient: HttpClient,
        ticket: Ticket.CollectPayment,
        response: Response<*>,
    ): Response<*> =
        when (response) {
            is Response.Dialog -> handleDialog(client, ticket, response)
            is Response.Redirect -> {
                visitRedirect(httpClient, response.redirect.url)
                client.confirmCollectPayment(ticket = ticket, context = response.redirect.context)
            }
            is Response.RedirectHandle -> {
                val url =
                    client.registerRedirectUri(
                        ticket = ticket,
                        handle = response.redirectHandle.handle,
                        redirectUriValue = "smoketest://redirect",
                    )
                visitRedirect(httpClient, url)
                client.confirmCollectPayment(ticket = ticket, context = response.redirectHandle.context)
            }
            is Response.Result -> error("guarded by while loop")
        }

    private suspend fun handleDialog(
        client: RoutexClient,
        ticket: Ticket.CollectPayment,
        response: Response.Dialog<*>,
    ): Response<*> =
        when (val input = response.dialog.input) {
            is DialogInput.Confirmation -> {
                input.pollingDelaySecs?.let { delay(it.toLong() * MILLIS_PER_SECOND) }
                client.confirmCollectPayment(ticket = ticket, context = input.context)
            }
            is DialogInput.Field ->
                client.respondCollectPayment(ticket = ticket, context = input.context, response = "133742")
            is DialogInput.Selection ->
                client.respondCollectPayment(
                    ticket = ticket,
                    context = input.context,
                    response = input.options.first().key,
                )
        }

    /**
     * If [url] points at the YAXI redirector, post the query back to remux to simulate
     * the bank-side callback; otherwise issue a non-following GET so the bank emits its
     * 303 without us chasing it.
     */
    private suspend fun visitRedirect(
        httpClient: HttpClient,
        url: String,
    ) {
        val parsed = Url(url)
        httpClient.request(parsed) { method = HttpMethod.Get }
    }

    private companion object {
        const val MAX_STEPS = 10
        const val MILLIS_PER_SECOND = 1_000L
    }
}
