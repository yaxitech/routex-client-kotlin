package tech.yaxi.routex.client.samples;

import java.util.ArrayList;
import java.util.List;
import tech.yaxi.routex.client.RoutexAsyncClient;
import tech.yaxi.routex.models.Account;
import tech.yaxi.routex.models.AccountField;
import tech.yaxi.routex.models.CreditorAccountIdentifier;
import tech.yaxi.routex.models.AccountReference;
import tech.yaxi.routex.models.IbanReference;
import tech.yaxi.routex.models.AccountType;
import tech.yaxi.routex.models.AccountsResult;
import tech.yaxi.routex.models.Amount;
import tech.yaxi.routex.models.CollectPaymentResult;
import tech.yaxi.routex.models.ConfirmationContext;
import tech.yaxi.routex.models.ConnectionData;
import tech.yaxi.routex.models.ConnectionId;
import tech.yaxi.routex.models.ConnectionInfo;
import tech.yaxi.routex.models.Credentials;
import tech.yaxi.routex.models.EncryptedIbanReference;
import tech.yaxi.routex.models.DialogInput;
import tech.yaxi.routex.models.PaymentProduct;
import tech.yaxi.routex.models.Redirect;
import tech.yaxi.routex.models.Response;
import tech.yaxi.routex.models.SearchFilter;
import tech.yaxi.routex.models.Session;
import tech.yaxi.routex.models.SupportedService;
import tech.yaxi.routex.models.Ticket;
import tech.yaxi.routex.models.TransferDetails;

/**
 * Compile-checked Java samples that source the Java code blocks shown in
 * https://docs.yaxi.tech. Each method's body (the lines between the outer {@code {}
 * and {@code }}, dedented one level) is the snippet text shown in the corresponding
 * {@code [,java]} doc tab. Keep them in sync by hand: docs are copied from here, not
 * the other way around.
 *
 * <p>Method names mirror their Kotlin counterparts in {@link RoutexClientSamples}.
 */
@SuppressWarnings("unused")
final class RoutexClientJavaSamples {
    private RoutexClientJavaSamples() {}

    // getting-started.adoc :: Initialize the client
    static void initializeClient() {
        RoutexAsyncClient client = new RoutexAsyncClient();
    }

    // getting-started.adoc :: Initialize the client (integration environment)
    static void initializeClientForIntegration() {
        RoutexAsyncClient client = new RoutexAsyncClient("https://integration.yaxi.tech/");
    }

    // getting-started.adoc :: Search for a bank
    static void searchConnections(RoutexAsyncClient client, Ticket ticket, String userInput) {
        // Split input at whitespace for improved name matching
        List<SearchFilter> filters = new ArrayList<>();
        for (String term : userInput.split("\\s+")) {
            filters.add(new SearchFilter.Term(term));
        }

        List<ConnectionInfo> connectionInfos = client.search(
            ticket,
            filters,
            true, // ibanDetection
            20    // limit
        ).join();
    }

    // getting-started.adoc :: Look up a connection info
    static void connectionInfo(RoutexAsyncClient client, Ticket ticket, ConnectionId connectionId) {
        ConnectionInfo connectionInfo = client.info(ticket, connectionId).join();
    }

    // getting-started.adoc :: Collect credentials
    static void credentialsExample(ConnectionId connectionId, String userId, String password) {
        Credentials credentials = new Credentials(connectionId, userId, password);
    }

    // getting-started.adoc :: Call the service
    static void collectPaymentSimple(
        RoutexAsyncClient client,
        Ticket.CollectPayment ticket,
        Credentials credentials
    ) {
        Response<CollectPaymentResult> response =
            client.collectPayment(ticket, credentials).join();
    }

    // getting-started.adoc :: Confirm dialog without input for the accounts service
    static void confirmDialogExample(
        RoutexAsyncClient client,
        Ticket.Accounts ticket,
        Response<AccountsResult> initial
    ) {
        Response<AccountsResult> response = initial;
        if (response instanceof Response.Dialog<AccountsResult> dialog
            && dialog.getDialog().getInput() instanceof DialogInput.Confirmation confirmation) {
            // Wait for user confirmation

            response = client.confirmAccounts(ticket, confirmation.getContext()).join();
        }
    }

    // getting-started.adoc :: Extract result
    static void extractResultExample(Response<AccountsResult> response) {
        if (response instanceof Response.Result<AccountsResult> result) {
            String jwt = result.getAuthenticated().getJwt();
            Session session = result.getSession();
            ConnectionData connectionData = result.getConnectionData();

            // ...
        }
    }

