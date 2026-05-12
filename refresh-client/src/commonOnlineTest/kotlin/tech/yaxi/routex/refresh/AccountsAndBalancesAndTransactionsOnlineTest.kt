package tech.yaxi.routex.refresh

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Tag
import tech.yaxi.routex.models.Account
import tech.yaxi.routex.models.AccountField
import tech.yaxi.routex.models.AccountReference
import tech.yaxi.routex.models.AccountType
import tech.yaxi.routex.models.AccountsResult
import tech.yaxi.routex.models.BalanceType
import tech.yaxi.routex.models.BalancesResult
import tech.yaxi.routex.models.Credentials
import tech.yaxi.routex.models.Response
import tech.yaxi.routex.models.SupportedService
import tech.yaxi.routex.models.TransactionsResult

/**
 * End-to-end check that a [tech.yaxi.routex.models.ConnectionData] from an interactive
 * [tech.yaxi.routex.client.RoutexClient] accounts/balances/transactions flow can be used through
 * [RoutexRefreshClient] non-interactively, and that the two paths return identical payloads.
 */
@Tag("online")
@OptIn(ExperimentalUuidApi::class)
class AccountsAndBalancesAndTransactionsOnlineTest {
    @Test
    fun nonInteractiveRefreshMatchesInteractiveResults() =
        runTest {
            val credentials =
                Credentials(
                    connectionId = OnlineTestSupport.DEMO_CONNECTION_ID,
                    userId = "result",
                )
            val rangeFrom =
                Clock.System
                    .todayIn(TimeZone.UTC)
                    // Use less than 90 days to not trigger SCA on non-interactive sessions
                    .minus(DatePeriod(days = 89))
                    .toString()
            val transactionsTicketData =
                buildJsonObject {
                    put(
                        "account",
                        buildJsonObject {
                            put("iban", OnlineTestSupport.DEMO_IBAN)
                            put("currency", "EUR")
                        },
                    )
                    put("range", buildJsonObject { put("from", rangeFrom) })
                }

            // Phase 1: interactive RoutexClient drives a full SCA flow per service and
            // captures the result payload alongside a reusable ConnectionData.
            val client = OnlineTestSupport.routexClient()

            // Interactive Accounts
            val accountsTicket = OnlineTestSupport.accountsTicket()
            val accountFields =
                listOf(
                    AccountField.Iban,
                    AccountField.Bic,
                    AccountField.Name,
                    AccountField.DisplayName,
                    AccountField.OwnerName,
                    AccountField.Currency,
                )
            val accountFilter =
                AccountField.Iban
                    .notEq(null)
                    .and(AccountField.Type.eq(AccountType.CURRENT).or(AccountField.Type.eq(null)))
                    .and(Account.supports(SupportedService.COLLECT_PAYMENT))
            val accountsResponse =
                client.accounts(
                    ticket = accountsTicket,
                    credentials = credentials,
                    fields = accountFields,
                    filter = accountFilter,
                )
            val accountsResult = assertIs<Response.Result<AccountsResult>>(accountsResponse)
            val interactiveAccounts = accountsResult.authenticated.decodeUnverified().data
            assertEquals(1, interactiveAccounts.size)
            val account = interactiveAccounts[0]
            assertEquals("BYLADEM1001", account.bic)
            assertEquals("DE02120300000000202051", account.iban)
            assertEquals("EUR", account.currency)
            assertEquals("Dr. Peter Steiger", account.ownerName)

            // Interactive Balances
            val balancesTicket = OnlineTestSupport.balancesTicket()
            val balancesResponse =
                client.balances(
                    ticket = balancesTicket,
                    credentials = credentials,
                    accounts =
                        interactiveAccounts.map {
                            AccountReference(
                                iban = it.iban!!,
                                currency = it.currency,
                            )
                        },
                    recurringConsents = true,
                )
            val balancesResult = assertIs<Response.Result<BalancesResult>>(balancesResponse)
            val interactiveBalances = balancesResult.authenticated.decodeUnverified().data
            assertEquals(emptyList<AccountReference>(), interactiveBalances.missingAccounts)
            assertEquals(1, interactiveBalances.balances.size)
            val accountBalances = interactiveBalances.balances[0]
            assertEquals(
                AccountReference("DE02120300000000202051", "EUR"),
                accountBalances.account,
            )
            assertEquals(2, accountBalances.balances.size)
            val booked = accountBalances.balances[0]
            assertEquals(BigDecimal.parseString("8877.78"), booked.amount)
            assertEquals(BalanceType.BOOKED, booked.balanceType)
            assertEquals("EUR", booked.currency)
            assertEquals(false, booked.creditLimitIncluded)
            val available = accountBalances.balances[1]
            assertEquals(BigDecimal.parseString("8947.64"), available.amount)
            assertEquals(BalanceType.AVAILABLE, available.balanceType)
            assertEquals("EUR", available.currency)
            assertEquals(false, available.creditLimitIncluded)

            // Connection data for non-interactive calls
            val connectionData =
                assertNotNull(
                    balancesResult.connectionData,
                    "expected connectionData on interactive balances result",
                )

            // Interactive Transactions
            val transactionsTicket =
                OnlineTestSupport.transactionsTicket(
                    ticketId = Uuid.random(),
                    ticketData = transactionsTicketData,
                )
            val transactionsResponse =
                client.transactions(
                    ticket = transactionsTicket,
                    credentials = credentials.copy(connectionData = connectionData),
                    session = balancesResult.session,
                )
            val interactiveTransactions =
                assertIs<Response.Result<TransactionsResult>>(transactionsResponse)
                    .authenticated
                    .decodeUnverified()
                    .data
            assertNotNull(interactiveTransactions)
            assertEquals(listOf(), interactiveTransactions)

            // Phase 2: Refresh client with connection data
            val refreshClient = OnlineTestSupport.refreshClient()

            // Non-interactive accounts
            val noninteractiveAccounts =
                refreshClient
                    .accounts(
                        ticket = OnlineTestSupport.accountsTicket(),
                        connectionData = connectionData,
                        fields = accountFields,
                        filter = accountFilter,
                    ).result

            // Make sure the interactive and the non-interactive results match
            assertEquals(noninteractiveAccounts, interactiveAccounts)

            // Non-interactive balances
            val noninteractiveBalances =
                refreshClient
                    .balances(
                        ticket = OnlineTestSupport.balancesTicket(),
                        connectionData = connectionData,
                        accounts =
                            interactiveAccounts.map {
                                AccountReference(
                                    iban = it.iban!!,
                                    currency = it.currency,
                                )
                            },
                    ).result

            // Make sure the interactive and the non-interactive results match
            assertEquals(interactiveBalances, noninteractiveBalances)

            val noninteractiveTransactions =
                refreshClient
                    .transactions(
                        ticket =
                            OnlineTestSupport.transactionsTicket(
                                ticketId = Uuid.random(),
                                ticketData = transactionsTicketData,
                            ),
                        connectionData = connectionData,
                    ).result

            // Make sure the interactive and the non-interactive results match
            assertEquals(interactiveTransactions, noninteractiveTransactions)
        }
}
