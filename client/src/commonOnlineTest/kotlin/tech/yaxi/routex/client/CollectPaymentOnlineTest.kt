package tech.yaxi.routex.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Tag
import tech.yaxi.routex.models.Account
import tech.yaxi.routex.models.AccountField
import tech.yaxi.routex.models.AccountReference
import tech.yaxi.routex.models.AccountType
import tech.yaxi.routex.models.AccountsResult
import tech.yaxi.routex.models.CollectPaymentResult
import tech.yaxi.routex.models.Credentials
import tech.yaxi.routex.models.DialogInput
import tech.yaxi.routex.models.Response
import tech.yaxi.routex.models.SupportedService

@Tag("online")
@OptIn(ExperimentalUuidApi::class)
class CollectPaymentOnlineTest {
    @Test
    fun collectPaymentWithDebtorIdentificationReturnsDebtorNameAndIban() =
        runTest {
            val client = OnlineTestSupport.routexClient()
            val ticket =
                OnlineTestSupport.collectPaymentTicket(
                    ticketData = OnlineTestSupport.collectPaymentTicketData(),
                )

            val response =
                client.collectPayment(
                    ticket = ticket,
                    credentials =
                        Credentials(
                            connectionId = OnlineTestSupport.YAXI_DEMO_CONNECTION_ID,
                            userId = "result",
                        ),
                    account = AccountReference(iban = "DE02120300000000202051"),
                )
            val result = assertIs<Response.Result<CollectPaymentResult>>(response)
            val payload = result.authenticated.decodeUnverified().data
            assertEquals("Dr. Peter Steiger", payload.debtorName)
            assertEquals("DE02120300000000202051", payload.debtorIban)
        }

    @Test
    @Suppress("LongMethod")
    fun accountsDialogFeedsCollectPaymentAndConnectionDataReplay() =
        runTest {
            val client = OnlineTestSupport.routexClient()
            val accountsTicketId = Uuid.random()
            val accountsTicket = OnlineTestSupport.accountsTicket(accountsTicketId)
            val connectionId = OnlineTestSupport.YAXI_DEMO_CONNECTION_ID

            val typeAny =
                AccountField.Type
                    .eq(AccountType.SAVINGS)
                    .or(AccountField.Type.eq(AccountType.CURRENT))
                    .or(AccountField.Type.eq(null))
            val filter =
                AccountField.Iban
                    .notEq(null)
                    .and(typeAny)
                    .and(Account.supports(SupportedService.COLLECT_PAYMENT))

            val dialogResponse =
                client.accounts(
                    ticket = accountsTicket,
                    credentials = Credentials(connectionId = connectionId, userId = "input"),
                    fields =
                        listOf(
                            AccountField.Iban,
                            AccountField.Bic,
                            AccountField.Name,
                            AccountField.DisplayName,
                            AccountField.OwnerName,
                            AccountField.Currency,
                        ),
                    filter = filter,
                )
            val dialog = assertIs<Response.Dialog<*>>(dialogResponse).dialog
            val fieldInput = assertIs<DialogInput.Field>(dialog.input)

            val respondResponse =
                client.respondAccounts(
                    ticket = accountsTicket,
                    context = fieldInput.context,
                    response = "",
                )
            val respondResult = assertIs<Response.Result<AccountsResult>>(respondResponse)
            val accountsPayload = respondResult.authenticated.decodeUnverified()

            assertEquals(accountsTicketId, accountsPayload.ticketId)
            assertEquals(1, accountsPayload.data.size)
            val first = accountsPayload.data[0]
            assertEquals("BYLADEM1001", first.bic)
            assertEquals("DE02120300000000202051", first.iban)
            assertEquals("EUR", first.currency)
            assertEquals("Dr. Peter Steiger", first.ownerName)

            val selectedIban = assertNotNull(first.iban, "expected IBAN on first account")
            val paymentTicket =
                OnlineTestSupport.collectPaymentTicket(
                    ticketData =
                        buildJsonObject {
                            put(
                                "amount",
                                buildJsonObject {
                                    put("currency", "EUR")
                                    put("amount", "100")
                                },
                            )
                            put(
                                "creditorAccount",
                                buildJsonObject { put("iban", "DE79430609671288143100") },
                            )
                            put("creditorName", "YAXI GmbH")
                            put("remittance", "Sign-up fee routex 123456789")
                        },
                )

            val collectDialog =
                client.collectPayment(
                    ticket = paymentTicket,
                    credentials = Credentials(connectionId = connectionId, userId = "confirmation"),
                    account = AccountReference(iban = selectedIban),
                    session = respondResult.session,
                )
            val confirmation =
                assertIs<DialogInput.Confirmation>(assertIs<Response.Dialog<*>>(collectDialog).dialog.input)

            val collectResponse =
                client.confirmCollectPayment(ticket = paymentTicket, context = confirmation.context)
            val collectResult = assertIs<Response.Result<CollectPaymentResult>>(collectResponse)
            val payment = collectResult.authenticated.decodeUnverified()
            assertNull(payment.data.status)
            assertNull(payment.data.debtorName)
            assertNull(payment.data.debtorIban)
            assertNull(payment.data.encryptedDebtorIban)

            // Connection-data replay: a second accounts call resolves straight to Result.
            val followupResponse =
                client.accounts(
                    ticket = OnlineTestSupport.accountsTicket(),
                    credentials =
                        Credentials(
                            connectionId = connectionId,
                            userId = "confirmation",
                            connectionData = respondResult.connectionData,
                        ),
                    fields = emptyList(),
                )
            assertIs<Response.Result<AccountsResult>>(followupResponse)
        }
}
