package tech.yaxi.routex.models

import kotlinx.serialization.Serializable

/** Envelope for non-interactive service responses returned by `RoutexRefreshClient`. */
@Serializable
public data class NonInteractiveResponse<T>(
    public val result: T,
    public val session: Session? = null,
    public val connectionData: ConnectionData? = null,
)

/** Request body for the non-interactive accounts endpoint. */
@Serializable
public data class NonInteractiveAccountsRequest(
    public val connectionData: ConnectionData,
    public val session: Session? = null,
    public val fields: List<AccountField<*>>,
    public val filter: AccountFilter? = null,
)

/** Request body for the non-interactive balances endpoint. */
@Serializable
public data class NonInteractiveBalancesRequest(
    public val connectionData: ConnectionData,
    public val session: Session? = null,
    public val accounts: List<AccountReference>,
)

/** Request body for the non-interactive transactions endpoint. */
@Serializable
public data class NonInteractiveTransactionsRequest(
    public val connectionData: ConnectionData,
    public val session: Session? = null,
)
