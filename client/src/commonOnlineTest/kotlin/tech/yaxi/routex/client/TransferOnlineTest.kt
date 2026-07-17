package tech.yaxi.routex.client

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import tech.yaxi.routex.models.Amount
import tech.yaxi.routex.models.Credentials
import tech.yaxi.routex.models.CreditorAccountIdentifier
import tech.yaxi.routex.models.PaymentProduct
import tech.yaxi.routex.models.PaymentStatus
import tech.yaxi.routex.models.Response
import tech.yaxi.routex.models.TransferDetails
import tech.yaxi.routex.models.TransferResult

@Tag("online")
@OptIn(ExperimentalUuidApi::class)
class TransferOnlineTest {
    @Test
    fun transferFlowReturnsEmptyResultPayload() =
        runTest {
            val client = OnlineTestSupport.routexClient()
            val ticket = OnlineTestSupport.ticketIssuer.transfer()

            val response =
                client.transfer(
                    ticket = ticket,
                    credentials =
                        Credentials(
                            connectionId = OnlineTestSupport.YAXI_DEMO_CONNECTION_ID,
                            userId = "result",
                        ),
                    product = PaymentProduct.DEFAULT_SEPA_CREDIT_TRANSFER,
                    details =
                        listOf(
                            TransferDetails(
                                amount = Amount(currency = "EUR", amount = BigDecimal.fromInt(1)),
                                creditorAccount = CreditorAccountIdentifier.Iban("NL58YAXI1234567890"),
                                creditorName = "creditor",
                            ),
                        ),
                )
            val payload = assertIs<Response.Result<TransferResult>>(response).authenticated.decodeUnverified()
            assertEquals(ticket.id, payload.ticketId)
            assertEquals(PaymentStatus.ACCEPTED, payload.data.status)
        }
}
