package tech.yaxi.routex.models

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * Single account balance reported by the balances service.
 *
 * @property amount Exact decimal balance amount; serialized as a JSON string
 *   (matches [Amount.amount]).
 * @property currency ISO 4217 Alpha 3 currency code.
 * @property balanceType Kind of balance (booked / available / expected).
 * @property creditLimitIncluded Whether [amount][tech.yaxi.routex.models.Balance.amount]
 *   includes any associated credit line, when known.
 * @property dateTime
 */
@ConsistentCopyVisibility
@Serializable
public data class Balance internal constructor(
    @Serializable(with = DecimalSerializer::class)
    public val amount: BigDecimal,
    public val currency: String,
    public val balanceType: BalanceType,
    public val creditLimitIncluded: Boolean? = null,
    public val dateTime: Instant? = null,
)

/** Per-account container holding zero or more typed balances. */
@ConsistentCopyVisibility
@Serializable
public data class AccountBalances internal constructor(
    public val account: AccountReference,
    public val balances: List<Balance>,
)

/** Top-level payload returned by the balances service. */
@ConsistentCopyVisibility
@Serializable
public data class Balances internal constructor(
    /** Per-account balances for accounts that the provider returned data for. */
    public val balances: List<AccountBalances>,
    /** Accounts that the request asked about but the provider did not surface. */
    public val missingAccounts: List<AccountReference> = emptyList(),
)
