package tech.yaxi.routex.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import tech.yaxi.routex.models.AccountReference
import tech.yaxi.routex.models.Credentials
import tech.yaxi.routex.models.Response
import tech.yaxi.routex.models.TransactionStatus
import tech.yaxi.routex.models.TransactionsResult
import tech.yaxi.routex.tickets.Range

@OnlineTest
@OptIn(ExperimentalUuidApi::class)
class TransactionsOnlineTest {
    @Test
    fun transactionsFlowReturnsCanonicalDemoPayload() =
        runTest {
            val client = OnlineTestSupport.routexClient()
            val rangeFrom = Clock.System.todayIn(TimeZone.UTC).minus(DatePeriod(days = 89))
            val ticket =
                OnlineTestSupport.ticketIssuer.transactions(
                    account = AccountReference.iban("DE02120300000000202051", "EUR"),
                    range = Range.Period(from = rangeFrom),
                )

            val response =
                client.transactions(
                    ticket = ticket,
                    credentials =
                        Credentials(
                            connectionId = OnlineTestSupport.YAXI_DEMO_CONNECTION_ID,
                            userId = "result",
                        ),
                )
            val payload = assertIs<Response.Result<TransactionsResult>>(response).authenticated.decodeUnverified()
            assertEquals(ticket.id, payload.ticketId)

            // The demo connection's transactions should return generated demo transactions.
            val transactions = assertNotNull(payload.data, "expected non-null transactions list")
            assertTrue(transactions.isNotEmpty())
            transactions.forEach { transaction ->
                assertTrue(transaction.amount.currency.isNotEmpty())

                val creditor = assertNotNull(transaction.creditor)
                assertNotNull(creditor.name)
                assertNotNull(creditor.iban)
                val debtor = assertNotNull(transaction.debtor)
                assertNotNull(debtor.name)
                assertNotNull(debtor.iban)

                assertNotNull(transaction.bookingDate)
                assertNotNull(transaction.valueDate)

                assertTrue(
                    transaction.status in
                        setOf(
                            TransactionStatus.PENDING,
                            TransactionStatus.BOOKED,
                        ),
                )
            }
        }
}
