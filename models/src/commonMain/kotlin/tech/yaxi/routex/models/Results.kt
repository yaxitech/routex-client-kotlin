@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package tech.yaxi.routex.models

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

/** Authenticated result wrapper carried in the JWT body's `data` claim of every service response. */
public interface RoutexResult<out T> {
    public val data: T
    public val ticketId: Uuid
    public val timestamp: Instant
}

/** Authenticated result of a successful accounts service call. */
@ConsistentCopyVisibility
@Serializable
public data class AccountsResult internal constructor(
    public override val data: List<Account>,
    public override val ticketId: Uuid,
    public override val timestamp: Instant,
) : RoutexResult<List<Account>>

/** Authenticated result of a successful balances service call. */
@ConsistentCopyVisibility
@Serializable
public data class BalancesResult internal constructor(
    public override val data: Balances,
    public override val ticketId: Uuid,
    public override val timestamp: Instant,
) : RoutexResult<Balances>

/** Authenticated result of a successful transactions service call. */
@ConsistentCopyVisibility
@Serializable
public data class TransactionsResult internal constructor(
    public override val data: List<Transaction>?,
    public override val ticketId: Uuid,
    public override val timestamp: Instant,
) : RoutexResult<List<Transaction>?>

/** Authenticated result of a successful collect-payment service call. */
@ConsistentCopyVisibility
@Serializable
public data class CollectPaymentResult internal constructor(
    public override val data: PaymentInitiation,
    public override val ticketId: Uuid,
    public override val timestamp: Instant,
) : RoutexResult<PaymentInitiation>

/** Authenticated result of a successful transfer service call. */
@ConsistentCopyVisibility
@Serializable
public data class TransferResult internal constructor(
    public override val data: Transfer,
    public override val ticketId: Uuid,
    public override val timestamp: Instant,
) : RoutexResult<Transfer>
