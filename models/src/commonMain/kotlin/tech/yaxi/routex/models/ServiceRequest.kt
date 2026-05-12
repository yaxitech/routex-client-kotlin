package tech.yaxi.routex.models

import kotlinx.serialization.Serializable

/** Request body for the interactive accounts service. */
@Serializable
public data class AccountsServiceRequest(
    public val credentials: Credentials,
    public val session: Session? = null,
    public val recurringConsents: Boolean = false,
    public val fields: List<AccountField<*>>,
    public val filter: AccountFilter? = null,
)

/** Request body for the interactive balances service. */
@Serializable
public data class BalancesServiceRequest(
    public val credentials: Credentials,
    public val session: Session? = null,
    public val recurringConsents: Boolean = false,
    public val accounts: List<AccountReference>,
)

/** Request body for the interactive collect-payment service. */
@Serializable
public data class CollectPaymentServiceRequest(
    public val credentials: Credentials,
    public val session: Session? = null,
    public val recurringConsents: Boolean = false,
    public val account: DebtorAccountReference? = null,
)

/** Request body for the interactive transactions service. */
@Serializable
public data class TransactionsServiceRequest(
    public val credentials: Credentials,
    public val session: Session? = null,
    public val recurringConsents: Boolean = false,
)

/** Request body for the interactive transfer service. */
@Serializable
public data class TransferServiceRequest(
    public val credentials: Credentials,
    public val session: Session? = null,
    public val recurringConsents: Boolean = false,
    public val product: PaymentProduct,
    public val debtorAccount: AccountReference? = null,
    public val debtorName: String? = null,
    public val requestedExecutionDate: IsoDateTimeOrDate? = null,
    public val details: List<TransferDetails>,
)

/**
 * Details of a single credit transfer inside a [TransferServiceRequest].
 *
 * @property amount Transfer amount and currency.
 * @property creditorAccount Account to credit.
 * @property creditorName Legal name of the creditor.
 * @property endToEndIdentification Unique end-to-end identifier assigned by the
 *   initiating party.
 * @property creditorAgentBic ISO 20022 BICFIIdentifier of the creditor's agent.
 * @property creditorAddress Creditor postal address (required for some payment products).
 * @property remittance Remittance information / purpose.
 * @property chargeBearer Who bears the charges for the transfer.
 */
@Serializable
public data class TransferDetails
    @JvmOverloads
    constructor(
        public val amount: Amount,
        public val creditorAccount: AccountIdentifier,
        public val creditorName: String,
        public val endToEndIdentification: String? = null,
        public val creditorAgentBic: String? = null,
        public val creditorAddress: CreditorAddress? = null,
        public val remittance: String? = null,
        public val chargeBearer: ChargeBearer? = null,
    )

/**
 * Postal address of a creditor.
 *
 * @property townName Town the creditor resides in.
 * @property country ISO 3166-1 ALPHA-2 country code.
 */
@Serializable
public data class CreditorAddress(
    public val townName: String,
    public val country: CountryCode,
)
