package tech.yaxi.routex.refresh

import io.ktor.http.Url
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import tech.yaxi.routex.models.Account
import tech.yaxi.routex.models.AccountField
import tech.yaxi.routex.models.AccountFilter
import tech.yaxi.routex.models.AccountReference
import tech.yaxi.routex.models.Balances
import tech.yaxi.routex.models.ConnectionData
import tech.yaxi.routex.models.ConnectionDetails
import tech.yaxi.routex.models.ConnectionId
import tech.yaxi.routex.models.ConnectionInfo
import tech.yaxi.routex.models.InternalRoutexApi
import tech.yaxi.routex.models.NonInteractiveResponse
import tech.yaxi.routex.models.SearchFilter
import tech.yaxi.routex.models.Session
import tech.yaxi.routex.models.Ticket
import tech.yaxi.routex.models.Transaction
import tech.yaxi.routex.settlement.SystemVersionEntry
import tech.yaxi.routex.transport.HttpTransport
import tech.yaxi.routex.transport.defaultHttpTransport

/**
 * Java-friendly async facade for [RoutexRefreshClient]: every public method is exposed
 * here as an instance method returning [CompletableFuture]. Kotlin callers should use
 * [RoutexRefreshClient] directly; this class is intended for Java consumers and other
 * JVM languages that integrate with [CompletableFuture].
 *
 * The conceptual model carries over from [RoutexRefreshClient]: each call is
 * authorized by a typed [Ticket] and uses a [ConnectionData] envelope (obtained from a
 * prior interactive flow's
 * [Response.Result.connectionData][tech.yaxi.routex.models.Response.Result.connectionData]).
 * There is no interrupt loop; calls either return a [NonInteractiveResponse] with
 * the typed payload, or throw a [RoutexError][tech.yaxi.routex.models.RoutexError]
 * subclass.
 *
 * ## Usage
 *
 * ```java
 * try (RoutexRefreshAsyncClient refresh = new RoutexRefreshAsyncClient()) {
 *   refresh
 *       .balances(ticket, connectionData, accountReferences)
 *       .whenComplete(
 *           (response, error) -> {
 *             if (error != null) {
 *               // RoutexError subclass, RoutexClientError, or I/O exception
 *               handleFailure(error);
 *               return;
 *             }
 *             Balances balances = response.getResult();
 *             // ...
 *           });
 * }
 * ```
 *
 * Calling [CompletableFuture.get] / [CompletableFuture.join] instead rewraps failures
 * in [ExecutionException][java.util.concurrent.ExecutionException] (`get`) or
 * [CompletionException][java.util.concurrent.CompletionException] (`join`); unwrap with
 * `e.getCause()` to recover the underlying exception.
 *
 * ## Lifecycle
 *
 * The supplied [Executor] (default [ForkJoinPool.commonPool]) backs every emitted
 * future. [close] cancels in-flight futures with
 * [CancellationException][java.util.concurrent.CancellationException] and rejects
 * later calls. Instances are safe to share across threads.
 */
