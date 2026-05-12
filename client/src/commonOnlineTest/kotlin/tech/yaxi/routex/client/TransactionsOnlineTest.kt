package tech.yaxi.routex.client

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Tag
import tech.yaxi.routex.models.Amount
import tech.yaxi.routex.models.BankTransactionCode
import tech.yaxi.routex.models.Credentials
import tech.yaxi.routex.models.Response
import tech.yaxi.routex.models.TransactionStatus
import tech.yaxi.routex.models.TransactionsResult

@Tag("online")
@OptIn(ExperimentalUuidApi::class)
class TransactionsOnlineTest {
    @Test
    fun transactionsFlowReturnsCanonicalDemoPayload() =
        runTest {
            val client = OnlineTestSupport.routexClient()
            val ticketId = Uuid.random()
            val ticketData =
                buildJsonObject {
                    put(
                        "account",
                        buildJsonObject {
                            put("iban", "DE02120300000000202051")
                            put("currency", "EUR")
                        },
                    )
                    put("range", buildJsonObject { put("from", "2019-01-13") })
                }
            val ticket = OnlineTestSupport.transactionsTicket(ticketId, ticketData)

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
            assertEquals(ticketId, payload.ticketId)

            val txs = assertNotNull(payload.data, "expected non-null transactions list")
            assertEquals(63, txs.size)

            val first = txs[0]
            assertEquals(Amount(amount = BigDecimal.parseString("-0.95"), currency = "EUR"), first.amount)
            assertEquals(LocalDate(2025, 7, 29), first.bookingDate)
            assertEquals(LocalDate(2025, 7, 29), first.valueDate)
            assertEquals(TransactionStatus.BOOKED, first.status)
            assertEquals("DCRD", first.purposeCode)
            assertEquals("485209459755938", first.endToEndId)
            val creditor = assertNotNull(first.creditor)
            assertEquals("DHL.K53VEV55WWVE/BONN", creditor.name)
            assertEquals("DE96120300009005290904", creditor.iban)
            val debtor = assertNotNull(first.debtor)
            assertEquals("ISSUER", debtor.name)
            assertEquals("DE02120300000000202051", debtor.iban)
            assertEquals(3, first.bankTransactionCodes.size)
            val iso = assertIs<BankTransactionCode.Iso>(first.bankTransactionCodes[0])
            assertEquals("PMNT", iso.domain)
            assertEquals("ICDT", iso.family)
            assertEquals("STDO", iso.subFamily)
            assertEquals("DDT", assertIs<BankTransactionCode.Swift>(first.bankTransactionCodes[1]).value)
            val national = assertIs<BankTransactionCode.National>(first.bankTransactionCodes[2])
            assertEquals("106", national.code)
            assertEquals("DE", national.country)
            assertEquals(listOf("VISA Debitkartenumsatz"), first.remittanceInformation)
        }
}
