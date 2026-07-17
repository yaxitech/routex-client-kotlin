package tech.yaxi.routex.tickets

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tech.yaxi.routex.models.Amount
import tech.yaxi.routex.models.CreditorAccountIdentifier
import tech.yaxi.routex.models.Ticket

/**
 * Debtor-side fields that the collect-payment service can include in its result.
 * Pass these as the `fields` argument to [RoutexTicketIssuer.collectPayment];
 * omitting the parameter (or passing `null`) returns no debtor data.
 */
@Serializable
public enum class CollectPaymentField {
    /** Plain-text IBAN of the debtor account. */
    @SerialName("debtorIban")
    DebtorIban,

    /** Account-owner name on the debtor account. */
    @SerialName("debtorName")
    DebtorName,

    /**
     * Encrypted debtor name, suitable for an opaque handoff back to
     * the caller's backend. See
     * [Opaque Data](https://docs.yaxi.tech/opaque-data.html).
     */
    @SerialName("encryptedDebtorName")
    EncryptedDebtorName,

    /**
     * Encrypted IBAN of the debtor account, suitable for an opaque handoff back to
     * the caller's backend. See
     * [Opaque Data](https://docs.yaxi.tech/opaque-data.html).
     */
    @SerialName("encryptedDebtorIban")
    EncryptedDebtorIban,
}

@Serializable
internal data class CollectPaymentTicketData(
    val amount: Amount,
    val creditorAccount: CreditorAccountIdentifier,
    val creditorName: String,
    val remittance: String,
    val instant: Boolean? = null,
    val fields: List<CollectPaymentField>? = null,
)

internal class CollectPaymentService(
    data: CollectPaymentTicketData,
) : Service<Ticket.CollectPayment>(
        "CollectPayment",
        Json.encodeToJsonElement(CollectPaymentTicketData.serializer(), data),
        Ticket::CollectPayment,
    )
