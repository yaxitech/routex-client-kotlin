package tech.yaxi.refresh.demo;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import tech.yaxi.routex.client.RoutexAsyncClient;
import tech.yaxi.routex.models.AccountField;
import tech.yaxi.routex.models.AccountReference;
import tech.yaxi.routex.models.AccountsResult;
import tech.yaxi.routex.models.Balances;
import tech.yaxi.routex.models.ConnectionData;
import tech.yaxi.routex.models.ConnectionId;
import tech.yaxi.routex.models.Credentials;
import tech.yaxi.routex.models.JvmBigDecimals;
import tech.yaxi.routex.models.NonInteractiveResponse;
import tech.yaxi.routex.models.Response;
import tech.yaxi.routex.refresh.RoutexRefreshAsyncClient;
import tech.yaxi.routex.tickets.RoutexTicketIssuer;

/**
 * Java demo for {@code routex-refresh-client}: opens a one-shot consent against YAXI's
 * Demo Bank to obtain a {@link ConnectionData}, then uses it to refresh balances
 * non-interactively. In production, the consent half runs on the user's device and the
 * refresh half runs on your backend.
 */
public final class JavaDemo {

    /** YAXI Demo Bank, pre-wired to return canned data without contacting a real bank. */
    private static final String DEMO_CONNECTION_ID = "connection-96386142-60e5-4ca9-abcf-944efce5bc1e";

    public static void main(String[] args) {
        var creds = EnvCredentials.fromEnv();
        var issuer = new RoutexTicketIssuer(creds.apiKeyId(), creds.apiKeySecret());

        try (var asyncClient = new RoutexAsyncClient(creds.apiUrl());
             var refreshClient = new RoutexRefreshAsyncClient(creds.apiUrl())) {

            var balances = obtainConnectionData(asyncClient, issuer)
                .thenCompose(refreshContext -> refreshBalances(refreshClient, issuer, refreshContext))
                .join();

            printBalances(balances);
        }
    }

    /** Step 1: interactive accounts call with {@code recurringConsents=true} yields a reusable {@link ConnectionData}. */
    private static CompletableFuture<RefreshContext> obtainConnectionData(RoutexAsyncClient client, RoutexTicketIssuer issuer) {
        var ticket = issuer.accounts();
        var credentials = new Credentials(ConnectionId.parse(DEMO_CONNECTION_ID), "result");
        List<AccountField<?>> fields = List.of(AccountField.Iban.INSTANCE, AccountField.Currency.INSTANCE, AccountField.Name.INSTANCE);

        return client.accounts(ticket, credentials, fields, null, null, true).thenApply(response ->
            switch (response) {
                case Response.Result<AccountsResult> result -> {
                    var connectionData = result.getConnectionData();
                    if (connectionData == null) {
                        throw new IllegalStateException("server did not return connectionData; recurringConsents not granted");
                    }
                    var refs = result.getAuthenticated().decodeUnverified().getData().stream()
                        .filter(a -> a.getIban() != null)
                        .map(a -> new AccountReference(a.getIban(), a.getCurrency()))
                        .toList();
                    yield new RefreshContext(connectionData, refs);
                }
                // A real app handles every variant; the Demo Bank with userId="result" always returns Result.
                default -> throw new UnsupportedOperationException(
                    "expected a Result, got " + response.getClass().getSimpleName());
            }
        );
    }

    /** Step 2: non-interactive balances call using the {@link ConnectionData} obtained above. */
    private static CompletableFuture<Balances> refreshBalances(
        RoutexRefreshAsyncClient client, RoutexTicketIssuer issuer, RefreshContext refreshContext
    ) {
        var ticket = issuer.balances();
        return client.balances(ticket, refreshContext.connectionData(), refreshContext.accountRefs(), null)
            .thenApply(NonInteractiveResponse::getResult);
    }

    private static void printBalances(Balances balances) {
        for (var perAccount : balances.getBalances()) {
            var iban = perAccount.getAccount().getIban();
            for (var balance : perAccount.getBalances()) {
                // The amount is exposed as a multiplatform BigDecimal; convert to
                // java.math.BigDecimal for use with the JDK's monetary tooling.
                BigDecimal amount = JvmBigDecimals.toJavaBigDecimal(balance.getAmount());
                System.out.printf(
                    "%-9s %s %s  (%s)%n",
                    balance.getBalanceType(),
                    amount.toPlainString(),
                    balance.getCurrency(),
                    iban
                );
            }
        }
    }

    /** Pair of {@link ConnectionData} and the account references derived from the interactive accounts response. */
    private record RefreshContext(ConnectionData connectionData, List<AccountReference> accountRefs) {}
}
