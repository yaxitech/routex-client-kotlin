package tech.yaxi.routex.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import tech.yaxi.routex.models.AccountIdentifier
import tech.yaxi.routex.models.Amount
import tech.yaxi.routex.models.CollectPaymentResult
import tech.yaxi.routex.models.Credentials
import tech.yaxi.routex.models.EncryptedIbanReference
import tech.yaxi.routex.models.Response
import tech.yaxi.routex.tickets.CollectPaymentField

@Tag("online")
@OptIn(ExperimentalUuidApi::class)
class CollectPaymentEncryptedIbanOnlineTest {
    @Test
    fun collectPaymentWithEncryptedIbanReturnsDebtorNameAndIban() =
        runTest {
            val client = OnlineTestSupport.routexClient()
            val ticket =
                OnlineTestSupport.ticketIssuer.collectPayment(
                    amount = Amount("100", "EUR"),
                    creditorAccount = AccountIdentifier.Iban("DE79430609671288143100"),
                    creditorName = "YAXI GmbH",
                    remittance = "Sign-up fee routex 123456789",
                    fields = listOf(CollectPaymentField.DebtorIban, CollectPaymentField.DebtorName),
                )

            val encrypted =
                encryptOnFileData(
                    plaintext = "DE02120300000000202051".encodeToByteArray(),
                    secret = OnlineTestSupport.apiKeySecret(),
                )

            val response =
                client.collectPayment(
                    ticket = ticket,
                    credentials =
                        Credentials(
                            connectionId = OnlineTestSupport.YAXI_DEMO_CONNECTION_ID,
                            userId = "result",
                        ),
                    account =
                        EncryptedIbanReference(
                            bytes = encrypted,
                            currency = "EUR",
                        ),
                )
            val result = assertIs<Response.Result<CollectPaymentResult>>(response)
            val payload = result.authenticated.decodeUnverified().data
            assertEquals("Dr. Peter Steiger", payload.debtorName)
            assertEquals("DE02120300000000202051", payload.debtorIban)
        }
}
