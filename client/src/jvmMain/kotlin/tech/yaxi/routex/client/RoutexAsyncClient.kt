package tech.yaxi.routex.client

import io.ktor.http.Url
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import tech.yaxi.routex.models.AccountField
import tech.yaxi.routex.models.AccountFilter
import tech.yaxi.routex.models.AccountReference
import tech.yaxi.routex.models.AccountsResult
import tech.yaxi.routex.models.BalancesResult
import tech.yaxi.routex.models.CollectPaymentResult
import tech.yaxi.routex.models.ConfirmationContext
import tech.yaxi.routex.models.ConnectionData
import tech.yaxi.routex.models.ConnectionDetails
import tech.yaxi.routex.models.ConnectionId
import tech.yaxi.routex.models.ConnectionInfo
import tech.yaxi.routex.models.Credentials
import tech.yaxi.routex.models.DebtorAccountReference
import tech.yaxi.routex.models.InputContext
import tech.yaxi.routex.models.InternalRoutexApi
import tech.yaxi.routex.models.IsoDateTimeOrDate
import tech.yaxi.routex.models.PaymentProduct
import tech.yaxi.routex.models.Response
import tech.yaxi.routex.models.SearchFilter
import tech.yaxi.routex.models.Session
import tech.yaxi.routex.models.StandingOrdersResult
import tech.yaxi.routex.models.Ticket
import tech.yaxi.routex.models.TraceId
import tech.yaxi.routex.models.TransactionsResult
import tech.yaxi.routex.models.TransferDetails
import tech.yaxi.routex.models.TransferResult
import tech.yaxi.routex.settlement.SystemVersionEntry
import tech.yaxi.routex.transport.HttpTransport
import tech.yaxi.routex.transport.defaultHttpTransport

/**
 * Java-friendly async facade for [RoutexClient]: every public method is exposed here
 * as an instance method returning [CompletableFuture]. Kotlin callers should use
 * [RoutexClient] directly; this class is intended for Java consumers and other JVM
 * languages that integrate with [CompletableFuture].
 *
 * The conceptual model carries over from [RoutexClient]: each call is authorized
 * by a typed [Ticket] (for example [Ticket.Accounts]) minted by the caller's
 * backend (see [Getting started](https://docs.yaxi.tech/getting-started.html)),
 * the bank-side identification uses a [Credentials] envelope (see
 * [Providing Credentials](https://docs.yaxi.tech/credentials.html)), and every
 * service call yields a typed [Response] that is either a final
 * [Response.Result] or an interrupt the caller resolves with the matching
 * `respond*` / `confirm*` method before the flow can complete (see
 * [Handling interrupts](https://docs.yaxi.tech/interrupts.html)).
 *
 * ## Handling the result
 *
 *  - Forward
 *    [result.getAuthenticated().getJwt()][tech.yaxi.routex.models.Authenticated.jwt]
 *    to your backend, verify the signature with your YAXI API secret (the same key
 *    your backend uses to mint tickets), and act on the verified payload. See
 *    [Verify results](https://docs.yaxi.tech/verify.html) for backend snippets.
 *    [result.getAuthenticated().decodeUnverified()][tech.yaxi.routex.models.Authenticated.decodeUnverified]
 *    is available for client-only display; its output must never reach a backend or
 *    drive a security-sensitive decision.
 *  - Persist
 *    [result.getConnectionData()][tech.yaxi.routex.models.Response.Result.connectionData].
 *    Pass it back via [Credentials.connectionData] on a future call to skip prompts
 *    the original consent already covers, in place of the [Credentials] to run a call
 *    without credentials, or hand it to your backend to drive non-interactive
 *    refreshes via
 *    [RoutexRefreshAsyncClient][tech.yaxi.routex.refresh.RoutexRefreshAsyncClient].
 *  - Pass [result.getSession()][tech.yaxi.routex.models.Response.Result.session] to
 *    subsequent calls in the same flow.
 *
 * ## Usage
 *
 * ```java
 * try (RoutexAsyncClient client = new RoutexAsyncClient()) {
 *   CompletableFuture<Response<AccountsResult>> future =
 *       client.accounts(ticket, credentials, fields);
 *
 *   future.whenComplete(
 *       (response, error) -> {
 *         if (error != null) {
 *           // error is the original exception, not wrapped in CompletionException.
 *           handleFailure(error);
 *           return;
 *         }
 *         if (response instanceof Response.Result<AccountsResult> result) {
 *           // forward result.getAuthenticated().getJwt() to your backend
 *         }
 *         // also handle Response.Dialog / Response.Redirect / Response.RedirectHandle
 *       });
 * }
 * ```
 *
 * Calling [CompletableFuture.get] / [CompletableFuture.join] instead rewraps failures
 * in [ExecutionException][java.util.concurrent.ExecutionException] (`get`) or
 * [CompletionException][java.util.concurrent.CompletionException] (`join`); unwrap with
 * `e.getCause()` to recover the underlying exception (a
 * [RoutexError][tech.yaxi.routex.models.RoutexError] subclass mapped from the server's
 * typed error response, see [Errors](https://docs.yaxi.tech/errors.html);
 * [RoutexClientError.MalformedResponse][tech.yaxi.routex.settlement.RoutexClientError.MalformedResponse];
 * [AuthenticatedDecodeError][tech.yaxi.routex.models.AuthenticatedDecodeError]; or an
 * I/O exception).
 *
 * ## Lifecycle
 *
 * The supplied [Executor] (default [ForkJoinPool.commonPool]) backs every emitted
 * future. [close] cancels the underlying coroutine scope: in-flight futures complete
 * exceptionally with
 * [CancellationException][java.util.concurrent.CancellationException], and subsequent
 * calls return failed
 * futures. Instances are safe to share across threads.
 */
