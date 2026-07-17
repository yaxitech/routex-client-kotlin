package tech.yaxi.routex.client

import io.ktor.http.Url
import kotlin.io.encoding.Base64
import kotlin.jvm.JvmOverloads
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import tech.yaxi.routex.client.internal.prepareAccounts
import tech.yaxi.routex.client.internal.prepareBalances
import tech.yaxi.routex.client.internal.prepareCollectPayment
import tech.yaxi.routex.client.internal.prepareTransactions
import tech.yaxi.routex.client.internal.prepareTransfer
import tech.yaxi.routex.models.AccountField
import tech.yaxi.routex.models.AccountFilter
import tech.yaxi.routex.models.AccountReference
import tech.yaxi.routex.models.AccountsResult
import tech.yaxi.routex.models.AccountsServiceRequest
import tech.yaxi.routex.models.BalancesResult
import tech.yaxi.routex.models.BalancesServiceRequest
import tech.yaxi.routex.models.CollectPaymentResult
import tech.yaxi.routex.models.CollectPaymentServiceRequest
import tech.yaxi.routex.models.ConfirmationContext
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
import tech.yaxi.routex.models.RoutexError
import tech.yaxi.routex.models.RoutexJson
import tech.yaxi.routex.models.SearchFilter
import tech.yaxi.routex.models.Session
import tech.yaxi.routex.models.Ticket
import tech.yaxi.routex.models.TraceId
import tech.yaxi.routex.models.TransactionsResult
import tech.yaxi.routex.models.TransactionsServiceRequest
import tech.yaxi.routex.models.TransferDetails
import tech.yaxi.routex.models.TransferResult
import tech.yaxi.routex.models.TransferServiceRequest
import tech.yaxi.routex.settlement.RoutexClientError
import tech.yaxi.routex.settlement.Settlement
import tech.yaxi.routex.settlement.SystemVersionEntry
import tech.yaxi.routex.settlement.internal.RoutexClientCore
import tech.yaxi.routex.settlement.internal.encodeRequestBody
import tech.yaxi.routex.settlement.internal.encodeSearchRequestBody
import tech.yaxi.routex.settlement.internal.readConnectionInfo
import tech.yaxi.routex.settlement.internal.readConnectionInfoList
import tech.yaxi.routex.settlement.internal.readResponse
import tech.yaxi.routex.transport.HttpError
import tech.yaxi.routex.transport.HttpTransport
import tech.yaxi.routex.transport.defaultHttpTransport

