package tech.yaxi.routex.refresh

import io.ktor.http.Url
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import tech.yaxi.routex.models.Account
import tech.yaxi.routex.models.AccountField
import tech.yaxi.routex.models.AccountFilter
import tech.yaxi.routex.models.AccountReference
import tech.yaxi.routex.models.Balances
import tech.yaxi.routex.models.ConnectionData
import tech.yaxi.routex.models.ConnectionDetails
import tech.yaxi.routex.models.ConnectionId
import tech.yaxi.routex.models.ConnectionInfo
import tech.yaxi.routex.models.NonInteractiveAccountsRequest
import tech.yaxi.routex.models.NonInteractiveBalancesRequest
import tech.yaxi.routex.models.NonInteractiveResponse
import tech.yaxi.routex.models.NonInteractiveTransactionsRequest
import tech.yaxi.routex.models.RoutexError
import tech.yaxi.routex.models.SearchFilter
import tech.yaxi.routex.models.Session
import tech.yaxi.routex.models.Ticket
import tech.yaxi.routex.models.Transaction
import tech.yaxi.routex.refresh.internal.prepareNonInteractiveAccounts
import tech.yaxi.routex.refresh.internal.prepareNonInteractiveBalances
import tech.yaxi.routex.refresh.internal.prepareNonInteractiveTransactions
import tech.yaxi.routex.refresh.internal.readNonInteractiveResponse
import tech.yaxi.routex.settlement.RoutexClientError
import tech.yaxi.routex.settlement.Settlement
import tech.yaxi.routex.settlement.SystemVersionEntry
import tech.yaxi.routex.settlement.internal.RoutexClientCore
import tech.yaxi.routex.settlement.internal.encodeRequestBody
import tech.yaxi.routex.settlement.internal.encodeSearchRequestBody
import tech.yaxi.routex.settlement.internal.readConnectionInfo
import tech.yaxi.routex.settlement.internal.readConnectionInfoList
import tech.yaxi.routex.transport.HttpError
import tech.yaxi.routex.transport.HttpTransport
import tech.yaxi.routex.transport.defaultHttpTransport

/**
 * Kotlin client for the YAXI Open Banking non-interactive (refresh) services:
 * [accounts], [balances], [transactions], plus the discovery endpoints [info] and
 * [search].
 *
 * Use this client to refresh data for an *already-consented* connection without
 * prompting the user. The driving difference vs.
 * [RoutexClient][tech.yaxi.routex.client.RoutexClient]:
 *
 *  - **Inputs.** A [ConnectionData] envelope replaces the
 *    [Credentials][tech.yaxi.routex.models.Credentials] indirection. [ConnectionData]
 *    is obtained from a successful interactive flow (see
 *    [Response.Result.connectionData][tech.yaxi.routex.models.Response.Result.connectionData])
 *    and persisted by the caller (see
 *    [Handling sessions](https://docs.yaxi.tech/sessions.html)).
 *  - **Outputs.** Each call returns a [NonInteractiveResponse] carrying the decoded
 *    payload directly: no JWT envelope, no
 *    [Response][tech.yaxi.routex.models.Response] interrupt branches.
 *  - **Surface.** Only the read services ([accounts], [balances], [transactions])
 *    plus [info] and [search] are exposed; payment and transfer flows are inherently
 *    interactive and live on [RoutexClient][tech.yaxi.routex.client.RoutexClient].
 *
 * The module is JVM-only because the typical caller is a backend that schedules
 * recurring refreshes; Android consumers should use
 * [RoutexClient][tech.yaxi.routex.client.RoutexClient] (which carries the consent
 * flow).
 *
 * ## Shared parameters
 *
 * Service methods share this optional input (omitted from each method's KDoc; see
 * [Handling sessions](https://docs.yaxi.tech/sessions.html) for the broader
 * picture):
 *
 *  - `session`: opaque [Session] returned by a previous call in the same flow.
 *
 * ## Errors
 *
 * Service methods are `suspend` and throw on failure. Expected exceptions:
 *
 *  - [RoutexError][tech.yaxi.routex.models.RoutexError] subclasses (see
 *    [Errors](https://docs.yaxi.tech/errors.html)).
 *  - [RoutexClientError.MalformedResponse][tech.yaxi.routex.settlement.RoutexClientError.MalformedResponse]
 *    when the server returned a payload this client could not decode (incident-grade;
 *    report it).
 *  - I/O exceptions from the underlying [HttpTransport] (timeouts, connection
 *    failures, and similar).
 *
 * ## Lifecycle
 *
 * Instances are safe to share across coroutines; create one per backing [HttpTransport]
 * and reuse it. Java callers should use [RoutexRefreshAsyncClient].
 *
 * @sample tech.yaxi.routex.refresh.samples.RoutexRefreshClientSamples.refreshBalances
 */
