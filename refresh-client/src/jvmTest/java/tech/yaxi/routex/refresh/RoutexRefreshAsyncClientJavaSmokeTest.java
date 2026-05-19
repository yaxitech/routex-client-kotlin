package tech.yaxi.routex.refresh;

import kotlinx.datetime.ConvertersKt;
import kotlinx.datetime.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tech.yaxi.routex.client.RoutexAsyncClient;
import tech.yaxi.routex.models.Account;
import tech.yaxi.routex.models.AccountField;
import tech.yaxi.routex.models.AccountReference;
import tech.yaxi.routex.models.BalancesResult;
import tech.yaxi.routex.models.ConnectionData;
import tech.yaxi.routex.models.ConnectionId;
import tech.yaxi.routex.models.Credentials;
import tech.yaxi.routex.models.NonInteractiveResponse;
import tech.yaxi.routex.models.Response;
import tech.yaxi.routex.models.Ticket;
import tech.yaxi.routex.settlement.YaxiSystemVersionKeys;
import tech.yaxi.routex.testing.internal.TestCredentials;
import tech.yaxi.routex.tickets.Range;
import tech.yaxi.routex.tickets.RoutexTicketIssuer;
import tech.yaxi.routex.transport.DefaultHttpTransportKt;

import java.util.List;
import java.util.Map;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * End-to-end Java smoke test for {@link RoutexRefreshAsyncClient}: mints a fresh
 * {@link ConnectionData} via the interactive {@link RoutexAsyncClient} (using the
 * {@code result} demo user, which short-circuits past every dialog), then exercises
 * accounts / balances / transactions through the non-interactive async API.
 */
@Tag("online")
class RoutexRefreshAsyncClientJavaSmokeTest {

    private static final String DEMO_CONNECTION_ID =
            "connection-96386142-60e5-4ca9-abcf-944efce5bc1e";
    private static final String DEMO_IBAN = "DE02120300000000202051";

    @Test
    @DisplayName("RoutexRefreshAsyncClient (Java): result user → issue connectionData → drive non-interactive API")
    void refreshAsyncClientFromInteractiveConnectionData() throws Exception {
        RoutexTicketIssuer issuer = new RoutexTicketIssuer(TestCredentials.API_KEY_ID, TestCredentials.API_KEY);
        Credentials credentials = new Credentials(new ConnectionId(DEMO_CONNECTION_ID), "result");
        AccountReference account =
                new AccountReference(DEMO_IBAN, "EUR");

        // Phase 1: interactive balances, userId="result" returns a Response.Result on the
        // first call, no dialog walk required, with a reusable connectionData attached.
        ConnectionData connectionData;
        try (RoutexAsyncClient client = new RoutexAsyncClient(
                io.ktor.http.URLUtilsKt.Url(TestCredentials.URL),
                DefaultHttpTransportKt.defaultHttpTransport(),
                signingKeys(),
                ForkJoinPool.commonPool())) {
            Ticket.Balances balancesTicket = issuer.balances();

            Response<BalancesResult> response = client
                    .balances(balancesTicket, credentials, List.of(account))
                    .get();
            Response.Result<BalancesResult> result;
            if (response instanceof Response.Result<BalancesResult> r) {
                result = r;
            } else {
                throw new AssertionError("Expected Response.Result, got " + response);
            }
            connectionData = result.getConnectionData();
            if (connectionData == null) {
                throw new AssertionError("Expected connectionData on interactive balances result");
            }
        }

        // Phase 2: non-interactive replay through the refresh client's async facade.
        try (RoutexRefreshAsyncClient refresh = new RoutexRefreshAsyncClient(
                io.ktor.http.URLUtilsKt.Url(TestCredentials.URL),
                DefaultHttpTransportKt.defaultHttpTransport(),
                signingKeys(),
                ForkJoinPool.commonPool())) {
            NonInteractiveResponse<List<Account>> accounts = refresh
                    .accounts(
                            issuer.accounts(),
                            connectionData,
                            List.of(AccountField.Iban.INSTANCE, AccountField.Currency.INSTANCE, AccountField.OwnerName.INSTANCE))
                    .get();
            if (accounts.getResult().isEmpty()) {
                throw new AssertionError("Expected at least one account from refresh.accounts()");
            }

            NonInteractiveResponse<tech.yaxi.routex.models.Balances> balances = refresh
                    .balances(
                            issuer.balances(),
                            connectionData,
                            List.of(account))
                    .get();
            if (balances.getResult().getBalances().isEmpty()) {
                throw new AssertionError("Expected at least one account balance from refresh.balances()");
            }

            // Stay inside PSD2's 90-day SCA-exemption window for non-interactive transactions.
            LocalDate rangeFrom = ConvertersKt.toKotlinLocalDate(java.time.LocalDate.now().minusDays(89));
            NonInteractiveResponse<List<tech.yaxi.routex.models.Transaction>> transactions = refresh
                    .transactions(
                            issuer.transactions(
                                    new AccountReference(DEMO_IBAN, "EUR"),
                                    new Range.Period(rangeFrom, null)),
                            connectionData)
                    .get();
            // Transactions list may legitimately be null (no delta); the call succeeding is
            // the actual assertion. Touch the optional payload only if present.
            if (transactions.getResult() != null && transactions.getResult().isEmpty()) {
                // Empty list is acceptable; we just need the call to round-trip.
                return;
            }
        }
    }

    /**
     * Mirror of {@code OnlineTestSupport.systemVersionSigningKeys}: pin the staging key
     * id when {@code TEST_SIGNING_KEYS} is on (CI), otherwise use the production map.
     */
    private static Map<String, byte[]> signingKeys() {
        if (TestCredentials.TEST_SIGNING_KEYS) {
            return Map.of(
                    "dYa685dhHap8RSUtB4DDy1l4UcycsGhklBnV5a/4HSg=",
                    Base64.getDecoder().decode("qzLgDnRegbiQzY416i9/MClrmMp24jcHzaWCWSWSutA="));
        }
        return YaxiSystemVersionKeys.INSTANCE.getDEFAULT();
    }

}
