package tech.yaxi.routex.client

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import tech.yaxi.routex.models.AccountReference
import tech.yaxi.routex.models.BalanceType
import tech.yaxi.routex.models.BalancesResult
import tech.yaxi.routex.models.Credentials
import tech.yaxi.routex.models.Response

@Tag("online")
@OptIn(ExperimentalUuidApi::class)
class BalancesOnlineTest {
    @Test
    fun balancesFlowReturnsBookedAndAvailableBalances() =
        runTest {
            val client = OnlineTestSupport.routexClient()
            val ticket = OnlineTestSupport.ticketIssuer.balances()

            val response =
                client.balances(
                    ticket = ticket,
                    credentials =
                        Credentials(
                            connectionId = OnlineTestSupport.YAXI_DEMO_CONNECTION_ID,
                            userId = "result",
                        ),
                    accounts =
                        listOf(
                            AccountReference.iban("DE02120300000000202051", "EUR"),
                        ),
                )
            val payload = assertIs<Response.Result<BalancesResult>>(response).authenticated.decodeUnverified()
            assertEquals(ticket.id, payload.ticketId)

            val accountBalances = payload.data.balances
            assertEquals(1, accountBalances.size)
            val entry = accountBalances[0]
            assertEquals(
                AccountReference.iban("DE02120300000000202051", "EUR"),
                entry.account,
            )
            assertEquals(2, entry.balances.size)
            val booked = entry.balances[0]
            assertEquals(BigDecimal.parseString("8877.78"), booked.amount)
            assertEquals("EUR", booked.currency)
            assertEquals(BalanceType.BOOKED, booked.balanceType)
            assertEquals(false, booked.creditLimitIncluded)
            val available = entry.balances[1]
            assertEquals(BigDecimal.parseString("8947.64"), available.amount)
            assertEquals("EUR", available.currency)
            assertEquals(BalanceType.AVAILABLE, available.balanceType)
            assertEquals(false, available.creditLimitIncluded)
            assertNotNull(booked.dateTime)
            assertNotNull(available.dateTime)
        }
}