@Suppress("TooManyFunctions")
public class RoutexAsyncClient private constructor(
    private val delegate: RoutexClient,
    private val scope: CoroutineScope,
) : AutoCloseable {
    /**
     * Construct a client.
     *
     * @param baseUrl Base URL of the YAXI API. Defaults to the production endpoint.
     * @param httpTransport HTTP transport. Defaults to a Ktor engine selected per
     *   platform (CIO on JVM/Android, Darwin on Apple targets).
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
        delegate = RoutexClient(baseUrl, httpTransport),
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
        delegate = RoutexClient(baseUrl, httpTransport, signingKeys),
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

    /**
     * Trace identifier returned with the most recent request, if any.
     *
     * Forward to [trace] with a matching ticket to retrieve the raw server-side trace.
     * Useful when reporting a failed request to YAXI support.
     */
    public val traceId: TraceId? get() = delegate.traceId

    /**
     * Set the redirect URI for subsequent service requests, or pass `null` to clear it.
     *
     * When set, the server returns [Response.Redirect] with the fully-formed landing
     * URL the user can be sent to. When unset, the server returns
     * [Response.RedirectHandle] instead and the caller must call [registerRedirectUri]
     * to obtain the URL.
     *
     * Use a fixed redirect to send the user back to a web app or to deep-link into a
     * desktop / mobile app context.
     */
    public fun setRedirectUri(uri: String?): Unit = delegate.setRedirectUri(uri)

    /**
     * Invoke the accounts service: list accounts (and selected fields) reachable
     * through [credentials].
     *
     * ```java
     * client
     *     .accounts(
     *         ticket,
     *         credentials,
     *         List.of(AccountField.Iban.INSTANCE, AccountField.OwnerName.INSTANCE))
     *     .thenAccept(
     *         response -> {
     *           if (response instanceof Response.Result<AccountsResult> result) {
     *             // forward result.getAuthenticated().getJwt() to your backend
     *           }
     *           // also handle Response.Dialog / Response.Redirect / Response.RedirectHandle
     *         });
     * ```
     *
     * @param ticket Ticket authorizing the call.
     * @param credentials Bank-side identification.
     * @param fields Fields to populate on each returned account.
     * @param filter Optional account filter (e.g. by IBAN, currency, supported
     *   services). Server-side AND/OR/NOT predicates available via the [AccountField]
     *   helpers.
     * @see <a href="https://docs.yaxi.tech/accounts.html">Accounts service reference</a>
     */
    @JvmOverloads
    @Suppress("LongParameterList")
    public fun accounts(
        ticket: Ticket.Accounts,
        credentials: Credentials,
        fields: List<AccountField<*>>,
        filter: AccountFilter? = null,
        session: Session? = null,
        recurringConsents: Boolean = false,
    ): CompletableFuture<Response<AccountsResult>> =
        scope.future { delegate.accounts(ticket, credentials, fields, filter, session, recurringConsents) }

    /**
     * Invoke the accounts service on the consent carried by [connectionData],
     * without credentials.
     *
     * @see RoutexClient.accounts
     * @see <a href="https://docs.yaxi.tech/accounts.html">Accounts service reference</a>
     */
    @JvmOverloads
    public fun accounts(
        ticket: Ticket.Accounts,
        connectionData: ConnectionData,
        fields: List<AccountField<*>>,
        filter: AccountFilter? = null,
        session: Session? = null,
    ): CompletableFuture<Response<AccountsResult>> =
        scope.future { delegate.accounts(ticket, connectionData, fields, filter, session) }

    /**
     * Invoke the balances service: fetch current balances for [accounts].
     *
     * @see <a href="https://docs.yaxi.tech/balances.html">Balances service reference</a>
     */
    @JvmOverloads
    public fun balances(
        ticket: Ticket.Balances,
        credentials: Credentials,
        accounts: List<AccountReference>,
        session: Session? = null,
        recurringConsents: Boolean = false,
    ): CompletableFuture<Response<BalancesResult>> =
        scope.future { delegate.balances(ticket, credentials, accounts, session, recurringConsents) }

    /**
     * Invoke the balances service on the consent carried by [connectionData],
     * without credentials.
     *
     * @see RoutexClient.accounts
     * @see <a href="https://docs.yaxi.tech/balances.html">Balances service reference</a>
     */
    @JvmOverloads
    public fun balances(
        ticket: Ticket.Balances,
        connectionData: ConnectionData,
        accounts: List<AccountReference>,
        session: Session? = null,
    ): CompletableFuture<Response<BalancesResult>> =
        scope.future { delegate.balances(ticket, connectionData, accounts, session) }

    /**
     * Invoke the collect-payment service: identify the debtor and prepare a
     * payment-initiation flow.
     *
     * ```java
     * client
     *     .collectPayment(
     *         ticket,
     *         credentials,
     *         new IbanReference("DE02120300000000202051", null))
     *     .thenAccept(
     *         response -> {
     *           // handle Result / Dialog / Redirect on response
     *         });
     * ```
     *
     * @param account Optional debtor account to collect from. Pass a plain
     *   [IbanReference][tech.yaxi.routex.models.IbanReference], or an
     *   [EncryptedIbanReference][tech.yaxi.routex.models.EncryptedIbanReference]
     *   for a ciphertext your backend produced with its API secret following the
     *   [Opaque Data](https://docs.yaxi.tech/opaque-data.html) protocol so the
     *   frontend never sees the underlying IBAN. Providing [account] reduces the
     *   chance of an account-selection prompt; omitting it may surface one
     *   (bank-dependent).
     * @see <a href="https://docs.yaxi.tech/collect-payment.html">Collect-payment service reference</a>
     */
    @JvmOverloads
    public fun collectPayment(
        ticket: Ticket.CollectPayment,
        credentials: Credentials,
        account: DebtorAccountReference? = null,
        session: Session? = null,
        recurringConsents: Boolean = false,
    ): CompletableFuture<Response<CollectPaymentResult>> =
        scope.future { delegate.collectPayment(ticket, credentials, account, session, recurringConsents) }

    /**
     * Invoke the standing-orders service: fetch the standing orders of the ticket's
     * account authorized by [credentials].
     *
     * @see <a href="https://docs.yaxi.tech/standing-orders.html">Standing-orders service reference</a>
     */
    @JvmOverloads
    public fun standingOrders(
        ticket: Ticket.StandingOrders,
        credentials: Credentials,
        session: Session? = null,
        recurringConsents: Boolean = false,
    ): CompletableFuture<Response<StandingOrdersResult>> =
        scope.future { delegate.standingOrders(ticket, credentials, session, recurringConsents) }

    /**
     * Invoke the transactions service: fetch the booked-and-pending transaction list
     * authorized by [credentials].
     *
     * @see <a href="https://docs.yaxi.tech/transactions.html">Transactions service reference</a>
     */
    @JvmOverloads
    public fun transactions(
        ticket: Ticket.Transactions,
        credentials: Credentials,
        session: Session? = null,
        recurringConsents: Boolean = false,
    ): CompletableFuture<Response<TransactionsResult>> =
        scope.future { delegate.transactions(ticket, credentials, session, recurringConsents) }

    /**
     * Invoke the transactions service on the consent carried by [connectionData],
     * without credentials.
     *
     * @see RoutexClient.transactions
     * @see <a href="https://docs.yaxi.tech/transactions.html">Transactions service reference</a>
     */
    @JvmOverloads
    public fun transactions(
        ticket: Ticket.Transactions,
        connectionData: ConnectionData,
        session: Session? = null,
    ): CompletableFuture<Response<TransactionsResult>> =
        scope.future { delegate.transactions(ticket, connectionData, session) }

    /**
     * Invoke the transfer service: initiate a credit transfer for [product] with the
     * given [details].
     *
     * @param product The payment product to use (e.g. SEPA credit transfer).
     * @param details Transfer line items (creditor, amount, remittance information).
     * @param debtorAccount Optional debtor account; the bank prompts the user when
     *   omitted.
     * @param debtorName Optional debtor name to display in the confirmation flow.
     * @param requestedExecutionDate Optional future execution date; same-day execution
     *   is requested when omitted.
     * @see <a href="https://docs.yaxi.tech/transfer.html">Transfer service reference</a>
     */
    @JvmOverloads
    @Suppress("LongParameterList")
    public fun transfer(
        ticket: Ticket.Transfer,
        credentials: Credentials,
        product: PaymentProduct,
        details: List<TransferDetails>,
        debtorAccount: DebtorAccountReference? = null,
        debtorName: String? = null,
        requestedExecutionDate: IsoDateTimeOrDate? = null,
        session: Session? = null,
        recurringConsents: Boolean = false,
    ): CompletableFuture<Response<TransferResult>> =
        scope.future {
            delegate.transfer(
                ticket,
                credentials,
                product,
                details,
                debtorAccount,
                debtorName,
                requestedExecutionDate,
                session,
                recurringConsents,
            )
        }

    /**
     * Continue an accounts flow by submitting the user's answer to a
     * [Response.Dialog].
     *
     * ```java
     * DialogInput.Field field = (DialogInput.Field) dialog.getInput();
     * client
     *     .respondAccounts(ticket, field.getContext(), userValue)
     *     .thenAccept(
     *         next -> {
     *           // loop on the next Response
     *         });
     * ```
     *
     * @param context [InputContext] taken from the dialog's input on the prior
     *   [Response.Dialog].
     * @param response The user's answer; see
     *   [DialogInput][tech.yaxi.routex.models.DialogInput] for the format expected by
     *   each input variant.
     */
    public fun respondAccounts(
        ticket: Ticket.Accounts,
        context: InputContext,
        response: String,
    ): CompletableFuture<Response<AccountsResult>> =
        scope.future { delegate.respondAccounts(ticket, context, response) }

    /**
     * Continue a balances flow by submitting the user's answer to a
     * [Response.Dialog].
     *
     * @param context [InputContext] taken from the dialog's input on the prior
     *   [Response.Dialog].
     * @param response The user's answer; see
     *   [DialogInput][tech.yaxi.routex.models.DialogInput] for the format expected by
     *   each input variant.
     */
    public fun respondBalances(
        ticket: Ticket.Balances,
        context: InputContext,
        response: String,
    ): CompletableFuture<Response<BalancesResult>> =
        scope.future { delegate.respondBalances(ticket, context, response) }

    /**
     * Continue a transactions flow by submitting the user's answer to a
     * [Response.Dialog].
     *
     * @param context [InputContext] taken from the dialog's input on the prior
     *   [Response.Dialog].
     * @param response The user's answer; see
     *   [DialogInput][tech.yaxi.routex.models.DialogInput] for the format expected by
     *   each input variant.
     */
    public fun respondTransactions(
        ticket: Ticket.Transactions,
        context: InputContext,
        response: String,
    ): CompletableFuture<Response<TransactionsResult>> =
        scope.future { delegate.respondTransactions(ticket, context, response) }

    /**
     * Continue a standing-orders flow by submitting the user's answer to a
     * [Response.Dialog].
     *
     * @param context [InputContext] taken from the dialog's input on the prior
     *   [Response.Dialog].
     * @param response The user's answer; see
     *   [DialogInput][tech.yaxi.routex.models.DialogInput] for the format expected by
     *   each input variant.
     */
    public fun respondStandingOrders(
        ticket: Ticket.StandingOrders,
        context: InputContext,
        response: String,
    ): CompletableFuture<Response<StandingOrdersResult>> =
        scope.future { delegate.respondStandingOrders(ticket, context, response) }

    /**
     * Continue a collect-payment flow by submitting the user's answer to a
     * [Response.Dialog].
     *
     * @param context [InputContext] taken from the dialog's input on the prior
     *   [Response.Dialog].
     * @param response The user's answer; see
     *   [DialogInput][tech.yaxi.routex.models.DialogInput] for the format expected by
     *   each input variant.
     */
    public fun respondCollectPayment(
        ticket: Ticket.CollectPayment,
        context: InputContext,
        response: String,
    ): CompletableFuture<Response<CollectPaymentResult>> =
        scope.future { delegate.respondCollectPayment(ticket, context, response) }

    /**
     * Continue a transfer flow by submitting the user's answer to a
     * [Response.Dialog].
     *
     * @param context [InputContext] taken from the dialog's input on the prior
     *   [Response.Dialog].
     * @param response The user's answer; see
     *   [DialogInput][tech.yaxi.routex.models.DialogInput] for the format expected by
     *   each input variant.
     */
    public fun respondTransfer(
        ticket: Ticket.Transfer,
        context: InputContext,
        response: String,
    ): CompletableFuture<Response<TransferResult>> =
        scope.future { delegate.respondTransfer(ticket, context, response) }

    /**
     * Continue an accounts flow paused on a confirmation [Response.Dialog],
     * [Response.Redirect], or [Response.RedirectHandle].
     *
     * ```java
     * Response.Redirect<AccountsResult> redirectResponse = ...;
     * client
     *     .confirmAccounts(ticket, redirectResponse.getRedirect().getContext())
     *     .thenAccept(
     *         next -> {
     *           // loop on the next Response
     *         });
     * ```
     *
     * @param context [ConfirmationContext] taken from the [Response.Dialog],
     *   [Response.Redirect], or [Response.RedirectHandle] returned by the prior call.
     */
    public fun confirmAccounts(
        ticket: Ticket.Accounts,
        context: ConfirmationContext,
    ): CompletableFuture<Response<AccountsResult>> = scope.future { delegate.confirmAccounts(ticket, context) }

    /**
     * Continue a balances flow paused on a confirmation [Response.Dialog],
     * [Response.Redirect], or [Response.RedirectHandle].
     *
     * @param context [ConfirmationContext] taken from the [Response.Dialog],
     *   [Response.Redirect], or [Response.RedirectHandle] returned by the prior call.
     */
    public fun confirmBalances(
        ticket: Ticket.Balances,
        context: ConfirmationContext,
    ): CompletableFuture<Response<BalancesResult>> = scope.future { delegate.confirmBalances(ticket, context) }

    /**
     * Continue a transactions flow paused on a confirmation [Response.Dialog],
     * [Response.Redirect], or [Response.RedirectHandle].
     *
     * @param context [ConfirmationContext] taken from the [Response.Dialog],
     *   [Response.Redirect], or [Response.RedirectHandle] returned by the prior call.
     */
    public fun confirmTransactions(
        ticket: Ticket.Transactions,
        context: ConfirmationContext,
    ): CompletableFuture<Response<TransactionsResult>> = scope.future { delegate.confirmTransactions(ticket, context) }

    /**
     * Continue a standing-orders flow paused on a confirmation [Response.Dialog],
     * [Response.Redirect], or [Response.RedirectHandle].
     *
     * @param context [ConfirmationContext] taken from the [Response.Dialog],
     *   [Response.Redirect], or [Response.RedirectHandle] returned by the prior call.
     */
    public fun confirmStandingOrders(
        ticket: Ticket.StandingOrders,
        context: ConfirmationContext,
    ): CompletableFuture<Response<StandingOrdersResult>> =
        scope.future { delegate.confirmStandingOrders(ticket, context) }

    /**
     * Continue a collect-payment flow paused on a confirmation [Response.Dialog],
     * [Response.Redirect], or [Response.RedirectHandle].
     *
     * @param context [ConfirmationContext] taken from the [Response.Dialog],
     *   [Response.Redirect], or [Response.RedirectHandle] returned by the prior call.
     */
    public fun confirmCollectPayment(
        ticket: Ticket.CollectPayment,
        context: ConfirmationContext,
    ): CompletableFuture<Response<CollectPaymentResult>> =
        scope.future { delegate.confirmCollectPayment(ticket, context) }

    /**
     * Continue a transfer flow paused on a confirmation [Response.Dialog],
     * [Response.Redirect], or [Response.RedirectHandle].
     *
     * @param context [ConfirmationContext] taken from the [Response.Dialog],
     *   [Response.Redirect], or [Response.RedirectHandle] returned by the prior call.
     */
    public fun confirmTransfer(
        ticket: Ticket.Transfer,
        context: ConfirmationContext,
    ): CompletableFuture<Response<TransferResult>> = scope.future { delegate.confirmTransfer(ticket, context) }

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
     * client
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
     * Retrieve raw trace data for a [traceId] emitted by a prior request.
     *
     * Useful when reporting an incident to YAXI support: `traceId` is typically read
     * from [traceId] right after a failure, then exchanged here for the server-side
     * trace text.
     */
    public fun trace(
        ticket: Ticket,
        traceId: TraceId,
    ): CompletableFuture<String> = scope.future { delegate.trace(ticket, traceId) }

    /**
     * Finalize a [Response.RedirectHandle] by registering [redirectUriValue] as the
     * landing URI for [handle].
     *
     * @return URL the user should be sent to. Open this in a browser, deep-link target,
     *   or WebView. After the user returns, continue the flow with the matching
     *   `confirm*` and the redirect handle's `context`.
     */
    public fun registerRedirectUri(
        ticket: Ticket,
        handle: String,
        redirectUriValue: String,
    ): CompletableFuture<String> = scope.future { delegate.registerRedirectUri(ticket, handle, redirectUriValue) }

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
