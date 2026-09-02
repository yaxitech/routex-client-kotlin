package tech.yaxi.routex.client

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
import tech.yaxi.routex.models.AccountField
import tech.yaxi.routex.models.AccountReference
import tech.yaxi.routex.models.AccountsResult
import tech.yaxi.routex.models.BalanceType
import tech.yaxi.routex.models.BalancesResult
import tech.yaxi.routex.models.ConnectionData
import tech.yaxi.routex.models.Credentials
import tech.yaxi.routex.models.InterruptError
import tech.yaxi.routex.models.NotFound
import tech.yaxi.routex.models.Response
import tech.yaxi.routex.models.TransactionsResult
import tech.yaxi.routex.tickets.Range

/**
 * The `accounts`/`balances`/`transactions` overloads that take [ConnectionData] instead of
 * [Credentials], covering both the happy path and the two ways a call can fail without a
 * user to interact with.
 */
@OnlineTest
@OptIn(ExperimentalUuidApi::class)
class WithoutCredentialsOnlineTest {
    private val demoIban = "DE02120300000000202051"

    /** The "refresh" demo user completes without dialogs and returns reusable connection data. */
    private suspend fun connectionData(client: RoutexClient): ConnectionData {
        val response =
            client.accounts(
                ticket = OnlineTestSupport.ticketIssuer.accounts(),
                credentials =
                    Credentials(
                        connectionId = OnlineTestSupport.YAXI_DEMO_CONNECTION_ID,
                        userId = "refresh",
                    ),
                fields = emptyList(),
            )

        return assertNotNull(
            assertIs<Response.Result<AccountsResult>>(response).connectionData,
            "expected connection data",
        )
    }

    private fun daysAgo(days: Int) = Clock.System.todayIn(TimeZone.UTC).minus(DatePeriod(days = days))

    @Test
    fun accountsWithoutCredentials() =
        runTest {
            val client = OnlineTestSupport.routexClient()
            val ticket = OnlineTestSupport.ticketIssuer.accounts()

            val response =
                client.accounts(
                    ticket = ticket,
                    connectionData = connectionData(client),
                    fields = listOf(AccountField.Iban, AccountField.OwnerName),
                )

            val payload = assertIs<Response.Result<AccountsResult>>(response).authenticated.decodeUnverified()
            assertEquals(ticket.id, payload.ticketId)
            assertEquals(1, payload.data.size)
            assertEquals(demoIban, payload.data[0].iban)
            assertEquals("Dr. Peter Steiger", payload.data[0].ownerName)
        }

    @Test
    fun balancesWithoutCredentials() =
        runTest {
            val client = OnlineTestSupport.routexClient()
            val known = AccountReference.iban(demoIban, "EUR")
            val unknown = AccountReference.iban("NL58YAXI1234567890", "EUR")

            val response =
                client.balances(
                    ticket = OnlineTestSupport.ticketIssuer.balances(),
                    connectionData = connectionData(client),
                    accounts = listOf(known, unknown),
                )

            val payload = assertIs<Response.Result<BalancesResult>>(response).authenticated.decodeUnverified()
            assertEquals(1, payload.data.balances.size)
            assertEquals(known, payload.data.balances[0].account)

            val balances = payload.data.balances[0].balances
            assertEquals(2, balances.size)
            assertEquals(BigDecimal.parseString("8877.78"), balances[0].amount)
            assertEquals(BalanceType.BOOKED, balances[0].balanceType)
            assertEquals(BigDecimal.parseString("8947.64"), balances[1].amount)
            assertEquals(BalanceType.AVAILABLE, balances[1].balanceType)

            assertEquals(listOf(unknown), payload.data.missingAccounts)
        }

    @Test
    fun transactionsWithoutCredentials() =
        runTest {
            val client = OnlineTestSupport.routexClient()

            val response =
                client.transactions(
                    ticket =
                        OnlineTestSupport.ticketIssuer.transactions(
                            account = AccountReference.iban(demoIban, "EUR"),
                            range = Range.Period(daysAgo(89)),
                        ),
                    connectionData = connectionData(client),
                )

            val payload = assertIs<Response.Result<TransactionsResult>>(response).authenticated.decodeUnverified()
            assertTrue(assertNotNull(payload.data).isNotEmpty())
        }

    /** A window of 90 days or more needs SCA, which cannot be driven without credentials. */
    @Test
    fun periodWithoutScaExemptionFails() =
        runTest {
            val client = OnlineTestSupport.routexClient()
            val data = connectionData(client)

            assertFailsWith<InterruptError> {
                client.transactions(
                    ticket =
                        OnlineTestSupport.ticketIssuer.transactions(
                            account = AccountReference.iban(demoIban, "EUR"),
                            range = Range.Period(daysAgo(91)),
                        ),
                    connectionData = data,
                )
            }
        }

    @Test
    fun unknownAccountFails() =
        runTest {
            val client = OnlineTestSupport.routexClient()
            val data = connectionData(client)

            assertFailsWith<NotFound> {
                client.transactions(
                    ticket =
                        OnlineTestSupport.ticketIssuer.transactions(
                            account = AccountReference.iban("NL31YAXI1234567891", "EUR"),
                            range = Range.Period(daysAgo(89)),
                        ),
                    connectionData = data,
                )
            }
        }
}