    // accounts.adoc :: Call the service
    static void accountsCall(
        RoutexAsyncClient client,
        Ticket.Accounts accountsTicket,
        Credentials credentials
    ) {
        client.accounts(
            accountsTicket,
            credentials,
            List.of(
                AccountField.Iban.INSTANCE,
                AccountField.Bic.INSTANCE,
                AccountField.Name.INSTANCE,
                AccountField.DisplayName.INSTANCE,
                AccountField.OwnerName.INSTANCE,
                AccountField.Currency.INSTANCE
            ),
            AccountField.Iban.INSTANCE.notEq(null)
                .and(AccountField.Type.INSTANCE.eq(AccountType.CURRENT)
                    .or(AccountField.Type.INSTANCE.eq(null)))
                .and(Account.supports(SupportedService.COLLECT_PAYMENT))
        );
    }

    // balances.adoc :: Call the service
    static void balancesCall(
        RoutexAsyncClient client,
        Ticket.Balances ticket,
        Credentials credentials
    ) {
        client.balances(
            ticket,
            credentials,
            List.of(AccountReference.iban("NL58YAXI1234567890", "EUR"))
        );
    }

    // transactions.adoc :: Call the service
    static void transactionsCall(
        RoutexAsyncClient client,
        Ticket.Transactions ticket,
        Credentials credentials
    ) {
        client.transactions(ticket, credentials);
    }

    // collect-payment.adoc :: Call the service
    static void collectPaymentCall(
        RoutexAsyncClient client,
        Ticket.CollectPayment ticket,
        Credentials credentials
    ) {
        client.collectPayment(
            ticket,
            credentials,
            new IbanReference("NL58YAXI1234567890", "EUR")
        );
    }

    // collect-payment.adoc :: Encrypted debtor account
    static void collectPaymentEncrypted(
        RoutexAsyncClient client,
        Ticket.CollectPayment ticket,
        Credentials credentials
    ) {
        client.collectPayment(
            ticket,
            credentials,
            new EncryptedIbanReference(
                "sInjgAGbg2k2dXiQ5Y+Mt+zG0AESR7VtxlU03LXoDSa3zcLB9QYRjAv8FBNWETHamyo=",
                "EUR"
            )
        );
    }

    // transfer.adoc :: Call the service
    static void transferCall(
        RoutexAsyncClient client,
        Ticket.Transfer ticket,
        Credentials credentials
    ) {
        client.transfer(
            ticket,
            credentials,
            PaymentProduct.DEFAULT_SEPA_CREDIT_TRANSFER,
            List.of(
                new TransferDetails(
                    new Amount("100.00", "EUR"),
                    new CreditorAccountIdentifier.Iban("NL58YAXI1234567890"),
                    "John Doe"
                )
            ),
            new IbanReference("NL58YAXI1234567890", "EUR"),
            "Debtor"
        );
    }

    // interrupts.adoc :: Check interrupts
    static void dispatchInterrupt(Response<AccountsResult> response) {
        if (response instanceof Response.Dialog<AccountsResult> dialog) {
            // ...
        } else if (response instanceof Response.Redirect<AccountsResult> redirect) {
            // ...
        } else if (response instanceof Response.RedirectHandle<AccountsResult> handle) {
            // ...
        } else if (response instanceof Response.Result<AccountsResult> result) {
            // ...
        }
    }

    // interrupts.adoc :: Confirm dialog without input for the accounts service
    static void confirmAccountsContinuation(
        RoutexAsyncClient client,
        Ticket.Accounts ticket,
        ConfirmationContext context,
        Response<AccountsResult> initial
    ) {
        Response<AccountsResult> response = initial;
        response = client.confirmAccounts(ticket, context).join();
    }

    // interrupts.adoc :: Respond to any other dialog for the accounts service
    static void respondAccountsContinuation(
        RoutexAsyncClient client,
        Ticket.Accounts ticket,
        tech.yaxi.routex.models.InputContext context,
        String userResponse,
        Response<AccountsResult> initial
    ) {
        Response<AccountsResult> response = initial;
        response = client.respondAccounts(ticket, context, userResponse).join();
    }

    // interrupts.adoc :: Set redirect URI
    static void setRedirectUriExample(RoutexAsyncClient client) {
        client.setRedirectUri("myapp://redirect?context=signup");
    }

    // interrupts.adoc :: Register redirect URI
    static void registerRedirectUriExample(
        RoutexAsyncClient client,
        Ticket ticket,
        String handle
    ) {
        String redirectUrl = client.registerRedirectUri(
            ticket,
            handle,
            "myapp://redirect?context=signup"
        ).join();
    }

    // interrupts.adoc :: Confirm redirect for the accounts service
    static void confirmRedirect(
        RoutexAsyncClient client,
        Ticket.Accounts ticket,
        Redirect redirect
    ) {
        Response<AccountsResult> response =
            client.confirmAccounts(ticket, redirect.getContext()).join();
    }
}
