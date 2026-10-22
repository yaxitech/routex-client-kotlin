@file:Suppress("UnusedVariable", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED_EXPRESSION")

package tech.yaxi.routex.client.samples

import tech.yaxi.routex.client.RoutexClient
import tech.yaxi.routex.models.Account
import tech.yaxi.routex.models.AccountField
import tech.yaxi.routex.models.CreditorAccountIdentifier
import tech.yaxi.routex.models.AccountReference
import tech.yaxi.routex.models.AccountType
import tech.yaxi.routex.models.AccountsResult
import tech.yaxi.routex.models.Amount
import tech.yaxi.routex.models.ConfirmationContext
import tech.yaxi.routex.models.ConnectionId
import tech.yaxi.routex.models.Credentials
import tech.yaxi.routex.models.EncryptedIbanReference
import tech.yaxi.routex.models.IbanReference
import tech.yaxi.routex.models.DialogInput
import tech.yaxi.routex.models.InputContext
import tech.yaxi.routex.models.PaymentProduct
import tech.yaxi.routex.models.Redirect
import tech.yaxi.routex.models.Response
import tech.yaxi.routex.models.SearchFilter
import tech.yaxi.routex.models.SupportedService
import tech.yaxi.routex.models.Ticket
import tech.yaxi.routex.models.TransferDetails

/**
 * Compile-checked Kotlin samples used both as Dokka `@sample` references on the
 * public API and as the source of the Kotlin code blocks shown in
 * https://docs.yaxi.tech.
 *
 * Each function's body (the lines between the outer `{` and `}`, dedented one level)
 * is the snippet text shown in the corresponding `[,kotlin]` doc tab. Keep them in
 * sync by hand: docs are copied from here, not the other way around.
 *
 * KtLint formatting rules are disabled for this file via `.editorconfig`; the body
 * style is dictated by what reads well in the rendered docs, not by the project
 * coding style.
 */
internal object RoutexClientSamples {
    // getting-started.adoc :: Initialize the client
    fun initializeClient() {
        val client = RoutexClient()
    }

    // getting-started.adoc :: Initialize the client (integration environment)
    fun initializeClientForIntegration() {
        val client = RoutexClient("https://integration.yaxi.tech/")
    }

    // getting-started.adoc :: Search for a bank
    suspend fun searchConnections(
        client: RoutexClient,
        ticket: Ticket,
        userInput: String,
    ) {
        // Split input at whitespace for improved name matching
        val filters = userInput.split("\\s".toRegex()).map { SearchFilter.Term(it) }

        val connectionInfos = client.search(
            ticket,
            filters,
            ibanDetection = true,
            limit = 20u,
        )
    }

    // getting-started.adoc :: Look up a connection info
    suspend fun connectionInfo(
        client: RoutexClient,
        ticket: Ticket,
        connectionId: ConnectionId,
    ) {
        val connectionInfo = client.info(ticket, connectionId)
    }

    // getting-started.adoc :: Collect credentials
    fun credentialsExample(
        connectionId: ConnectionId,
        userId: String,
        password: String,
    ) {
        val credentials = Credentials(connectionId, userId, password)
    }

    // getting-started.adoc :: Call the service
    suspend fun collectPaymentSimple(
        client: RoutexClient,
        ticket: Ticket.CollectPayment,
        credentials: Credentials,
    ) {
        var response = client.collectPayment(ticket, credentials)
    }

    // getting-started.adoc :: Confirm dialog without input for the accounts service
    suspend fun confirmDialogExample(
        client: RoutexClient,
        ticket: Ticket.Accounts,
        initial: Response<AccountsResult>,
    ) {
        var response = initial
        if (response is Response.Dialog &&
            response.dialog.input is DialogInput.Confirmation) {
            // Wait for user confirmation

            response = client.confirmAccounts(
                ticket,
                (response.dialog.input as DialogInput.Confirmation).context,
            )
        }
    }

    // getting-started.adoc :: Extract result
    fun extractResultExample(response: Response<AccountsResult>) {
        if (response is Response.Result) {
            val jwt = response.authenticated.jwt
            val session = response.session
            val connectionData = response.connectionData

            // ...
        }
    }

    // accounts.adoc :: Call the service
    suspend fun accountsCall(
        client: RoutexClient,
        accountsTicket: Ticket.Accounts,
        credentials: Credentials,
    ) {
        client.accounts(
            accountsTicket,
            credentials,
            fields = listOf(
                AccountField.Iban,
                AccountField.Bic,
                AccountField.Name,
                AccountField.DisplayName,
                AccountField.OwnerName,
                AccountField.Currency,
            ),
            filter = AccountField.Iban.notEq(null)
                .and(AccountField.Type.eq(AccountType.CURRENT)
                    .or(AccountField.Type.eq(null)))
                .and(Account.supports(SupportedService.COLLECT_PAYMENT)),
        )
    }

    // balances.adoc :: Call the service
    suspend fun balancesCall(
        client: RoutexClient,
        ticket: Ticket.Balances,
        credentials: Credentials,
    ) {
        client.balances(
            ticket,
            credentials,
            accounts = listOf(
                AccountReference.iban("NL58YAXI1234567890", "EUR")
            ),
        )
    }

    // transactions.adoc :: Call the service
    suspend fun transactionsCall(
        client: RoutexClient,
        ticket: Ticket.Transactions,
        credentials: Credentials,
    ) {
        client.transactions(ticket, credentials)
    }

    // standing-orders.adoc :: Call the service
    suspend fun standingOrdersCall(
        client: RoutexClient,
        ticket: Ticket.StandingOrders,
        credentials: Credentials,
    ) {
        client.standingOrders(ticket, credentials)
    }

    // collect-payment.adoc :: Call the service
    suspend fun collectPaymentCall(
        client: RoutexClient,
        ticket: Ticket.CollectPayment,
        credentials: Credentials,
    ) {
        client.collectPayment(
            ticket,
            credentials,
            account = IbanReference("NL58YAXI1234567890", "EUR"),
        )
    }

    // collect-payment.adoc :: Encrypted debtor account
    suspend fun collectPaymentEncrypted(
        client: RoutexClient,
        ticket: Ticket.CollectPayment,
        credentials: Credentials,
    ) {
        client.collectPayment(
            ticket,
            credentials,
            account = EncryptedIbanReference(
                "sInjgAGbg2k2dXiQ5Y+Mt+zG0AESR7VtxlU03LXoDSa3zcLB9QYRjAv8FBNWETHamyo=",
                currency = "EUR",
            ),
        )
    }

    // transfer.adoc :: Call the service
    suspend fun transferCall(
        client: RoutexClient,
        ticket: Ticket.Transfer,
        credentials: Credentials,
    ) {
        client.transfer(
            ticket,
            credentials,
            product = PaymentProduct.DEFAULT_SEPA_CREDIT_TRANSFER,
            details = listOf(
                TransferDetails(
                    amount = Amount("100.00", "EUR"),
                    creditorAccount = CreditorAccountIdentifier.Iban("NL58YAXI1234567890"),
                    creditorName = "John Doe",
                ),
            ),
            debtorAccount = IbanReference("NL58YAXI1234567890", "EUR"),
            debtorName = "Debtor",
        )
    }

    // interrupts.adoc :: Check interrupts
    fun dispatchInterrupt(response: Response<AccountsResult>) {
        when (response) {
            is Response.Dialog -> { /* ... */ }
            is Response.Redirect -> { /* ... */ }
            is Response.RedirectHandle -> { /* ... */ }
            is Response.Result -> { /* ... */ }
        }
    }

    // interrupts.adoc :: Confirm dialog without input for the accounts service
    suspend fun confirmAccountsContinuation(
        client: RoutexClient,
        ticket: Ticket.Accounts,
        context: ConfirmationContext,
        initial: Response<AccountsResult>,
    ) {
        var response = initial
        response = client.confirmAccounts(ticket, context)
    }

    // interrupts.adoc :: Respond to any other dialog for the accounts service
    suspend fun respondAccountsContinuation(
        client: RoutexClient,
        ticket: Ticket.Accounts,
        context: InputContext,
        userResponse: String,
        initial: Response<AccountsResult>,
    ) {
        var response = initial
        response = client.respondAccounts(ticket, context, userResponse)
    }

    // interrupts.adoc :: Set redirect URI
    fun setRedirectUriExample(client: RoutexClient) {
        client.setRedirectUri("myapp://redirect?context=signup")
    }

    // interrupts.adoc :: Register redirect URI
    suspend fun registerRedirectUriExample(
        client: RoutexClient,
        ticket: Ticket,
        handle: String,
    ) {
        val redirectUrl = client.registerRedirectUri(
            ticket,
            handle,
            "myapp://redirect?context=signup",
        )
    }

    // interrupts.adoc :: Confirm redirect for the accounts service
    suspend fun confirmRedirect(
        client: RoutexClient,
        ticket: Ticket.Accounts,
        redirect: Redirect,
    ) {
        val response = client.confirmAccounts(
            ticket,
            context = redirect.context,
        )
    }

    // Not in docs — referenced from RoutexClient.trace() KDoc only.
    suspend fun fetchTraceForLastRequest(
        client: RoutexClient,
        ticket: Ticket,
    ) {
        val traceId = client.traceId ?: return
        val rawTrace = client.trace(ticket = ticket, traceId = traceId)
    }
}
