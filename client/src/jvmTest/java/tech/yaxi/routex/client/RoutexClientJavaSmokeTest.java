package tech.yaxi.routex.client;

import kotlin.uuid.Uuid;
import kotlinx.datetime.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tech.yaxi.routex.models.AccountReference;
import tech.yaxi.routex.models.BalancesResult;
import tech.yaxi.routex.models.ConfirmationContext;
import tech.yaxi.routex.models.ConnectionId;
import tech.yaxi.routex.models.Credentials;
import tech.yaxi.routex.models.DialogInput;
import tech.yaxi.routex.models.InputContext;
import tech.yaxi.routex.models.JvmBigDecimals;
import tech.yaxi.routex.models.Response;
import tech.yaxi.routex.models.Ticket;
import tech.yaxi.routex.models.TransactionsResult;
import tech.yaxi.routex.settlement.YaxiSystemVersionKeys;
import tech.yaxi.routex.testing.internal.TestCredentials;
import tech.yaxi.routex.tickets.Range;
import tech.yaxi.routex.tickets.RoutexTicketIssuer;
import tech.yaxi.routex.transport.DefaultHttpTransportKt;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * End-to-end Java smoke test exercising {@link RoutexAsyncClient} and the
 * {@link CompletableFuture}-returning service methods against the live YAXI API.
 * Drives the {@code random} demo user, which walks through SCA confirmation,
 * field input, account selection, and (where the service supports it) the
 * redirect / redirect-handle interrupts before reaching a {@link Response.Result}.
 */
@Tag("online")
class RoutexClientJavaSmokeTest {

    private static final String DEMO_CONNECTION_ID =
            "connection-96386142-60e5-4ca9-abcf-944efce5bc1e";
    private static final String DEMO_IBAN = "DE02120300000000202051";
    private static final int MAX_INTERRUPT_STEPS = 10;

    @Test
    @DisplayName("balances() (Java): random user → drive interrupts → typed BalancesResult")
    void balancesViaCompletableFuture() throws Exception {
        try (RoutexAsyncClient client = newAsyncClient();
             CloseableHttpClient http = newRedirectVisitor()) {
            Credentials credentials = new Credentials(new ConnectionId(DEMO_CONNECTION_ID), "random");

            Ticket.Balances ticket = newIssuer().balances();
            Uuid ticketId = ticket.getId();

            List<AccountReference> accounts =
                    List.of(new AccountReference(DEMO_IBAN, "EUR"));

            ServiceOps<BalancesResult> ops = new ServiceOps<>() {
                @Override public CompletableFuture<Response<BalancesResult>> respond(InputContext context, String response) {
                    return client.respondBalances(ticket, context, response);
                }
                @Override public CompletableFuture<Response<BalancesResult>> confirm(ConfirmationContext context) {
                    return client.confirmBalances(ticket, context);
                }
                @Override public CompletableFuture<String> registerRedirectUri(String handle, String redirectUri) {
                    return client.registerRedirectUri(ticket, handle, redirectUri);
                }
            };

            Response<BalancesResult> resultResponse = driveToResult(
                    client.balances(ticket, credentials, accounts).get(), ops, http.client);

            Response.Result<BalancesResult> result;
            if (resultResponse instanceof Response.Result<BalancesResult> r) {
                result = r;
            } else {
                throw new AssertionError("Expected Response.Result, got " + resultResponse);
            }

            BalancesResult balancesResult = result.getAuthenticated().decodeUnverified();
            if (!ticketId.equals(balancesResult.getTicketId())) {
                throw new AssertionError(
                        "Mismatched ticket id: expected " + ticketId + ", got " + balancesResult.getTicketId());
            }
            if (balancesResult.getData().getBalances().isEmpty()) {
                throw new AssertionError("Expected at least one account balance");
            }
            // Smoke-test the Java interop bridge: a balance amount round-trips through
            // java.math.BigDecimal byte-stably via JvmBigDecimals.toJavaBigDecimal.
            tech.yaxi.routex.models.Balance firstBalance =
                    balancesResult.getData().getBalances().get(0).getBalances().get(0);
            java.math.BigDecimal asJava = JvmBigDecimals.toJavaBigDecimal(firstBalance.getAmount());
            if (asJava.signum() < 0) {
                throw new AssertionError("Expected non-negative balance, got " + asJava);
            }
        }
    }

    @Test
    @DisplayName("transactions() (Java): random user → drive interrupts → typed TransactionsResult")
    void transactionsViaCompletableFuture() throws Exception {
        try (RoutexAsyncClient client = newAsyncClient();
             CloseableHttpClient http = newRedirectVisitor()) {
            Credentials credentials = new Credentials(new ConnectionId(DEMO_CONNECTION_ID), "random");

            Ticket.Transactions ticket = newIssuer().transactions(
                    new AccountReference(DEMO_IBAN, "EUR"),
                    new Range.Period(new LocalDate(2019, 1, 13), null));
            Uuid ticketId = ticket.getId();

            ServiceOps<TransactionsResult> ops = new ServiceOps<>() {
                @Override public CompletableFuture<Response<TransactionsResult>> respond(InputContext context, String response) {
                    return client.respondTransactions(ticket, context, response);
                }
                @Override public CompletableFuture<Response<TransactionsResult>> confirm(ConfirmationContext context) {
                    return client.confirmTransactions(ticket, context);
                }
                @Override public CompletableFuture<String> registerRedirectUri(String handle, String redirectUri) {
                    return client.registerRedirectUri(ticket, handle, redirectUri);
                }
            };

            Response<TransactionsResult> resultResponse = driveToResult(
                    client.transactions(ticket, credentials).get(), ops, http.client);

            Response.Result<TransactionsResult> result;
            if (resultResponse instanceof Response.Result<TransactionsResult> r) {
                result = r;
            } else {
                throw new AssertionError("Expected Response.Result, got " + resultResponse);
            }
            TransactionsResult transactionsResult = result.getAuthenticated().decodeUnverified();
            if (!ticketId.equals(transactionsResult.getTicketId())) {
                throw new AssertionError(
                        "Mismatched ticket id: expected " + ticketId + ", got " + transactionsResult.getTicketId());
            }
            if (transactionsResult.getData() == null || transactionsResult.getData().isEmpty()) {
                throw new AssertionError("Expected non-empty transactions list");
            }
        }
    }