/**
 * Kotlin client for the YAXI Open Banking interactive services: [accounts], [balances],
 * [collectPayment], [transactions], [transfer], plus the discovery endpoints [info] and
 * [search].
 *
 * Every service call returns a typed [Response]. A [Response.Result] holds an
 * [Authenticated][tech.yaxi.routex.models.Authenticated] JWT carrying the typed
 * payload; the other variants ([Response.Dialog], [Response.Redirect],
 * [Response.RedirectHandle]) are interrupts the caller resolves before the flow can
 * complete (see [Handling interrupts](https://docs.yaxi.tech/interrupts.html)). Each
 * call is authorized by a typed [Ticket] (for example [Ticket.Accounts]) minted by
 * the caller's backend (see
 * [Getting started](https://docs.yaxi.tech/getting-started.html)); the bank-side
 * identification uses a [Credentials] envelope (see
 * [Providing Credentials](https://docs.yaxi.tech/credentials.html)).
 *
 * ## Resolving interrupts
 *
 *  - [Response.Dialog]: present [dialog.input][tech.yaxi.routex.models.Dialog.input]
 *    to the user, then call the matching `respond*` (when the user enters or
 *    selects an answer) or `confirm*` (when the user just confirms) with the
 *    `context` carried by the input.
 *  - [Response.Redirect]: send the user to
 *    [redirect.url][tech.yaxi.routex.models.Redirect.url], then call the matching
 *    `confirm*` with [redirect.context][tech.yaxi.routex.models.Redirect.context].
 *  - [Response.RedirectHandle]: call [registerRedirectUri] with
 *    [redirectHandle.handle][tech.yaxi.routex.models.RedirectHandle.handle] to obtain
 *    the URL to open, then confirm with
 *    [redirectHandle.context][tech.yaxi.routex.models.RedirectHandle.context]. Setting
 *    a default redirect via [setRedirectUri] turns later interrupts into
 *    [Response.Redirect] directly.
 *
 * Loop until a [Response.Result] is returned.
 *
 * ## Handling the result
 *
 *  - Forward [result.authenticated.jwt][tech.yaxi.routex.models.Authenticated.jwt] to
 *    your backend, verify the signature with your YAXI API secret (the same key your
 *    backend uses to mint tickets), and act on the verified payload. See
 *    [Verify results](https://docs.yaxi.tech/verify.html) for backend snippets.
 *    [result.authenticated.decodeUnverified()][tech.yaxi.routex.models.Authenticated.decodeUnverified]
 *    is available for client-only display; its output must never reach a backend or
 *    drive a security-sensitive decision.
 *  - Persist
 *    [result.connectionData][tech.yaxi.routex.models.Response.Result.connectionData].
 *    Pass it back via [Credentials.connectionData] on a future [RoutexClient] call to
 *    skip prompts the original consent already covers, or hand it to your backend to
 *    drive non-interactive refreshes via
 *    [RoutexRefreshClient][tech.yaxi.routex.refresh.RoutexRefreshClient].
 *  - Pass [result.session][tech.yaxi.routex.models.Response.Result.session] to
 *    subsequent calls in the same flow.
 *
 * ## Shared parameters
 *
 * Service methods share these optional inputs (omitted from each method's KDoc; see
 * [Handling sessions](https://docs.yaxi.tech/sessions.html) for the broader picture):
 *
 *  - `session`: opaque [Session] returned by a previous call in the same flow.
 *  - `recurringConsents`: when `true`, request a long-lived consent with the maximum
 *    permissions the bank grants for this ticket's service so the returned
 *    [ConnectionData][tech.yaxi.routex.models.ConnectionData] carries it. Subsequent
 *    [RoutexClient] calls that reuse that
 *    [ConnectionData][tech.yaxi.routex.models.ConnectionData] (via
 *    [Credentials.connectionData]) and non-interactive refreshes on
 *    [RoutexRefreshClient][tech.yaxi.routex.refresh.RoutexRefreshClient] skip the
 *    interrupt loop until the consent expires or is revoked. Persist the resulting
 *    [ConnectionData][tech.yaxi.routex.models.ConnectionData] alongside the user's
 *    credentials, since it gates downstream refreshes. Default `false` requests a
 *    one-shot consent scoped to this call.
 *
 * ## Errors
 *
 * Service methods are `suspend` and throw on failure. Expected exceptions:
 *
 *  - [RoutexError][tech.yaxi.routex.models.RoutexError] subclasses (mapped from the
 *    server's typed error response; see
 *    [Errors](https://docs.yaxi.tech/errors.html)).
 *  - [RoutexClientError.MalformedResponse] when the server returned a payload this
 *    client could not decode (incident-grade; report it with the [traceId]).
 *  - [AuthenticatedDecodeError][tech.yaxi.routex.models.AuthenticatedDecodeError]
 *    when the JWT body did not match the expected typed shape (raised lazily by
 *    [result.authenticated.decodeUnverified()][tech.yaxi.routex.models.Authenticated.decodeUnverified]).
 *  - I/O exceptions from the underlying [HttpTransport] (timeouts, connection
 *    failures, and similar).
 *
 * ## Lifecycle
 *
 * Instances are safe to share across coroutines; create one per backing [HttpTransport]
 * and reuse it. Java callers should use [RoutexAsyncClient].
 *
 * @sample tech.yaxi.routex.client.samples.RoutexClientSamples.accountsCall
 */
