package tech.yaxi.routex.tickets

import kotlin.io.encoding.Base64
import kotlin.jvm.JvmOverloads
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import tech.yaxi.routex.models.AccountReference
import tech.yaxi.routex.models.Amount
import tech.yaxi.routex.models.CreditorAccountIdentifier
import tech.yaxi.routex.models.InternalRoutexApi
import tech.yaxi.routex.models.Ticket
import tech.yaxi.routex.tickets.internal.buildClaimsJson
import tech.yaxi.routex.tickets.internal.signCompactJwt

/**
 * Issues signed JWT tickets for the YAXI routex services.
 *
 * One method per service, each returning the matching typed [Ticket] subclass.
 *
 * Instances are immutable and safe to share across threads / coroutines.
 *
 * ```kotlin
 * val issuer = RoutexTicketIssuer(apiKeyId = "api-key-2eeba71f-...", base64Secret = System.getenv("YAXI_API_KEY_SECRET"))
 * val ticket: Ticket.Accounts = issuer.accounts()
 * ```
 */
@OptIn(ExperimentalUuidApi::class)
public class RoutexTicketIssuer internal constructor(
    private val state: State,
) {
    internal data class State(
        val apiKeyId: String,
        val apiKeySecret: ByteArray,
        val ttl: Duration,
        val clock: Clock,
    )

    init {
        require(state.apiKeyId.isNotBlank()) { "apiKeyId must not be blank" }
        require(state.apiKeySecret.isNotEmpty()) { "apiKeySecret must not be empty" }
        require(state.ttl.isPositive()) { "ttl must be positive (got ${state.ttl})" }
    }

    /**
     * @param apiKeyId Identifier of the API key, set as the JWT `kid` header so the
     *   routex backend can pick the correct verifying key.
     * @param apiKeySecret HMAC-SHA256 key bytes; the issuer never logs or otherwise
     *   exposes it.
     * @param ttl Default time-to-live applied to every issued ticket unless the
     *   call site supplies its own override.
     */
    @JvmOverloads
    public constructor(
        apiKeyId: String,
        apiKeySecret: ByteArray,
        ttl: Duration = DEFAULT_TTL,
    ) : this(State(apiKeyId, apiKeySecret, ttl, Clock.System))

    /**
     * Convenience constructor that decodes [base64Secret] as standard Base64.
     *
     * @param apiKeyId Identifier of the API key, set as the JWT `kid` header so the
     *   routex backend can pick the correct verifying key.
     * @param base64Secret HMAC-SHA256 key bytes encoded as standard Base64; the
     *   issuer never logs or otherwise exposes it.
     * @param ttl Default time-to-live applied to every issued ticket unless the
     *   call site supplies its own override.
     * @throws IllegalArgumentException if [base64Secret] is not valid Base64.
     */
    @JvmOverloads
    public constructor(
        apiKeyId: String,
        base64Secret: String,
        ttl: Duration = DEFAULT_TTL,
    ) : this(State(apiKeyId, Base64.Default.decode(base64Secret), ttl, Clock.System))

    /**
     * Test-only constructor that injects a custom [clock].
     * @suppress
     */
    @InternalRoutexApi
    public constructor(
        apiKeyId: String,
        apiKeySecret: ByteArray,
        ttl: Duration,
        clock: Clock,
    ) : this(State(apiKeyId, apiKeySecret, ttl, clock))

    /** Issue a ticket for the accounts service. */
    @JvmOverloads
    public fun accounts(
        ticketId: Uuid = Uuid.random(),
        ttl: Duration? = null,
    ): Ticket.Accounts = issue(AccountsService, ttl, ticketId)

    /** Issue a ticket for the balances service. */
    @JvmOverloads
    public fun balances(
        ticketId: Uuid = Uuid.random(),
        ttl: Duration? = null,
    ): Ticket.Balances = issue(BalancesService, ttl, ticketId)

    /** Issue a ticket for the transfer service. */
    @JvmOverloads
    public fun transfer(
        ticketId: Uuid = Uuid.random(),
        ttl: Duration? = null,
    ): Ticket.Transfer = issue(TransferService, ttl, ticketId)

    /** Issue a ticket for the transactions service against [account]. */
    @JvmOverloads
    @Suppress("LongParameterList")
    public fun transactions(
        account: AccountReference,
        range: Range,
        webhook: String? = null,
        ticketId: Uuid = Uuid.random(),
        ttl: Duration? = null,
    ): Ticket.Transactions {
        require(webhook == null || webhook.isNotBlank()) { "webhook must not be blank" }
        return issue(TransactionsService(TransactionsTicketData(account, range, webhook)), ttl, ticketId)
    }

    /** Issue a ticket for the standing-orders service against [account]. */
    @JvmOverloads
    public fun standingOrders(
        account: AccountReference,
        ticketId: Uuid = Uuid.random(),
        ttl: Duration? = null,
    ): Ticket.StandingOrders = issue(StandingOrdersService(StandingOrdersTicketData(account)), ttl, ticketId)

    /**
     * Issue a ticket for the collect-payment service.
     *
     * @param instant Force instant (`true`) or non-instant (`false`). Leave `null`
     *   (the default) to request an instant payment when supported and fall back
     *   to non-instant otherwise; forcing `true` causes the service to return an
     *   [UnsupportedProduct][tech.yaxi.routex.models.UnsupportedProduct] error when
     *   the bank cannot offer an instant payment.
     * @param fields Debtor-side fields to populate on the result, e.g. the
     *   debtor IBAN or name. Omitting the parameter (or passing `null`) returns
     *   no debtor data.
     */
    @JvmOverloads
    @Suppress("LongParameterList")
    public fun collectPayment(
        amount: Amount,
        creditorAccount: CreditorAccountIdentifier,
        creditorName: String,
        remittance: String,
        instant: Boolean? = null,
        fields: List<CollectPaymentField>? = null,
        ticketId: Uuid = Uuid.random(),
        ttl: Duration? = null,
    ): Ticket.CollectPayment {
        require(creditorName.isNotBlank()) { "creditorName must not be blank" }
        require(remittance.isNotBlank()) { "remittance must not be blank" }
        val data = CollectPaymentTicketData(amount, creditorAccount, creditorName, remittance, instant, fields)
        return issue(CollectPaymentService(data), ttl, ticketId)
    }

    private fun <T : Ticket> issue(
        service: Service<T>,
        perCallTtl: Duration?,
        ticketId: Uuid,
    ): T {
        require(perCallTtl == null || perCallTtl.isPositive()) { "ttl override must be positive (got $perCallTtl)" }
        val effectiveTtl = perCallTtl ?: state.ttl
        val expiresAtSeconds = (state.clock.now() + effectiveTtl).epochSeconds
        val claimsJson = buildClaimsJson(service.id, ticketId, service.data, expiresAtSeconds)
        return service.ticket(signCompactJwt(state.apiKeyId, state.apiKeySecret, claimsJson))
    }

    private companion object {
        val DEFAULT_TTL: Duration = 10.minutes
    }
}
