package tech.yaxi.routex.client

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import tech.yaxi.routex.models.AccountReference
import tech.yaxi.routex.models.Amount
import tech.yaxi.routex.models.Credentials
import tech.yaxi.routex.models.ExecutionRule
import tech.yaxi.routex.models.Frequency
import tech.yaxi.routex.models.NotFound
import tech.yaxi.routex.models.Response
import tech.yaxi.routex.models.StandingOrdersResult

@OnlineTest
@OptIn(ExperimentalUuidApi::class)
class StandingOrdersOnlineTest {
    private val credentials =
        Credentials(
            connectionId = OnlineTestSupport.YAXI_DEMO_CONNECTION_ID,
            userId = "result",
        )

    @Test
    fun standingOrdersFlowReturnsCanonicalDemoPayload() =
        runTest {
            val client = OnlineTestSupport.routexClient()
            val ticket =
                OnlineTestSupport.ticketIssuer.standingOrders(
                    account = AccountReference.iban("DE02120300000000202051", "EUR"),
                )

            val response = client.standingOrders(ticket = ticket, credentials = credentials)
            val payload = assertIs<Response.Result<StandingOrdersResult>>(response).authenticated.decodeUnverified()
            assertEquals(ticket.id, payload.ticketId)
            assertEquals(4, payload.data.size)

            val (rent, broadcasting) = payload.data
            assertEquals(LocalDate(2023, 2, 1), rent.startDate)
            assertEquals(1, assertIs<Frequency.Months>(rent.frequency).value)
            assertEquals(ExecutionRule.FOLLOWING, rent.executionRule)
            assertEquals(1, rent.day)
            assertEquals(Amount(amount = BigDecimal.parseString("1000"), currency = "EUR"), rent.amount)
            assertEquals("DE89370400440532013000", assertNotNull(rent.creditor).iban)

            assertEquals(
                listOf(Month.JANUARY, Month.APRIL, Month.JULY, Month.OCTOBER),
                assertIs<Frequency.VariableMonths>(broadcasting.frequency).months,
            )
            assertEquals(Amount(amount = BigDecimal.parseString("55.08"), currency = "EUR"), broadcasting.amount)
        }

    @Test
    fun unknownAccountFails() =
        runTest {
            val client = OnlineTestSupport.routexClient()

            assertFailsWith<NotFound> {
                client.standingOrders(
                    ticket =
                        OnlineTestSupport.ticketIssuer.standingOrders(
                            account = AccountReference.iban("NL31YAXI1234567891", "EUR"),
                        ),
                    credentials = credentials,
                )
            }
        }
}