public class RoutexRefreshAsyncClient private constructor(
    private val delegate: RoutexRefreshClient,
    private val scope: CoroutineScope,
) : AutoCloseable {
    /**
     * Construct a client.
     *
     * @param baseUrl Base URL of the YAXI API. Defaults to the production endpoint.
     * @param httpTransport HTTP transport. Defaults to a Ktor engine selected per
     *   platform.
     * @param executor Executor backing every emitted [CompletableFuture]. Defaults
     *   to [ForkJoinPool.commonPool]; supply a dedicated pool to isolate Routex
     *   work from the rest of the JVM.
     */
    @JvmOverloads
    public constructor(
        baseUrl: Url = DEFAULT_BASE_URL,
        httpTransport: HttpTransport = defaultHttpTransport(),
        executor: Executor = ForkJoinPool.commonPool(),
    ) : this(
        delegate = RoutexRefreshClient(baseUrl, httpTransport),
        scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher()),
    )

    /** Convenience overload that parses `baseUrl` as a Ktor [Url]. */
    @JvmOverloads
    public constructor(
        baseUrl: String,
        httpTransport: HttpTransport = defaultHttpTransport(),
        executor: Executor = ForkJoinPool.commonPool(),
    ) : this(Url(baseUrl), httpTransport, executor)

    /**
     * Test-only constructor overriding the system-version verifying keys.
     * @suppress
     */
    @JvmOverloads
    @InternalRoutexApi
    public constructor(
        baseUrl: Url,
        httpTransport: HttpTransport,
        signingKeys: Map<String, ByteArray>,
        executor: Executor = ForkJoinPool.commonPool(),
    ) : this(
        delegate = RoutexRefreshClient(baseUrl, httpTransport, signingKeys),
        scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher()),
    )

    /**
     * Authenticated TEE system version learned during settlement of [ticket]'s
     * key-settlement session.
     *
     * Each [Ticket] id has its own settlement; pass the ticket whose settlement you
     * want to inspect. Returns `null` if no service call has been issued for that
     * ticket yet.
     *
     * The value lets a caller verify YAXI's confidential-computing promises
     * post-hoc by mapping the reported measurement back to an audited build of the
     * routex service.
     */
    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    public fun systemVersion(ticket: Ticket): SystemVersionEntry? = delegate.systemVersion(ticket)

    /** Fetch metadata for a single service connection. */
    public fun info(
        ticket: Ticket,
        connectionId: ConnectionId,
    ): CompletableFuture<ConnectionInfo> = scope.future { delegate.info(ticket, connectionId) }

    /**
     * Search for service connections (banks and other providers).
     *
     * Returns connections that match every entry in [filters].
     *
     * ```java
     * refresh
     *     .search(
     *         ticket,
     *         List.of(new SearchFilter.Term("sparkasse")),
     *         false, // ibanDetection
     *         10, // limit
     *         List.of(ConnectionDetails.BICS))
     *     .thenAccept(
     *         connections -> {
     *           // render connections
     *         });
     * ```
     *
     * @param ibanDetection When `true`, [SearchFilter.Term] values that look like an
     *   IBAN (e.g. `NL58YAXI1234567890`) also yield connections matching the IBAN's
     *   bank code, on top of the regular text matches.
     * @param limit Maximum number of results to return. `null` (default) leaves the
     *   server-side cap in effect.
     * @param details Extra per-connection details to populate (e.g.
     *   [ConnectionDetails.BICS]).
     */
    @JvmOverloads
    public fun search(
        ticket: Ticket,
        filters: List<SearchFilter>,
        ibanDetection: Boolean = false,
        limit: Int? = null,
        details: List<ConnectionDetails> = emptyList(),
    ): CompletableFuture<List<ConnectionInfo>> =
        scope.future { delegate.search(ticket, filters, ibanDetection, limit?.toUInt(), details) }

    /**
     * Refresh accounts (and selected fields) for the connection encoded in
     * [connectionData].
     *
     * ```java
     * refresh
     *     .accounts(
     *         ticket,
     *         connectionData,
     *         List.of(AccountField.Iban.INSTANCE, AccountField.OwnerName.INSTANCE))
     *     .thenAccept(
     *         response -> {
     *           List<Account> accounts = response.getResult();
     *           // ...
     *         });
     * ```
     *
     * @param connectionData Opaque envelope returned by a successful interactive
     *   accounts flow on [RoutexClient][tech.yaxi.routex.client.RoutexClient].
     * @param fields Fields to populate on each returned account.
     * @param filter Optional account filter (server-side AND/OR/NOT predicates over
     *   [AccountField]s).
     * @param session Opaque session token from a previous call in the same logical
     *   session, if any.
     * @see <a href="https://docs.yaxi.tech/accounts.html">Accounts service reference</a>
     */
    @JvmOverloads
    public fun accounts(
        ticket: Ticket.Accounts,
        connectionData: ConnectionData,
        fields: List<AccountField<*>>,
        filter: AccountFilter? = null,
        session: Session? = null,
    ): CompletableFuture<NonInteractiveResponse<List<Account>>> =
        scope.future { delegate.accounts(ticket, connectionData, fields, filter, session) }

    /**
     * Refresh current balances for [accounts] under [connectionData].
     *
     * ```java
     * refresh
     *     .balances(ticket, connectionData, accountReferences)
     *     .thenAccept(
     *         response -> {
     *           Balances balances = response.getResult();
     *           // ...
     *         });
     * ```
     *
     * @see <a href="https://docs.yaxi.tech/balances.html">Balances service reference</a>
     */
    @JvmOverloads
    public fun balances(
        ticket: Ticket.Balances,
        connectionData: ConnectionData,
        accounts: List<AccountReference>,
        session: Session? = null,
    ): CompletableFuture<NonInteractiveResponse<Balances>> =
        scope.future { delegate.balances(ticket, connectionData, accounts, session) }

    /**
     * Refresh booked-and-pending transactions for [connectionData].
     *
     * ```java
     * refresh
     *     .transactions(ticket, connectionData)
     *     .thenAccept(
     *         response -> {
     *           List<Transaction> txs = response.getResult();
     *           // null when the provider returned no transactions for the period.
     *         });
     * ```
     *
     * @see <a href="https://docs.yaxi.tech/transactions.html">Transactions service reference</a>
     */
    @JvmOverloads
    public fun transactions(
        ticket: Ticket.Transactions,
        connectionData: ConnectionData,
        session: Session? = null,
    ): CompletableFuture<NonInteractiveResponse<List<Transaction>?>> =
        scope.future { delegate.transactions(ticket, connectionData, session) }

    /**
     * Cancel in-flight futures and release the underlying coroutine scope.
     *
     * In-flight futures complete exceptionally with
     * [CancellationException][java.util.concurrent.CancellationException]; subsequent
     * calls return already-failed futures. Idempotent. Implements [AutoCloseable] so
     * the client can be used in a Java try-with-resources block.
     */
    override fun close(): Unit = scope.cancel()
}