@Suppress("TooManyFunctions")
public class RoutexClient internal constructor(
    internal val core: RoutexClientCore,
) {
    /**
     * Construct a client.
     *
     * @param baseUrl Base URL of the YAXI API. Defaults to the production endpoint.
     * @param httpTransport HTTP transport. Defaults to a Ktor engine selected per
     *   platform (CIO on JVM/Android, Js on JS/browser, Darwin on Apple targets).
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

    /**
     * Test-only constructor overriding the system-version verifying keys.
     * @suppress
     */
    @InternalRoutexApi
    public constructor(
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
     * Trace identifier returned with the most recent request, if any.
     *
     * Forward to [trace] with a matching ticket to retrieve the raw server-side trace.
     * Useful when reporting a failed request to YAXI support.
     */
    public val traceId: TraceId?
        get() = core.traceId

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
    public fun setRedirectUri(uri: String?): Unit = core.setRedirectUri(uri)

    // --- service methods ---

    /**
     * Invoke the accounts service: list accounts (and selected fields) reachable
     * through [credentials].
     *
     * @param ticket Ticket authorizing the call.
     * @param credentials Bank-side identification.
     * @param fields Fields to populate on each returned account.
     * @param filter Optional account filter (e.g. by IBAN, currency, supported
     *   services). Server-side AND/OR/NOT predicates available via the [AccountField]
     *   helpers.
     * @see <a href="https://docs.yaxi.tech/accounts.html">Accounts service reference</a>
     * @sample tech.yaxi.routex.client.samples.RoutexClientSamples.accountsCall
     */
    @Throws(RoutexError::class, RoutexClientError::class, HttpError::class)
    @Suppress("LongParameterList")
    public suspend fun accounts(
        ticket: Ticket.Accounts,
        credentials: Credentials,
        fields: List<AccountField<*>>,
        filter: AccountFilter? = null,
        session: Session? = null,
        recurringConsents: Boolean = false,
    ): Response<AccountsResult> {
        val body = prepareAccounts(credentials, session, recurringConsents, fields, filter)
        val bytes = encodeRequestBody(AccountsServiceRequest.serializer(), body)
        return decode(core.request(ticket, "accounts/service", bytes), AccountsResult.serializer())
    }

    /**
     * Invoke the balances service: fetch current balances for [accounts].
     *
     * @see <a href="https://docs.yaxi.tech/balances.html">Balances service reference</a>
     */
    @Throws(RoutexError::class, RoutexClientError::class, HttpError::class)
    public suspend fun balances(
        ticket: Ticket.Balances,
        credentials: Credentials,
        accounts: List<AccountReference>,
        session: Session? = null,
        recurringConsents: Boolean = false,
    ): Response<BalancesResult> {
        val body = prepareBalances(credentials, session, recurringConsents, accounts)
        val bytes = encodeRequestBody(BalancesServiceRequest.serializer(), body)
        return decode(core.request(ticket, "balances/service", bytes), BalancesResult.serializer())
    }

    /**
     * Invoke the collect-payment service: identify the debtor and prepare a
     * payment-initiation flow.
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
     * @sample tech.yaxi.routex.client.samples.RoutexClientSamples.collectPaymentCall
     */
    @Throws(RoutexError::class, RoutexClientError::class, HttpError::class)
    public suspend fun collectPayment(
        ticket: Ticket.CollectPayment,
        credentials: Credentials,
        account: DebtorAccountReference? = null,
        session: Session? = null,
        recurringConsents: Boolean = false,
    ): Response<CollectPaymentResult> {
        val body = prepareCollectPayment(credentials, session, recurringConsents, account)
        val bytes = encodeRequestBody(CollectPaymentServiceRequest.serializer(), body)
        return decode(
            core.request(ticket, "collect-payment/service", bytes),
            CollectPaymentResult.serializer(),
        )
    }

    /**
     * Invoke the transactions service: fetch the booked-and-pending transaction list
     * authorized by [credentials].
     *
     * @see <a href="https://docs.yaxi.tech/transactions.html">Transactions service reference</a>
     */
    @Throws(RoutexError::class, RoutexClientError::class, HttpError::class)
    public suspend fun transactions(
        ticket: Ticket.Transactions,
        credentials: Credentials,
        session: Session? = null,
        recurringConsents: Boolean = false,
    ): Response<TransactionsResult> {
        val body = prepareTransactions(credentials, session, recurringConsents)
        val bytes = encodeRequestBody(TransactionsServiceRequest.serializer(), body)
        return decode(
            core.request(ticket, "transactions/service", bytes),
            TransactionsResult.serializer(),
        )
    }

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
    @Throws(RoutexError::class, RoutexClientError::class, HttpError::class)
    @Suppress("LongParameterList")
    public suspend fun transfer(
        ticket: Ticket.Transfer,
        credentials: Credentials,
        product: PaymentProduct,
        details: List<TransferDetails>,
        debtorAccount: DebtorAccountReference? = null,
        debtorName: String? = null,
        requestedExecutionDate: IsoDateTimeOrDate? = null,
        session: Session? = null,
        recurringConsents: Boolean = false,
    ): Response<TransferResult> {
        val body =
            prepareTransfer(
                credentials,
                session,
                recurringConsents,
                product,
                debtorAccount,
                debtorName,
                requestedExecutionDate,
                details,
            )
        val bytes = encodeRequestBody(TransferServiceRequest.serializer(), body)
        return decode(core.request(ticket, "transfer/service", bytes), TransferResult.serializer())
    }

    /**
     * Continue an accounts flow by submitting the user's answer to a
     * [Response.Dialog].
     *
     * @param context [InputContext] taken from the dialog's input on the prior
     *   [Response.Dialog].
     * @param response The user's answer; see
     *   [DialogInput][tech.yaxi.routex.models.DialogInput] for the format expected by
     *   each input variant.
     * @sample tech.yaxi.routex.client.samples.RoutexClientSamples.respondAccountsContinuation
     */
    @Throws(RoutexError::class, RoutexClientError::class, HttpError::class)
    public suspend fun respondAccounts(
        ticket: Ticket.Accounts,
        context: InputContext,
        response: String,
    ): Response<AccountsResult> = respond("accounts", ticket, context, response, AccountsResult.serializer())

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
    @Throws(RoutexError::class, RoutexClientError::class, HttpError::class)
    public suspend fun respondBalances(
        ticket: Ticket.Balances,
        context: InputContext,
        response: String,
    ): Response<BalancesResult> = respond("balances", ticket, context, response, BalancesResult.serializer())

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
    @Throws(RoutexError::class, RoutexClientError::class, HttpError::class)
    public suspend fun respondTransactions(
        ticket: Ticket.Transactions,
        context: InputContext,
        response: String,
    ): Response<TransactionsResult> =
        respond("transactions", ticket, context, response, TransactionsResult.serializer())

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
    @Throws(RoutexError::class, RoutexClientError::class, HttpError::class)
    public suspend fun respondCollectPayment(
        ticket: Ticket.CollectPayment,
        context: InputContext,
        response: String,
    ): Response<CollectPaymentResult> =
        respond("collect-payment", ticket, context, response, CollectPaymentResult.serializer())

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
    @Throws(RoutexError::class, RoutexClientError::class, HttpError::class)
    public suspend fun respondTransfer(
        ticket: Ticket.Transfer,
        context: InputContext,
        response: String,
    ): Response<TransferResult> = respond("transfer", ticket, context, response, TransferResult.serializer())

    /**
     * Continue an accounts flow paused on a confirmation [Response.Dialog],
     * [Response.Redirect], or [Response.RedirectHandle].
     *
     * @param context [ConfirmationContext] taken from the [Response.Dialog],
     *   [Response.Redirect], or [Response.RedirectHandle] returned by the prior call.
     * @sample tech.yaxi.routex.client.samples.RoutexClientSamples.confirmRedirect
     */
    @Throws(RoutexError::class, RoutexClientError::class, HttpError::class)
    public suspend fun confirmAccounts(
        ticket: Ticket.Accounts,
        context: ConfirmationContext,
    ): Response<AccountsResult> = confirm("accounts", ticket, context, AccountsResult.serializer())

    /**
     * Continue a balances flow paused on a confirmation [Response.Dialog],
     * [Response.Redirect], or [Response.RedirectHandle].
     *
     * @param context [ConfirmationContext] taken from the [Response.Dialog],
     *   [Response.Redirect], or [Response.RedirectHandle] returned by the prior call.
     */
    @Throws(RoutexError::class, RoutexClientError::class, HttpError::class)
    public suspend fun confirmBalances(
        ticket: Ticket.Balances,
        context: ConfirmationContext,
    ): Response<BalancesResult> = confirm("balances", ticket, context, BalancesResult.serializer())

    /**
     * Continue a transactions flow paused on a confirmation [Response.Dialog],
     * [Response.Redirect], or [Response.RedirectHandle].
     *
     * @param context [ConfirmationContext] taken from the [Response.Dialog],
     *   [Response.Redirect], or [Response.RedirectHandle] returned by the prior call.
     */
    @Throws(RoutexError::class, RoutexClientError::class, HttpError::class)
    public suspend fun confirmTransactions(
        ticket: Ticket.Transactions,
        context: ConfirmationContext,
    ): Response<TransactionsResult> = confirm("transactions", ticket, context, TransactionsResult.serializer())

    /**
     * Continue a collect-payment flow paused on a confirmation [Response.Dialog],
     * [Response.Redirect], or [Response.RedirectHandle].
     *
     * @param context [ConfirmationContext] taken from the [Response.Dialog],
     *   [Response.Redirect], or [Response.RedirectHandle] returned by the prior call.
     */
    @Throws(RoutexError::class, RoutexClientError::class, HttpError::class)
    public suspend fun confirmCollectPayment(
        ticket: Ticket.CollectPayment,
        context: ConfirmationContext,
    ): Response<CollectPaymentResult> = confirm("collect-payment", ticket, context, CollectPaymentResult.serializer())

    /**
     * Continue a transfer flow paused on a confirmation [Response.Dialog],
     * [Response.Redirect], or [Response.RedirectHandle].
     *
     * @param context [ConfirmationContext] taken from the [Response.Dialog],
     *   [Response.Redirect], or [Response.RedirectHandle] returned by the prior call.
     */
    @Throws(RoutexError::class, RoutexClientError::class, HttpError::class)
    public suspend fun confirmTransfer(
        ticket: Ticket.Transfer,
        context: ConfirmationContext,
    ): Response<TransferResult> = confirm("transfer", ticket, context, TransferResult.serializer())

    /**
     * Fetch metadata for a single service connection.
     *
     * @sample tech.yaxi.routex.client.samples.RoutexClientSamples.connectionInfo
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
     * @sample tech.yaxi.routex.client.samples.RoutexClientSamples.searchConnections
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
     * Retrieve raw trace data for a [traceId] emitted by a prior request.
     *
     * Useful when reporting an incident to YAXI support: `traceId` is typically read
     * from [RoutexClient.traceId] right after a failure, then exchanged here for the
     * server-side trace text.
     *
     * @sample tech.yaxi.routex.client.samples.RoutexClientSamples.fetchTraceForLastRequest
     */
    @Throws(RoutexError::class, RoutexClientError::class, HttpError::class)
    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    public suspend fun trace(
        ticket: Ticket,
        traceId: TraceId,
    ): String {
        // The trace endpoint expects a ChaChaBox-sealed trace id encoded into the URL
        // path, so [sealPayload] settles before sealing.
        val sealed = core.sealPayload(ticket.id.toString(), traceId.bytes)
        val encoded = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(sealed)
        return core.request(ticket, "traces/$encoded", null).decodeToString()
    }

    /**
     * Finalize a [Response.RedirectHandle] by registering [redirectUriValue] as the
     * landing URI for [handle].
     *
     * @return URL the user should be sent to. Open this in a browser, deep-link target,
     *   or WebView. After the user returns, continue the flow with the matching
     *   `confirm*` and the redirect handle's `context`.
     */
    @Throws(RoutexError::class, RoutexClientError::class, HttpError::class)
    public suspend fun registerRedirectUri(
        ticket: Ticket,
        handle: String,
        redirectUriValue: String,
    ): String {
        val body =
            buildJsonObject {
                put("handle", handle)
                put("redirectUri", redirectUriValue)
            }
        val payload =
            RoutexJson.encodeToString(JsonObject.serializer(), body).encodeToByteArray()
        val bytes = core.request(ticket, "redirects", payload)
        val obj = RoutexJson.parseToJsonElement(bytes.decodeToString())
        return (obj as? JsonObject)
            ?.get("redirectUrl")
            ?.let { (it as? JsonPrimitive)?.content }
            ?: throw RoutexClientError.MalformedResponse("redirect response missing redirectUrl")
    }

    // --- private helpers ---

    private suspend fun <T> respond(
        servicePath: String,
        ticket: Ticket,
        context: InputContext,
        response: String,
        dataSerializer: KSerializer<T>,
    ): Response<T> {
        val body =
            buildJsonObject {
                put("context", RoutexJson.encodeToJsonElement(context))
                put("response", response)
            }
        val payload =
            RoutexJson.encodeToString(JsonObject.serializer(), body).encodeToByteArray()
        return decode(core.request(ticket, "$servicePath/response", payload), dataSerializer)
    }

    private suspend fun <T> confirm(
        servicePath: String,
        ticket: Ticket,
        context: ConfirmationContext,
        dataSerializer: KSerializer<T>,
    ): Response<T> {
        val body =
            buildJsonObject {
                put("context", RoutexJson.encodeToJsonElement(context))
            }
        val payload =
            RoutexJson.encodeToString(JsonObject.serializer(), body).encodeToByteArray()
        return decode(core.request(ticket, "$servicePath/confirmation", payload), dataSerializer)
    }

    private fun <T> decode(
        bytes: ByteArray,
        dataSerializer: KSerializer<T>,
    ): Response<T> = readResponse(bytes, dataSerializer)
}

internal val DEFAULT_BASE_URL: Url = Url("https://api.yaxi.tech")