    /**
     * Per-service confirm / respond / registerRedirectUri seam used by {@link #driveToResult}.
     * Lets the same loop walk every service; the test wires the right method references at
     * call site.
     */
    private interface ServiceOps<R> {
        CompletableFuture<Response<R>> respond(InputContext context, String response);
        CompletableFuture<Response<R>> confirm(ConfirmationContext context);
        CompletableFuture<String> registerRedirectUri(String handle, String redirectUri);
    }

    /**
     * Walk every interrupt variant ({@link Response.Dialog}, {@link Response.Redirect},
     * {@link Response.RedirectHandle}) until the server settles on a {@link Response.Result}.
     * Mirrors the Kotlin reference loop in {@code CollectPaymentRandomOnlineTest}.
     */
    private static <R> Response<R> driveToResult(
            Response<R> initial, ServiceOps<R> ops, HttpClient http) throws Exception {
        Response<R> current = initial;
        int steps = 0;
        while (!(current instanceof Response.Result<R>)) {
            if (++steps > MAX_INTERRUPT_STEPS) {
                throw new AssertionError(
                        "too many interrupt steps; last response was " + current.getClass().getSimpleName());
            }
            if (current instanceof Response.Dialog<R> dlg) {
                current = handleDialog(ops, dlg.getDialog().getInput()).get();
            } else if (current instanceof Response.Redirect<R> redirect) {
                visitRedirect(http, redirect.getRedirect().getUrl());
                current = ops.confirm(redirect.getRedirect().getContext()).get();
            } else if (current instanceof Response.RedirectHandle<R> handle) {
                String url = ops.registerRedirectUri(
                        handle.getRedirectHandle().getHandle(), "smoketest://redirect").get();
                visitRedirect(http, url);
                current = ops.confirm(handle.getRedirectHandle().getContext()).get();
            } else {
                throw new AssertionError("unhandled response variant: " + current);
            }
        }
        return current;
    }

    private static <R> CompletableFuture<Response<R>> handleDialog(ServiceOps<R> ops, DialogInput input) {
        if (input instanceof DialogInput.Confirmation c) {
            return ops.confirm(c.getContext());
        } else if (input instanceof DialogInput.Field f) {
            return ops.respond(f.getContext(), "133742");
        } else if (input instanceof DialogInput.Selection s) {
            return ops.respond(s.getContext(), s.getOptions().get(0).getKey());
        }
        throw new AssertionError("unhandled DialogInput: " + input);
    }

    /**
     * If [url] points at the YAXI redirector, post the query back to remux to simulate
     * the bank-side callback; otherwise issue a non-following GET so the bank emits its
     * 303 without us chasing it. Matches {@code CollectPaymentRandomOnlineTest.visitRedirect}.
     */
    private static void visitRedirect(HttpClient http, String url) throws Exception {
        URI parsed = URI.create(url);
        HttpRequest get = HttpRequest.newBuilder(parsed).GET().build();
        http.send(get, HttpResponse.BodyHandlers.discarding());
    }

    /** {@link HttpClient} wrapper that {@link CloseableHttpClient#close()} drives the JDK 21+ shutdown. */
    private record CloseableHttpClient(HttpClient client) implements AutoCloseable {
        @Override public void close() { client.close(); }
    }

    /**
     * Construct a {@link RoutexAsyncClient} pointing at the live API with the verifying
     * keys the active environment expects: in CI the {@code TEST_SIGNING_KEYS} flag
     * pins the staging key id, otherwise the production {@link YaxiSystemVersionKeys#getDEFAULT}
     * map is used. Mirrors {@code OnlineTestSupport.systemVersionSigningKeys} on the
     * Kotlin side.
     */
    private static RoutexAsyncClient newAsyncClient() {
        return new RoutexAsyncClient(
                io.ktor.http.URLUtilsKt.Url(TestCredentials.URL),
                DefaultHttpTransportKt.defaultHttpTransport(),
                signingKeys(),
                ForkJoinPool.commonPool());
    }

    private static Map<String, byte[]> signingKeys() {
        if (TestCredentials.TEST_SIGNING_KEYS) {
            return Map.of(
                    "dYa685dhHap8RSUtB4DDy1l4UcycsGhklBnV5a/4HSg=",
                    Base64.getDecoder().decode("qzLgDnRegbiQzY416i9/MClrmMp24jcHzaWCWSWSutA="));
        }
        return YaxiSystemVersionKeys.INSTANCE.getDEFAULT();
    }

    private static CloseableHttpClient newRedirectVisitor() {
        return new CloseableHttpClient(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    private static RoutexTicketIssuer newIssuer() {
        return new RoutexTicketIssuer(TestCredentials.API_KEY_ID, TestCredentials.API_KEY);
    }
}