public class RoutexRefreshClient internal constructor(
    internal val core: RoutexClientCore,
) {
    /**
     * Construct a client.
     *
     * @param baseUrl Base URL of the YAXI API. Defaults to the production endpoint.
     * @param httpTransport HTTP transport. Defaults to a Ktor engine selected per
     *   platform.
     */
    @JvmOverloads
    public constructor(
        baseUrl: Url = DEFAULT_BASE_URL,
        httpTransport: HttpTransport = defaultHttpTransport(),
    ) : this(
        core = RoutexClientCore { Settlement(baseUrl = baseUrl, httpTransport = httpTransport) },
    )

    /** Convenience overload that parses `baseUrl` as a Ktor [Url]. */
    @JvmOverloads
    public constructor(
        baseUrl: String,
        httpTransport: HttpTransport = defaultHttpTransport(),
    ) : this(Url(baseUrl), httpTransport)

    internal constructor(
        baseUrl: Url,
        httpTransport: HttpTransport,
        signingKeys: Map<String, ByteArray>,
    ) : this(
        core =
            RoutexClientCore {
                Settlement(baseUrl = baseUrl, httpTransport = httpTransport, signingKeys = signingKeys)
            },
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
    public fun systemVersion(ticket: Ticket): SystemVersionEntry? = core.systemVersionFor(ticket.id.toString())

    /**
     * Fetch metadata for a single service connection.
     *
     * @sample tech.yaxi.routex.refresh.samples.RoutexRefreshClientSamples.connectionInfo
     */
    @Throws(RoutexError::class, RoutexClientError::class, HttpError::class)
    public suspend fun info(
        ticket: Ticket,
        connectionId: ConnectionId,
    ): ConnectionInfo = readConnectionInfo(core.request(ticket, "info/$connectionId", null))

    /**
     * Search for service connections (banks and other providers).
     *
     * Returns connections that match every entry in [filters].
     *
     * @param ibanDetection When `true`, [SearchFilter.Term] values that look like an
     *   IBAN (e.g. `NL58YAXI1234567890`) also yield connections matching the IBAN's
     *   bank code, on top of the regular text matches.
     * @param limit Maximum number of results to return. `null` (default) leaves the
     *   server-side cap in effect.
     * @param details Extra per-connection details to populate (e.g.
     *   [ConnectionDetails.BICS]).
     * @sample tech.yaxi.routex.refresh.samples.RoutexRefreshClientSamples.searchConnections
     */
    @Throws(RoutexError::class, RoutexClientError::class, HttpError::class)
    public suspend fun search(
        ticket: Ticket,
        filters: List<SearchFilter>,
        ibanDetection: Boolean = false,
        limit: UInt? = null,
        details: List<ConnectionDetails> = emptyList(),
    ): List<ConnectionInfo> {
        val body = encodeSearchRequestBody(filters, ibanDetection, limit, details)
        return readConnectionInfoList(core.request(ticket, "search", body))
    }

    /**
     * Refresh accounts (and selected fields) for the connection encoded in
     * [connectionData].
     *
     * @param connectionData Opaque envelope returned by a successful interactive
     *   accounts flow on [RoutexClient][tech.yaxi.routex.client.RoutexClient].
     * @param fields Fields to populate on each returned account.
     * @param filter Optional account filter (server-side AND/OR/NOT predicates over
     *   [AccountField]s).
     * @see <a href="https://docs.yaxi.tech/accounts.html">Accounts service reference</a>
     * @sample tech.yaxi.routex.refresh.samples.RoutexRefreshClientSamples.refreshAccounts
     */
    @Throws(RoutexError::class, RoutexClientError::class, HttpError::class)
    public suspend fun accounts(
        ticket: Ticket.Accounts,
        connectionData: ConnectionData,
        fields: List<AccountField<*>>,
        filter: AccountFilter? = null,
        session: Session? = null,
    ): NonInteractiveResponse<List<Account>> {
        val body = prepareNonInteractiveAccounts(connectionData, session, fields, filter)
        val bytes = encodeRequestBody(NonInteractiveAccountsRequest.serializer(), body)
        return readNonInteractiveResponse(
            core.request(ticket, "accounts/non-interactive", bytes),
            ListSerializer(Account.serializer()),
        )
    }

    /**
     * Refresh current balances for [accounts] under [connectionData].
     *
     * @see <a href="https://docs.yaxi.tech/balances.html">Balances service reference</a>
     * @sample tech.yaxi.routex.refresh.samples.RoutexRefreshClientSamples.refreshBalances
     */
    @Throws(RoutexError::class, RoutexClientError::class, HttpError::class)
    public suspend fun balances(
        ticket: Ticket.Balances,
        connectionData: ConnectionData,
        accounts: List<AccountReference>,
        session: Session? = null,
    ): NonInteractiveResponse<Balances> {
        val body = prepareNonInteractiveBalances(connectionData, session, accounts)
        val bytes = encodeRequestBody(NonInteractiveBalancesRequest.serializer(), body)
        return readNonInteractiveResponse(
            core.request(ticket, "balances/non-interactive", bytes),
            Balances.serializer(),
        )
    }

    /**
     * Refresh booked-and-pending transactions for [connectionData].
     *
     * @see <a href="https://docs.yaxi.tech/transactions.html">Transactions service reference</a>
     * @sample tech.yaxi.routex.refresh.samples.RoutexRefreshClientSamples.refreshTransactions
     */
    @Throws(RoutexError::class, RoutexClientError::class, HttpError::class)
    public suspend fun transactions(
        ticket: Ticket.Transactions,
        connectionData: ConnectionData,
        session: Session? = null,
    ): NonInteractiveResponse<List<Transaction>?> {
        val body = prepareNonInteractiveTransactions(connectionData, session)
        val bytes = encodeRequestBody(NonInteractiveTransactionsRequest.serializer(), body)
        return readNonInteractiveResponse(
            core.request(ticket, "transactions/non-interactive", bytes),
            ListSerializer(Transaction.serializer()).nullable,
        )
    }
}

internal val DEFAULT_BASE_URL: Url = Url("https://api.yaxi.tech")
