package tech.yaxi.routex.client.internal

import tech.yaxi.routex.models.AccountField
import tech.yaxi.routex.models.AccountFilter
import tech.yaxi.routex.models.AccountReference
import tech.yaxi.routex.models.AccountsServiceRequest
import tech.yaxi.routex.models.BalancesServiceRequest
import tech.yaxi.routex.models.CollectPaymentServiceRequest
import tech.yaxi.routex.models.ConnectionData
import tech.yaxi.routex.models.Credentials
import tech.yaxi.routex.models.DebtorAccountReference
import tech.yaxi.routex.models.IsoDateTimeOrDate
import tech.yaxi.routex.models.NonInteractiveAccountsRequest
import tech.yaxi.routex.models.NonInteractiveBalancesRequest
import tech.yaxi.routex.models.NonInteractiveTransactionsRequest
import tech.yaxi.routex.models.PaymentProduct
import tech.yaxi.routex.models.Session
import tech.yaxi.routex.models.StandingOrdersServiceRequest
import tech.yaxi.routex.models.TransactionsServiceRequest
import tech.yaxi.routex.models.TransferDetails
import tech.yaxi.routex.models.TransferServiceRequest

// The typed request records in :models already produce the wire shape that the
// fixtures pin down, so "preparing" is just constructing the strongly-typed
// record. Exposing the helpers as top-level functions lets the oracle test source
// set exercise the encoding path without spinning up the HTTP stack.

internal fun prepareAccounts(
    credentials: Credentials,
    session: Session?,
    recurringConsents: Boolean,
    fields: List<AccountField<*>>,
    filter: AccountFilter?,
): AccountsServiceRequest =
    AccountsServiceRequest(
        credentials = credentials,
        session = session,
        recurringConsents = recurringConsents,
        fields = fields,
        filter = filter,
    )

internal fun prepareBalances(
    credentials: Credentials,
    session: Session?,
    recurringConsents: Boolean,
    accounts: List<AccountReference>,
): BalancesServiceRequest =
    BalancesServiceRequest(
        credentials = credentials,
        session = session,
        recurringConsents = recurringConsents,
        accounts = accounts,
    )

internal fun prepareAccountsWithoutCredentials(
    connectionData: ConnectionData,
    session: Session?,
    fields: List<AccountField<*>>,
    filter: AccountFilter?,
): NonInteractiveAccountsRequest =
    NonInteractiveAccountsRequest(
        connectionData = connectionData,
        session = session,
        fields = fields,
        filter = filter,
    )

internal fun prepareBalancesWithoutCredentials(
    connectionData: ConnectionData,
    session: Session?,
    accounts: List<AccountReference>,
): NonInteractiveBalancesRequest =
    NonInteractiveBalancesRequest(
        connectionData = connectionData,
        session = session,
        accounts = accounts,
    )

internal fun prepareTransactionsWithoutCredentials(
    connectionData: ConnectionData,
    session: Session?,
): NonInteractiveTransactionsRequest =
    NonInteractiveTransactionsRequest(
        connectionData = connectionData,
        session = session,
    )

internal fun prepareCollectPayment(
    credentials: Credentials,
    session: Session?,
    recurringConsents: Boolean,
    account: DebtorAccountReference?,
): CollectPaymentServiceRequest =
    CollectPaymentServiceRequest(
        credentials = credentials,
        session = session,
        recurringConsents = recurringConsents,
        account = account,
    )

internal fun prepareStandingOrders(
    credentials: Credentials,
    session: Session?,
    recurringConsents: Boolean,
): StandingOrdersServiceRequest =
    StandingOrdersServiceRequest(
        credentials = credentials,
        session = session,
        recurringConsents = recurringConsents,
    )

internal fun prepareTransactions(
    credentials: Credentials,
    session: Session?,
    recurringConsents: Boolean,
): TransactionsServiceRequest =
    TransactionsServiceRequest(
        credentials = credentials,
        session = session,
        recurringConsents = recurringConsents,
    )

@Suppress("LongParameterList")
internal fun prepareTransfer(
    credentials: Credentials,
    session: Session?,
    recurringConsents: Boolean,
    product: PaymentProduct,
    debtorAccount: DebtorAccountReference?,
    debtorName: String?,
    requestedExecutionDate: IsoDateTimeOrDate?,
    details: List<TransferDetails>,
): TransferServiceRequest =
    TransferServiceRequest(
        credentials = credentials,
        session = session,
        recurringConsents = recurringConsents,
        product = product,
        debtorAccount = debtorAccount,
        debtorName = debtorName,
        requestedExecutionDate = requestedExecutionDate,
        details = details,
    )
